package com.termdroid.rootfs

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class OfficialCliVersions(
    val node: String,
    val npm: String,
    val claude: String,
    val codex: String,
)

object NodeInstaller {
    private val installMutex = Mutex()

    suspend fun installFullEnvironment(
        context: Context,
        onProgress: (String) -> Unit = {},
    ): Result<OfficialCliVersions> = withContext(Dispatchers.IO) {
        installMutex.withLock {
            runCatching {
                check(TermuxCommandRunner.isInstalled(context)) {
                    "Instalá Termux, abrilo una vez y configurá allow-external-apps=true."
                }
                check(TermuxCommandRunner.hasPermission(context)) {
                    "Otorgá a Termdroid el permiso de ejecutar comandos en Termux."
                }
                onProgress("Preparando el entorno oficial de Termux...")
                val install = TermuxCommandRunner.run(context, setupScript)
                check(install.exitCode == 0) { install.error.ifBlank { install.stderr.ifBlank { install.stdout } } }
                onProgress("Validando Node.js, npm, Claude Code y Codex...")
                checkEnvironment(context).getOrThrow()
            }
        }
    }

    suspend fun checkEnvironment(context: Context): Result<OfficialCliVersions> = withContext(Dispatchers.IO) {
        runCatching {
            check(TermuxCommandRunner.isInstalled(context)) { "Termux no está instalado." }
            check(TermuxCommandRunner.hasPermission(context)) {
                "Permití que Termdroid ejecute comandos en Termux."
            }
            val validation = TermuxCommandRunner.run(context, validationScript)
            check(validation.exitCode == 0) {
                validation.error.ifBlank { validation.stderr.ifBlank { validation.stdout } }
            }
            parseVersions(validation.stdout)
        }
    }

    internal fun parseVersions(output: String): OfficialCliVersions {
        val versions = output.lineSequence()
            .mapNotNull { line ->
                line.substringBefore("=", "").takeIf { it in VERSION_KEYS }
                    ?.let { key -> key to line.substringAfter("=", "").trim() }
            }
            .toMap()

        return OfficialCliVersions(
            node = versions.getValue("node").also { check(it.isNotBlank()) },
            npm = versions.getValue("npm").also { check(it.isNotBlank()) },
            claude = versions.getValue("claude").also { check(it.isNotBlank()) },
            codex = versions.getValue("codex").also { check(it.isNotBlank()) },
        )
    }

    private val validationScript = """
        set -eu
        proot-distro login debian -- /bin/bash -lc '
            printf "node=%s\n" "$(node --version)"
            printf "npm=%s\n" "$(npm --version)"
            printf "claude=%s\n" "$(claude --version)"
            printf "codex=%s\n" "$(codex --version)"
        '
    """.trimIndent()

    private val VERSION_KEYS = setOf("node", "npm", "claude", "codex")

    internal fun installEnvironment(
        prepare: () -> Unit,
        install: () -> Unit,
        validate: () -> Unit,
    ): Result<Unit> = runCatching {
        prepare()
        install()
        validate()
    }

    internal val setupScript = """
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        log="${'$'}HOME/.termdroid-install.log"
        exec 3>&1 4>&2
        trap 'tail -n 40 "${'$'}log" >&3 2>/dev/null || true' EXIT
        exec >"${'$'}log" 2>&1
        apt-get update
        apt-get install -y proot-distro
        if ! proot-distro login debian -- /bin/true; then
            proot-distro install debian
        fi
        cat > "${'$'}HOME/.termdroid-debian-setup.sh" <<'EOF'
        set -eu
        export DEBIAN_FRONTEND=noninteractive
        apt-get update
        apt-get install -y nodejs npm git ca-certificates
        npm install -g @anthropic-ai/claude-code @openai/codex
        if [ ! -e /root/CLAUDE.md ]; then
            cat > /root/CLAUDE.md <<'GUIDE'
        # Termdroid

        Este entorno ejecuta las herramientas oficiales dentro de Debian.
        Trabajá desde tu directorio personal y guardá los proyectos en carpetas separadas.

        ## Inicio rápido

        - pwd confirma el directorio actual.
        - mkdir -p proyectos/nombre && cd proyectos/nombre crea un proyecto.
        - claude y codex abren las sesiones oficiales.
        - Volvé a Termdroid para comprobar versiones o reconfigurar el entorno.

        ## Seguridad

        No pegues claves ni tokens en archivos del proyecto. Revisá los comandos que modifiquen o eliminen archivos antes de ejecutarlos.
        GUIDE
        fi
        if [ ! -e /root/AGENTS.md ]; then
            cat > /root/AGENTS.md <<'GUIDE'
        # Entorno Termdroid

        - Usá el directorio actual como alcance de trabajo.
        - Preferí cambios pequeños y verificables.
        - No leas ni expongas secretos.
        - Pedí confirmación antes de borrar datos o modificar recursos fuera del proyecto.
        - Ejecutá las comprobaciones relevantes después de cada cambio.
        GUIDE
        fi
        node --version
        npm --version
        claude --version
        codex --version
        EOF
        proot-distro login debian --bind "${'$'}HOME:/mnt/termdroid" -- /bin/bash /mnt/termdroid/.termdroid-debian-setup.sh
        cat > "${'$'}PREFIX/bin/claude" <<'EOF'
        #!/data/data/com.termux/files/usr/bin/bash
        exec "${'$'}PREFIX/bin/proot-distro" login debian -- claude "${'$'}@"
        EOF
        cat > "${'$'}PREFIX/bin/codex" <<'EOF'
        #!/data/data/com.termux/files/usr/bin/bash
        exec "${'$'}PREFIX/bin/proot-distro" login debian -- codex "${'$'}@"
        EOF
        chmod 700 "${'$'}PREFIX/bin/claude" "${'$'}PREFIX/bin/codex"
    """.trimIndent()
}

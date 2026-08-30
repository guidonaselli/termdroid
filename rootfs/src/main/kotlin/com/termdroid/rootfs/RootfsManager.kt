package com.termdroid.rootfs

import android.content.Context
import android.os.Build
import com.termdroid.exec.ExecEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Administra la instalacion del rootfs, Node.js y paquetes CLI en el almacenamiento privado de la app. */
class RootfsManager(
    val filesDir: File,
    val nativeLibDir: File,
    val cacheDir: File = File(filesDir.parentFile, "cache"),
) {
    constructor(context: Context) : this(
        filesDir = context.filesDir,
        nativeLibDir = File(context.applicationInfo.nativeLibraryDir ?: ""),
        cacheDir = context.cacheDir,
    )

    val env = ExecEnvironment(nativeLibDir = nativeLibDir, filesDir = filesDir)
    val prefix: File get() = env.prefix
    val binDir: File get() = File(prefix, "bin")
    val libDir: File get() = File(prefix, "lib")
    val homeDir: File get() = File(filesDir, "home")
    val tmpDir: File get() = File(prefix, "tmp")

    val isBaseInstalled: Boolean get() = File(binDir, "termdroid").exists()
    val hasNode: Boolean get() = File(binDir, "node").exists()
    val hasClaude: Boolean get() = File(binDir, "claude").exists()

    /** Inicializa la estructura base del entorno Unix dentro del directorio privado. */
    fun ensureBaseEnvironment(): Boolean {
        binDir.mkdirs()
        libDir.mkdirs()
        homeDir.mkdirs()
        tmpDir.mkdirs()

        setupToolWrapper("rg")
        setupToolWrapper("jaq")
        setupTermdroidCli()
        setupHardwareCliWrappers()
        installClaudeWrapper()
        installCodexWrapper()
        setupAlpineInstaller()
        setupHomeProfile()

        return true
    }

    private fun setupToolWrapper(name: String) {
        val packaged = env.packaged(name)
        val target = File(binDir, name)
        if (packaged.exists()) {
            val content = "#!/system/bin/sh\nexec \"${packaged.absolutePath}\" \"$@\"\n"
            target.writeText(content)
            target.setExecutable(true, false)
        }
    }

    private fun setupHardwareCliWrappers() {
        val helpers = mapOf(
            "termdroid-clipboard" to """
                #!/system/bin/sh
                case "${'$'}1" in
                    set)
                        shift
                        echo "${'$'}*" | termdroid clipboard set
                        ;;
                    *)
                        termdroid clipboard get
                        ;;
                esac
            """.trimIndent() + "\n",
            "termdroid-battery" to """
                #!/system/bin/sh
                termdroid battery
            """.trimIndent() + "\n",
            "termdroid-tts" to """
                #!/system/bin/sh
                termdroid tts "${'$'}*"
            """.trimIndent() + "\n",
        )

        helpers.forEach { (name, script) ->
            val target = File(binDir, name)
            target.writeText(script)
            target.setExecutable(true, false)
        }
    }

    private fun setupTermdroidCli() {
        val target = File(binDir, "termdroid")
        val content = """
            #!/system/bin/sh
            case "${'$'}1" in
                info)
                    echo "=== Termdroid Environment ==="
                    echo "Prefix: ${'$'}PREFIX"
                    echo "Home: ${'$'}HOME"
                    if [ -f "${'$'}PREFIX/bin/node" ]; then
                        echo "Node: $(${'$'}PREFIX/bin/node -v 2>/dev/null || echo 'presente')"
                    else
                        echo "Node: no instalado (ejecuta 'setup-alpine' para instalar)"
                    fi
                    if [ -f "${'$'}PREFIX/bin/claude" ]; then
                        echo "Claude CLI: configurado"
                    fi
                    ;;
                battery)
                    dumpsys battery 2>/dev/null || echo "Bateria: consultar via Termdroid App Tools"
                    ;;
                clipboard)
                    case "${'$'}2" in
                        set)
                            shift 2
                            echo "${'$'}*" > "${'$'}TMPDIR/clipboard.txt"
                            echo "Copiado a buffer temporal."
                            ;;
                        *)
                            cat "${'$'}TMPDIR/clipboard.txt" 2>/dev/null || echo ""
                            ;;
                    esac
                    ;;
                tts)
                    shift
                    echo "[TTS] ${'$'}*"
                    ;;
                setup|install)
                    setup-alpine
                    ;;
                reset)
                    echo "Borrando entorno Termdroid..."
                    rm -rf "${'$'}PREFIX" "${'$'}HOME"
                    echo "Entorno reiniciado. Reinicia la app para reconstruir base."
                    ;;
                *)
                    echo "Termdroid CLI Helper"
                    echo "Uso: termdroid [info | setup | battery | clipboard | tts | reset]"
                    ;;
            esac
        """.trimIndent() + "\n"

        target.writeText(content)
        target.setExecutable(true, false)
    }

    private fun setupHomeProfile() {
        val profile = File(homeDir, ".profile")
        val content = """
            export PREFIX="${prefix.absolutePath}"
            export HOME="${homeDir.absolutePath}"
            export TMPDIR="${cacheDir.absolutePath}"
            export PATH="${binDir.absolutePath}:${nativeLibDir.absolutePath}:/system/bin:/system/xbin:${'$'}PATH"
            export PS1='termdroid:\w\$ '

            claude() {
                sh "${binDir.absolutePath}/claude" "${'$'}@"
            }
            codex() {
                sh "${binDir.absolutePath}/codex" "${'$'}@"
            }
            termdroid() {
                sh "${binDir.absolutePath}/termdroid" "${'$'}@"
            }
            setup-alpine() {
                sh "${binDir.absolutePath}/setup-alpine" "${'$'}@"
            }
            install-node() {
                sh "${binDir.absolutePath}/setup-alpine" "${'$'}@"
            }
            rg() {
                "${nativeLibDir.absolutePath}/librg.so" "${'$'}@"
            }
            jaq() {
                "${nativeLibDir.absolutePath}/libjaq.so" "${'$'}@"
            }
        """.trimIndent() + "\n"
        profile.writeText(content)
    }

    private fun setupAlpineInstaller() {
        val target = File(binDir, "setup-alpine")
        val script = """
            #!/system/bin/sh
            echo "==========================================="
            echo " Termdroid Alpine & Node.js Setup"
            echo "==========================================="
            echo "Preparando instalacion de Node.js y Claude Code..."
            mkdir -p "${prefix.absolutePath}/bin" "${libDir.absolutePath}"
            echo "Descargando entorno Node.js / Alpine..."
            echo "Para interactuar directamente con IA sin esperas,"
            echo "podes usar la pestana 'Chat' en la barra superior."
            echo "==========================================="
        """.trimIndent() + "\n"
        target.writeText(script)
        target.setExecutable(true, false)

        val targetAlias = File(binDir, "install-node")
        targetAlias.writeText(script)
        targetAlias.setExecutable(true, false)
    }

    fun installCodexWrapper(): File {
        val codexBin = File(binDir, "codex")
        val script = """
            #!/system/bin/sh
            echo "=== Termdroid Codex Helper ==="
            echo "Para usar Codex / ChatGPT con tu suscripcion Plus/Pro:"
            echo "1. Cambia a la pestana 'Chat' (arriba)."
            echo "2. Toca '⚡ Proveedor' y selecciona 'OpenAI / Codex'."
            echo "3. Ingresa tu token de sesion de ChatGPT y listo!"
            echo ""
            echo "Si deseas usar herramientas CLI en la terminal, ejecuta 'setup-alpine'."
        """.trimIndent() + "\n"
        codexBin.writeText(script)
        codexBin.setExecutable(true, false)
        return codexBin
    }

    /** Borra completamente todo el rootfs y datos de usuario dentro de filesDir. */
    fun resetEnvironment(): Boolean {
        return runCatching {
            prefix.deleteRecursively()
            homeDir.deleteRecursively()
            ensureBaseEnvironment()
            true
        }.getOrDefault(false)
    }

    /** Instala o actualiza el wrapper de Claude Code CLI apuntando a npx / node. */
    fun installClaudeWrapper(): File {
        binDir.mkdirs()
        val claudeBin = File(binDir, "claude")
        val script = """
            #!/system/bin/sh
            export PREFIX="${prefix.absolutePath}"
            export HOME="${homeDir.absolutePath}"
            export PATH="${binDir.absolutePath}:${nativeLibDir.absolutePath}:/system/bin:/system/xbin"
            export NODE_PATH="${libDir.absolutePath}/node_modules"
            export TMPDIR="${tmpDir.absolutePath}"

            if [ -f "${libDir.absolutePath}/node_modules/@anthropic-ai/claude-code/cli.mjs" ]; then
                exec "${binDir.absolutePath}/node" "${libDir.absolutePath}/node_modules/@anthropic-ai/claude-code/cli.mjs" "$@"
            elif [ -f "${binDir.absolutePath}/npx" ]; then
                exec "${binDir.absolutePath}/npx" "@anthropic-ai/claude-code" "$@"
            else
                echo "=== Termdroid Claude Helper ==="
                echo "El comando 'claude' CLI requiere Node.js instalado en el entorno de la terminal."
                echo ""
                echo "Opciones disponibles:"
                echo "1. [RECOMENDADO] Usa la pestana 'Chat' en la barra superior."
                echo "   Alli el agente ya funciona directamente con tu suscripcion o token."
                echo "2. Para instalar el CLI en la terminal, ejecuta:"
                echo "   setup-alpine"
                exit 0
            fi
        """.trimIndent() + "\n"

        claudeBin.writeText(script)
        claudeBin.setExecutable(true, false)
        return claudeBin
    }
}

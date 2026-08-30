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
    val hasNode: Boolean get() = File(prefix, "alpine/usr/bin/node").exists()
    val hasClaude: Boolean get() = File(prefix, "alpine/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe").exists()

    /** Inicializa la estructura base del entorno Unix dentro del directorio privado. */
    fun ensureBaseEnvironment(): Boolean {
        binDir.mkdirs()
        libDir.mkdirs()
        homeDir.mkdirs()
        tmpDir.mkdirs()
        File(filesDir, "workspace").mkdirs()

        setupToolWrapper("rg")
        setupToolWrapper("jaq")
        setupTermdroidCli()
        setupHardwareCliWrappers()
        installClaudeWrapper()
        installCodexWrapper()
        installAgyWrapper()
        setupAlpineInstaller()
        setupHomeProfile()
        setupAgentInstructionFiles()

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
            "xdg-open" to """
                #!/system/bin/sh
                URL="${'$'}1"
                if [ -n "${'$'}URL" ]; then
                    am start -a android.intent.action.VIEW -d "${'$'}URL" >/dev/null 2>&1 || echo "Enlace: ${'$'}URL"
                fi
            """.trimIndent() + "\n",
            "open-url" to """
                #!/system/bin/sh
                am start -a android.intent.action.VIEW -d "${'$'}1" >/dev/null 2>&1
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
                        echo "Node: no instalado (ejecuta 'setup-environment' para instalar)"
                    fi
                    echo "Claude CLI: activo (escribe 'claude')"
                    echo "Codex CLI: activo (escribe 'codex')"
                    echo "Gemini/AGY CLI: activo (escribe 'agy')"
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
                    setup-environment
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
            export PATH="${prefix.absolutePath}/bin:${binDir.absolutePath}:${nativeLibDir.absolutePath}:/system/bin:/system/xbin:${'$'}PATH"
            export LD_LIBRARY_PATH="${prefix.absolutePath}/lib:${nativeLibDir.absolutePath}:${'$'}LD_LIBRARY_PATH"
            export NODE_PATH="${prefix.absolutePath}/lib/node_modules"
            export PS1='termdroid:\w\$ '

            claude() {
                if [ -f "${prefix.absolutePath}/bin/claude" ]; then
                    "${prefix.absolutePath}/bin/claude" "${'$'}@"
                elif [ -f "${prefix.absolutePath}/alpine/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe" ]; then
                    "${prefix.absolutePath}/bin/node" "${prefix.absolutePath}/alpine/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe" "${'$'}@"
                elif [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                    "${nativeLibDir.absolutePath}/libtdcli.so" claude "${'$'}@"
                else
                    sh "${binDir.absolutePath}/claude" "${'$'}@"
                fi
            }
            codex() {
                if [ -f "${prefix.absolutePath}/bin/codex" ]; then
                    "${prefix.absolutePath}/bin/codex" "${'$'}@"
                elif [ -f "${prefix.absolutePath}/alpine/usr/lib/node_modules/@openai/codex/bin/codex.js" ]; then
                    "${prefix.absolutePath}/bin/node" "${prefix.absolutePath}/alpine/usr/lib/node_modules/@openai/codex/bin/codex.js" "${'$'}@"
                elif [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                    "${nativeLibDir.absolutePath}/libtdcli.so" codex "${'$'}@"
                else
                    sh "${binDir.absolutePath}/codex" "${'$'}@"
                fi
            }
            agy() {
                if [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                    "${nativeLibDir.absolutePath}/libtdcli.so" agy "${'$'}@"
                else
                    sh "${binDir.absolutePath}/agy" "${'$'}@"
                fi
            }
            gemini() {
                if [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                    "${nativeLibDir.absolutePath}/libtdcli.so" gemini "${'$'}@"
                else
                    sh "${binDir.absolutePath}/gemini" "${'$'}@"
                fi
            }
            node() {
                if [ -f "${prefix.absolutePath}/bin/node" ]; then
                    "${prefix.absolutePath}/bin/node" "${'$'}@"
                else
                    echo "Node.js no esta instalado. Ejecuta 'setup-environment' para instalarlo."
                fi
            }
            npm() {
                if [ -f "${prefix.absolutePath}/bin/npm" ]; then
                    "${prefix.absolutePath}/bin/npm" "${'$'}@"
                else
                    echo "npm no esta instalado. Ejecuta 'setup-environment' para instalarlo."
                fi
            }
            termdroid() {
                sh "${binDir.absolutePath}/termdroid" "${'$'}@"
            }
            setup-environment() {
                if [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                    "${nativeLibDir.absolutePath}/libtdcli.so" install
                else
                    sh "${binDir.absolutePath}/setup-environment"
                fi
            }
            setup-node() {
                setup-environment
            }
            install-node() {
                setup-environment
            }
            xdg-open() {
                am start -a android.intent.action.VIEW -d "${'$'}1" >/dev/null 2>&1
            }
            open-url() {
                am start -a android.intent.action.VIEW -d "${'$'}1" >/dev/null 2>&1
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
        val target = File(binDir, "setup-environment")
        val script = """
            #!/system/bin/sh
            export PREFIX="${prefix.absolutePath}"
            export HOME="${homeDir.absolutePath}"
            export PATH="${prefix.absolutePath}/bin:${binDir.absolutePath}:${nativeLibDir.absolutePath}:/system/bin:/system/xbin"
            export TMPDIR="${prefix.absolutePath}/tmp"

            echo "==========================================="
            echo " 📦 Instalador de Node.js y CLIs Oficiales"
            echo "==========================================="

            if [ ! -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                echo "❌ Error: el instalador nativo no esta disponible."
                exit 1
            fi

            exec "${nativeLibDir.absolutePath}/libtdcli.so" install
        """.trimIndent() + "\n"
        target.writeText(script)
        target.setExecutable(true, false)

        val targetAlias = File(binDir, "setup-node")
        targetAlias.writeText(script)
        targetAlias.setExecutable(true, false)

        val targetAlias2 = File(binDir, "install-node")
        targetAlias2.writeText(script)
        targetAlias2.setExecutable(true, false)
    }

    fun installCodexWrapper(): File {
        binDir.mkdirs()
        val codexBin = File(binDir, "codex")
        val script = """
            #!/system/bin/sh
            export PREFIX="${prefix.absolutePath}"
            export HOME="${homeDir.absolutePath}"
            export PATH="${prefix.absolutePath}/bin:${binDir.absolutePath}:${nativeLibDir.absolutePath}:/system/bin:/system/xbin"

            if [ -f "${prefix.absolutePath}/alpine/usr/lib/node_modules/@openai/codex/bin/codex.js" ]; then
                exec "${prefix.absolutePath}/bin/node" "${prefix.absolutePath}/alpine/usr/lib/node_modules/@openai/codex/bin/codex.js" "${'$'}@"
            elif [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                exec "${nativeLibDir.absolutePath}/libtdcli.so" codex "${'$'}@"
            else
                echo "Termdroid CLI inicializando..."
            fi
        """.trimIndent() + "\n"
        codexBin.writeText(script)
        codexBin.setExecutable(true, false)
        return codexBin
    }

    fun installAgyWrapper(): File {
        binDir.mkdirs()
        val agyBin = File(binDir, "agy")
        val geminiBin = File(binDir, "gemini")
        val script = """
            #!/system/bin/sh
            if [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                exec "${nativeLibDir.absolutePath}/libtdcli.so" agy "${'$'}@"
            else
                echo "Termdroid CLI inicializando..."
            fi
        """.trimIndent() + "\n"
        agyBin.writeText(script)
        agyBin.setExecutable(true, false)
        geminiBin.writeText(script)
        geminiBin.setExecutable(true, false)
        return agyBin
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
            export PATH="${prefix.absolutePath}/bin:${binDir.absolutePath}:${nativeLibDir.absolutePath}:/system/bin:/system/xbin"

            if [ -f "${prefix.absolutePath}/alpine/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe" ]; then
                exec "${prefix.absolutePath}/bin/node" "${prefix.absolutePath}/alpine/usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe" "${'$'}@"
            elif [ -f "${nativeLibDir.absolutePath}/libtdcli.so" ]; then
                exec "${nativeLibDir.absolutePath}/libtdcli.so" claude "${'$'}@"
            else
                echo "Termdroid CLI inicializando..."
            fi
        """.trimIndent() + "\n"

        claudeBin.writeText(script)
        claudeBin.setExecutable(true, false)
        return claudeBin
    }

    /** Escribe CLAUDE.md y AGENTS.md en home y workspace para instruir a cualquier agente de IA. */
    fun setupAgentInstructionFiles() {
        val agentDoc = """
            # Termdroid Agent Policy & Device Capabilities

            ## Overview
            Termdroid is an autonomous agent runtime and isolated Unix terminal environment running on Android.

            ## Environment & Paths
            - **OS**: Android (Linux Kernel with Bionic libc and Toybox userspace).
            - **Shell**: `/system/bin/sh` (toybox). Do not assume GNU coreutils flags.
            - **Home**: `${'$'}HOME` (${homeDir.absolutePath})
            - **Prefix**: `${'$'}PREFIX` (${prefix.absolutePath})
            - **Workspace**: ${filesDir.absolutePath}/workspace
            - **Temp**: `${'$'}TMPDIR` (${cacheDir.absolutePath})
            - **External Storage**: `/sdcard` or `/storage/emulated/0`

            ## Native Termdroid CLI Tools
            - `termdroid info`: Display environment details and runtime status.
            - `termdroid battery`: Query device battery capacity and status.
            - `termdroid clipboard [get|set <text>]`: Read or update the Android system clipboard.
            - `termdroid tts "<message>"`: Announce text aloud via Android Text-to-Speech engine.
            - `rg`: Fast code search via Ripgrep native binary.
            - `jaq`: JSON parser and query utility.

            ## Android System Commands
            - `am start -a android.intent.action.VIEW -d "<url>"`: Launch URL in web browser.
            - `pm list packages`: List installed Android packages.
            - `pm dump <package>`: Inspect app info and permissions.
            - `dumpsys battery`: Battery diagnostics.
            - `settings get [system|secure|global] <key>`: Query system settings.

            ## Agent Guidelines & Security
            1. **W^X & Execution**: Due to Android SELinux policies, run scripts via `sh <path>` or native library binaries.
            2. **Permissions**: Handle missing permissions gracefully and inform the user.
            3. **Conciseness**: Keep responses clear, compact, and formatted for mobile screens.
            4. **Safety**: Never execute destructive actions without explicit user confirmation.
        """.trimIndent() + "\n"

        listOf(
            File(homeDir, "CLAUDE.md"),
            File(homeDir, "AGENTS.md"),
            File(homeDir, "GEMINI.md"),
            File(File(filesDir, "workspace"), "CLAUDE.md"),
            File(File(filesDir, "workspace"), "AGENTS.md"),
            File(File(prefix, "etc").apply { mkdirs() }, "CLAUDE.md"),
        ).forEach { file ->
            file.writeText(agentDoc)
        }
    }
}

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
                        echo "Node: no instalado"
                    fi
                    if [ -f "${'$'}PREFIX/bin/claude" ]; then
                        echo "Claude CLI: presente"
                    else
                        echo "Claude CLI: no instalado"
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
                reset)
                    echo "Borrando entorno Termdroid..."
                    rm -rf "${'$'}PREFIX" "${'$'}HOME"
                    echo "Entorno reiniciado. Reinicia la app para reconstruir base."
                    ;;
                *)
                    echo "Termdroid CLI Helper"
                    echo "Uso: termdroid [info | battery | clipboard | tts | reset]"
                    ;;
            esac
        """.trimIndent() + "\n"

        target.writeText(content)
        target.setExecutable(true, false)
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
        ensureBaseEnvironment()
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
                echo "Claude Code requiere Node.js instalado en el entorno."
                echo "Ejecuta el asistente de setup o termdroid info."
                exit 1
            fi
        """.trimIndent() + "\n"

        claudeBin.writeText(script)
        claudeBin.setExecutable(true, false)
        return claudeBin
    }
}

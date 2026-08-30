package com.termdroid.rootfs

import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Instalador de entorno base Linux (Alpine Musl) y Node.js para Android.
 * No depende de Termux ni de rutas hardcodeadas /data/data/com.termux.
 */
object NodeInstaller {

    private const val ALPINE_VERSION = "v3.21"
    private const val ALPINE_RELEASE = "3.21.3"

    fun isNodeInstalled(prefix: File): Boolean {
        val alpineDir = File(prefix, "alpine")
        val nodeBin = File(alpineDir, "usr/bin/node")
        return nodeBin.exists()
    }

    fun isClaudeInstalled(prefix: File): Boolean {
        val alpineDir = File(prefix, "alpine")
        return File(alpineDir, "usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe").exists()
    }

    fun isCodexInstalled(prefix: File): Boolean {
        val alpineDir = File(prefix, "alpine")
        return File(alpineDir, "usr/lib/node_modules/@openai/codex/bin/codex.js").exists()
    }

    /**
     * Descarga e instala Alpine Linux rootfs y configura el entorno para Node.js.
     */
    suspend fun installFullEnvironment(
        prefix: File,
        cacheDir: File,
        onProgress: (String) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        installEnvironment(
            prepare = {
                val arch = detectArch()
                val tarGzName = "alpine-minirootfs-$ALPINE_RELEASE-$arch.tar.gz"
                val urlStr = "https://dl-cdn.alpinelinux.org/alpine/$ALPINE_VERSION/releases/$arch/$tarGzName"
                val targetTarGz = File(cacheDir, tarGzName)
                val alpineDir = File(prefix, "alpine")
                val stagingDir = File(prefix, "alpine.installing")

                onProgress("Descargando Alpine Linux ($arch)...")
                var lastReportedPct = -1
                downloadFile(urlStr, targetTarGz) { pct ->
                    if (pct % 25 == 0 && pct != lastReportedPct) {
                        lastReportedPct = pct
                        onProgress("Descargando Alpine Linux: $pct%")
                    }
                }

                onProgress("Extrayendo sistema Linux...")
                stagingDir.deleteRecursively()
                extractTarGz(targetTarGz, stagingDir)
                targetTarGz.delete()

                alpineDir.deleteRecursively()
                check(stagingDir.renameTo(alpineDir)) { "No se pudo activar el sistema Linux descargado" }

                onProgress("Configurando DNS y repositorios...")
                configureAlpine(alpineDir, prefix, arch)
            },
            install = {
                onProgress("Instalando Node.js, npm y Git...")
                installPackages(prefix, onProgress)
            },
            validate = {
                onProgress("Validando Node.js y CLIs...")
                validateInstallation(prefix)
                onProgress("Entorno completo instalado correctamente.")
            },
        )
    }

    internal fun installEnvironment(
        prepare: () -> Unit,
        install: () -> Unit,
        validate: () -> Unit,
    ): Result<Unit> = runCatching {
        prepare()
        install()
        validate()
    }

    private fun installPackages(prefix: File, onProgress: (String) -> Unit) {
        val alpineDir = File(prefix, "alpine")
        val apk = File(alpineDir, "sbin/apk")
        runAlpineBinary(
            alpineDir,
            apk,
            listOf(
                "--root", alpineDir.absolutePath,
                "--keys-dir", "/etc/apk/keys",
                "--repositories-file", "/etc/apk/repositories",
                "--no-cache",
                "--no-scripts",
                "add", "nodejs", "npm", "git",
            ),
            onProgress,
        )

        runScript(
            File(prefix, "bin/npm"),
            listOf(
                "install", "--global",
                "--script-shell", File(prefix, "bin/alpine-sh").absolutePath,
                "--prefix", File(alpineDir, "usr").absolutePath,
                "@anthropic-ai/claude-code", "@openai/codex",
            ),
            onProgress,
        )
    }

    private fun validateInstallation(prefix: File) {
        listOf(
            "Node.js" to Pair(File(prefix, "bin/node"), listOf("--version")),
            "npm" to Pair(File(prefix, "bin/npm"), listOf("--version")),
            "Claude Code" to Pair(File(prefix, "bin/claude"), listOf("--version")),
            "Codex" to Pair(File(prefix, "bin/codex"), listOf("--version")),
        ).forEach { (name, command) ->
            runScript(command.first, command.second) { }
                .takeIf { it.isNotBlank() }
                ?: error("$name no devolvio una version valida")
        }
    }

    private fun runAlpineBinary(
        alpineDir: File,
        executable: File,
        args: List<String>,
        onOutput: (String) -> Unit,
    ): String {
        val arch = detectArch()
        val linker = File(alpineDir, "lib/${muslLinkerName(arch)}")
        val libPath = "${File(alpineDir, "lib").absolutePath}:${File(alpineDir, "usr/lib").absolutePath}"
        return runProcess(listOf(linker.absolutePath, "--library-path", libPath, executable.absolutePath) + args, onOutput)
    }

    private fun runScript(script: File, args: List<String>, onOutput: (String) -> Unit): String =
        runProcess(listOf("/system/bin/sh", script.absolutePath) + args, onOutput)

    private fun runProcess(command: List<String>, onOutput: (String) -> Unit): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = buildString {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    appendLine(line)
                    onOutput(line)
                }
            }
        }.trim()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "${command.firstOrNull().orEmpty()} fallo ($exitCode): $output" }
        return output
    }

    private fun configureAlpine(alpineDir: File, prefix: File, arch: String) {
        val etc = File(alpineDir, "etc").apply { mkdirs() }
        val resolv = File(etc, "resolv.conf")
        resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        val apkDir = File(etc, "apk").apply { mkdirs() }
        val repos = File(apkDir, "repositories")
        repos.writeText("https://dl-cdn.alpinelinux.org/alpine/$ALPINE_VERSION/main\nhttps://dl-cdn.alpinelinux.org/alpine/$ALPINE_VERSION/community\n")

        val binDir = File(prefix, "bin").apply { mkdirs() }
        val muslLinker = muslLinkerName(arch)
        val linkerPath = File(alpineDir, "lib/$muslLinker").absolutePath
        val libPath = "${File(alpineDir, "lib").absolutePath}:${File(alpineDir, "usr/lib").absolutePath}"

        // Wrapper de ejecucion para Alpine shell
        val alpineSh = File(binDir, "alpine-sh")
        alpineSh.writeText("""
            #!/system/bin/sh
            exec "$linkerPath" --library-path "$libPath" "${File(alpineDir, "bin/sh").absolutePath}" "${'$'}@"
        """.trimIndent() + "\n")
        alpineSh.setExecutable(true, false)

        // Wrapper de ejecucion para Node
        val nodeWrapper = File(binDir, "node")
        nodeWrapper.writeText("""
            #!/system/bin/sh
            for ICU_DIR in "${File(alpineDir, "usr/share/icu").absolutePath}"/*; do export NODE_ICU_DATA="${'$'}ICU_DIR"; break; done
            if [ -f "${File(alpineDir, "usr/bin/node").absolutePath}" ]; then
                exec "$linkerPath" --library-path "$libPath" "${File(alpineDir, "usr/bin/node").absolutePath}" "${'$'}@"
            else
                echo "Node.js no esta instalado aun en Alpine. Ejecuta 'setup-environment'."
            fi
        """.trimIndent() + "\n")
        nodeWrapper.setExecutable(true, false)

        // Wrapper de ejecucion para npm
        val npmWrapper = File(binDir, "npm")
        npmWrapper.writeText("""
            #!/system/bin/sh
            export HOME="${File(prefix.parentFile, "home").absolutePath}"
            export PATH="${binDir.absolutePath}:/system/bin:/system/xbin"
            if [ -f "${File(alpineDir, "usr/bin/node").absolutePath}" ]; then
                exec "$linkerPath" --library-path "$libPath" "${File(alpineDir, "usr/bin/node").absolutePath}" "${File(alpineDir, "usr/lib/node_modules/npm/bin/npm-cli.js").absolutePath}" "${'$'}@"
            else
                echo "npm no esta instalado aun. Ejecuta 'setup-environment'."
            fi
        """.trimIndent() + "\n")
        npmWrapper.setExecutable(true, false)

        // Wrapper de ejecucion para Claude Code CLI
        val claudeWrapper = File(binDir, "claude")
        claudeWrapper.writeText("""
            #!/system/bin/sh
            CLI_PATH="${File(alpineDir, "usr/lib/node_modules/@anthropic-ai/claude-code/bin/claude.exe").absolutePath}"
            if [ -f "${'$'}CLI_PATH" ]; then
                exec "$linkerPath" --library-path "$libPath" "${File(alpineDir, "usr/bin/node").absolutePath}" "${'$'}CLI_PATH" "${'$'}@"
            else
                echo "Claude Code CLI (@anthropic-ai/claude-code) no esta instalado aun. Ejecuta 'setup-environment'."
            fi
        """.trimIndent() + "\n")
        claudeWrapper.setExecutable(true, false)

        // Wrapper de ejecucion para Codex CLI
        val codexWrapper = File(binDir, "codex")
        codexWrapper.writeText("""
            #!/system/bin/sh
            CLI_PATH="${File(alpineDir, "usr/lib/node_modules/@openai/codex/bin/codex.js").absolutePath}"
            if [ -f "${'$'}CLI_PATH" ]; then
                exec "$linkerPath" --library-path "$libPath" "${File(alpineDir, "usr/bin/node").absolutePath}" "${'$'}CLI_PATH" "${'$'}@"
            else
                echo "Codex CLI (@openai/codex) no esta instalado aun. Ejecuta 'setup-environment'."
            fi
        """.trimIndent() + "\n")
        codexWrapper.setExecutable(true, false)

        // Dar permisos de ejecucion a todos los binarios dentro de Alpine
        listOf(
            File(alpineDir, "bin"),
            File(alpineDir, "sbin"),
            File(alpineDir, "usr/bin"),
            File(alpineDir, "usr/sbin"),
            File(alpineDir, "lib"),
        ).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        f.setExecutable(true, false)
                        f.setReadable(true, false)
                    }
                }
            }
        }
    }

    private fun extractTarGz(tarGzFile: File, targetDir: File) {
        targetDir.mkdirs()
        GZIPInputStream(BufferedInputStream(tarGzFile.inputStream())).use { gzipIn ->
            val header = ByteArray(512)
            while (true) {
                var read = 0
                while (read < 512) {
                    val n = gzipIn.read(header, read, 512 - read)
                    if (n <= 0) break
                    read += n
                }
                if (read < 512) break
                if (header.all { it.toInt() == 0 }) break

                val name = String(header, 0, 100).trim { it <= ' ' || it == '\u0000' }
                if (name.isBlank()) continue

                val sizeStr = String(header, 124, 12).trim { it <= ' ' || it == '\u0000' }
                val size = if (sizeStr.isNotEmpty()) sizeStr.toLong(8) else 0L
                val typeFlag = header[156].toInt().toChar()

                val entryFile = File(targetDir, name)
                check(isSafeTarEntry(targetDir, entryFile)) {
                    "Entrada insegura en rootfs: $name"
                }
                if (typeFlag == '5' || name.endsWith("/")) {
                    entryFile.mkdirs()
                } else if (typeFlag == '2') {
                    val linkTarget = String(header, 157, 100).trim { it <= ' ' || it == '\u0000' }
                    entryFile.parentFile?.mkdirs()
                    entryFile.delete()
                    runCatching { Os.symlink(linkTarget, entryFile.absolutePath) }
                } else if (typeFlag == '0' || typeFlag == '\u0000') {
                    entryFile.parentFile?.mkdirs()
                    FileOutputStream(entryFile).use { out ->
                        var remaining = size
                        val buf = ByteArray(8192)
                        while (remaining > 0) {
                            val toRead = minOf(buf.size.toLong(), remaining).toInt()
                            val n = gzipIn.read(buf, 0, toRead)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            remaining -= n
                        }
                    }
                    val pad = (512 - (size % 512)) % 512
                    if (pad > 0) {
                        var padRemaining = pad
                        while (padRemaining > 0) {
                            val skipped = gzipIn.read(ByteArray(padRemaining.toInt()))
                            if (skipped <= 0) break
                            padRemaining -= skipped
                        }
                    }
                    entryFile.setExecutable(true, false)
                    entryFile.setReadable(true, false)
                }
            }
        }
    }

    internal fun isSafeTarEntry(targetDir: File, entryFile: File): Boolean {
        val root = targetDir.canonicalPath
        val entry = entryFile.canonicalPath
        return entry == root || entry.startsWith(root + File.separator)
    }

    private fun detectArch(): String {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase()
        return when {
            primaryAbi.contains("arm64") || primaryAbi.contains("aarch64") -> "aarch64"
            primaryAbi.contains("x86_64") || primaryAbi.contains("amd64") -> "x86_64"
            else -> error("ABI no soportada: ${Build.SUPPORTED_ABIS.joinToString()}")
        }
    }

    private fun muslLinkerName(arch: String): String = when (arch) {
        "aarch64" -> "ld-musl-aarch64.so.1"
        "x86_64" -> "ld-musl-x86_64.so.1"
        else -> error("Arquitectura Alpine no soportada: $arch")
    }

    private fun downloadFile(urlStr: String, destination: File, onPercent: (Int) -> Unit) {
        destination.parentFile?.mkdirs()
        var conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "Termdroid-Installer/1.0")

        var responseCode = conn.responseCode
        var redirects = 0
        while (responseCode in 300..399 && redirects < 5) {
            val newUrl = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            conn = URL(newUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Termdroid-Installer/1.0")
            responseCode = conn.responseCode
            redirects++
        }

        if (responseCode !in 200..299) {
            throw RuntimeException("HTTP $responseCode al descargar $urlStr")
        }

        val totalBytes = conn.contentLengthLong
        var downloadedBytes = 0L

        conn.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(32768)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    if (totalBytes > 0) {
                        val pct = ((downloadedBytes * 100) / totalBytes).toInt()
                        onPercent(pct)
                    }
                }
            }
        }
        conn.disconnect()
    }
}

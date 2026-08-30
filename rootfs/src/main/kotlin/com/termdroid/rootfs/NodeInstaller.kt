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
import java.util.zip.ZipInputStream

/**
 * Instalador nativo del entorno base de paquetes Unix, Node.js y los CLIs oficiales.
 */
object NodeInstaller {

    private const val BOOTSTRAP_BASE_URL =
        "https://github.com/termux/termux-packages/releases/latest/download"

    private const val NODE_DEB_BASE_URL =
        "https://packages.termux.dev/apt/termux-main/pool/main/n/nodejs"

    fun isNodeInstalled(prefix: File): Boolean {
        val nodeBin = File(prefix, "bin/node")
        val npmBin = File(prefix, "bin/npm")
        return nodeBin.exists() && npmBin.exists()
    }

    fun isClaudeInstalled(prefix: File): Boolean {
        val cliMjs = File(prefix, "lib/node_modules/@anthropic-ai/claude-code/cli.mjs")
        val binClaude = File(prefix, "bin/claude")
        return cliMjs.exists() || binClaude.exists()
    }

    fun isCodexInstalled(prefix: File): Boolean {
        val cliMjs = File(prefix, "lib/node_modules/@openai/codex/cli.mjs")
        val binCodex = File(prefix, "bin/codex")
        return cliMjs.exists() || binCodex.exists()
    }

    /**
     * Descarga e instala el bootstrap base y Node.js de forma directa y deterministica.
     */
    suspend fun installFullEnvironment(
        prefix: File,
        cacheDir: File,
        onProgress: (String) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val arch = detectArch()
            val zipUrl = "$BOOTSTRAP_BASE_URL/bootstrap-$arch.zip"
            val targetZip = File(cacheDir, "bootstrap-$arch.zip")

            onProgress("Descargando sistema base ($arch)...")
            var lastReportedPct = -1
            downloadFile(zipUrl, targetZip) { pct ->
                if (pct % 25 == 0 && pct != lastReportedPct) {
                    lastReportedPct = pct
                    onProgress("Descargando sistema base: $pct%")
                }
            }

            onProgress("Extrayendo paquetes base...")
            prefix.mkdirs()
            unzip(targetZip, prefix)
            targetZip.delete()

            onProgress("Configurando symlinks y permisos...")
            applySymlinks(prefix)
            setPermissions(prefix)

            // Descargar e instalar Node.js directamente
            onProgress("Descargando Node.js oficial...")
            val nodeDebName = if (arch == "aarch64") "nodejs_26.4.0-1_aarch64.deb" else "nodejs_26.4.0-1_x86_64.deb"
            val nodeDebUrl = "$NODE_DEB_BASE_URL/$nodeDebName"
            val targetDeb = File(cacheDir, nodeDebName)

            runCatching {
                downloadFile(nodeDebUrl, targetDeb) { pct ->
                    if (pct % 50 == 0) onProgress("Descargando Node.js: $pct%")
                }

                onProgress("Desempaquetando Node.js...")
                val extractDir = File(cacheDir, "node_extracted").apply { mkdirs() }
                val dpkgBin = File(prefix, "bin/dpkg-deb").takeIf { it.exists() } ?: File(prefix, "bin/dpkg")
                if (dpkgBin.exists()) {
                    val p = ProcessBuilder(
                        dpkgBin.absolutePath,
                        "-x",
                        targetDeb.absolutePath,
                        extractDir.absolutePath
                    ).apply {
                        environment()["LD_LIBRARY_PATH"] = File(prefix, "lib").absolutePath
                    }.start()
                    p.waitFor()

                    // Mover archivos desde data/data/com.termux/files/usr hacia prefix
                    val unpackedUsr = File(extractDir, "data/data/com.termux/files/usr")
                    if (unpackedUsr.exists()) {
                        unpackedUsr.walkTopDown().forEach { f ->
                            val rel = f.relativeTo(unpackedUsr).path
                            if (rel.isNotBlank()) {
                                val dest = File(prefix, rel)
                                if (f.isDirectory) {
                                    dest.mkdirs()
                                } else {
                                    dest.parentFile?.mkdirs()
                                    f.copyTo(dest, overwrite = true)
                                }
                            }
                        }
                    }
                }
                targetDeb.delete()
                extractDir.deleteRecursively()
            }

            // Asegurar symlinks de npm y permisos de node
            val nodeBin = File(prefix, "bin/node")
            if (nodeBin.exists()) {
                nodeBin.setExecutable(true, false)
                val npmCli = File(prefix, "lib/node_modules/npm/bin/npm-cli.js")
                val npmBin = File(prefix, "bin/npm")
                if (npmCli.exists() && !npmBin.exists()) {
                    npmBin.writeText("#!/system/bin/sh\nexec \"${nodeBin.absolutePath}\" \"${npmCli.absolutePath}\" \"$@\"\n")
                    npmBin.setExecutable(true, false)
                }
                val npxCli = File(prefix, "lib/node_modules/npm/bin/npx-cli.js")
                val npxBin = File(prefix, "bin/npx")
                if (npxCli.exists() && !npxBin.exists()) {
                    npxBin.writeText("#!/system/bin/sh\nexec \"${nodeBin.absolutePath}\" \"${npxCli.absolutePath}\" \"$@\"\n")
                    npxBin.setExecutable(true, false)
                }
            }

            setPermissions(prefix)
            onProgress("Sistema base y Node.js instalados correctamente.")
        }
    }

    private fun detectArch(): String {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase()
        return when {
            primaryAbi.contains("arm64") || primaryAbi.contains("aarch64") -> "aarch64"
            primaryAbi.contains("x86_64") || primaryAbi.contains("amd64") -> "x86_64"
            primaryAbi.contains("arm") -> "arm"
            primaryAbi.contains("x86") || primaryAbi.contains("i686") -> "i686"
            else -> "aarch64"
        }
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

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun applySymlinks(prefix: File) {
        val symlinksFile = File(prefix, "SYMLINKS.txt")
        if (!symlinksFile.exists()) return

        symlinksFile.forEachLine { line ->
            val parts = line.split("←")
            if (parts.size == 2) {
                val target = parts[0].trim()
                val symlinkRel = parts[1].trim().removePrefix("./")
                val symlinkFile = File(prefix, symlinkRel)
                symlinkFile.parentFile?.mkdirs()
                symlinkFile.delete()
                runCatching {
                    Os.symlink(target, symlinkFile.absolutePath)
                }
            }
        }
        symlinksFile.delete()
    }

    private fun setPermissions(prefix: File) {
        listOf(
            File(prefix, "bin"),
            File(prefix, "libexec"),
            File(prefix, "lib/apt/methods"),
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
}

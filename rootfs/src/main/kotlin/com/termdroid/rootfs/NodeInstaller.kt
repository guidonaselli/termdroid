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
     * Descarga e instala el bootstrap base y configura DNS / APT para instalación de paquetes.
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

            onProgress("Configurando DNS y repositorios APT...")
            configureAptAndDns(prefix)

            onProgress("Sistema base configurado con exito.")
        }
    }

    fun configureAptAndDns(prefix: File) {
        val etc = File(prefix, "etc").apply { mkdirs() }
        val aptDir = File(etc, "apt").apply { mkdirs() }
        File(aptDir, "trusted.gpg.d").mkdirs()

        // DNS resolver para Bionic libc
        val resolv = File(etc, "resolv.conf")
        resolv.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")

        // Repositorio de paquetes
        val sources = File(aptDir, "sources.list")
        sources.writeText("deb https://packages.termux.dev/apt/termux-main stable main\n")

        // Configuracion de rutas para apt
        val aptConf = File(aptDir, "apt.conf")
        aptConf.writeText("""
            Dir "${prefix.absolutePath}";
            Dir::State "${prefix.absolutePath}/var/lib/apt";
            Dir::State::status "${prefix.absolutePath}/var/lib/dpkg/status";
            Dir::Cache "${prefix.absolutePath}/var/cache/apt";
            Dir::Etc "${prefix.absolutePath}/etc/apt";
            Acquire::Languages "none";
        """.trimIndent() + "\n")

        // Estructura de estado de dpkg y apt
        val dpkgDir = File(prefix, "var/lib/dpkg").apply { mkdirs() }
        File(dpkgDir, "info").mkdirs()
        File(dpkgDir, "updates").mkdirs()
        val dpkgStatus = File(dpkgDir, "status")
        if (!dpkgStatus.exists()) dpkgStatus.writeText("")

        File(prefix, "var/lib/apt/lists/partial").mkdirs()
        File(prefix, "var/cache/apt/archives/partial").mkdirs()
        File(prefix, "tmp").mkdirs()
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

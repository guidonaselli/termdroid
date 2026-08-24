package com.termdroid.spike

import android.content.Context
import android.os.Build
import java.io.File

/** Los experimentos de F-001, uno por nivel del modelo de ejecucion. */
class ExecExperiments(private val ctx: Context) {

    private val nativeDir: String get() = ctx.applicationInfo.nativeLibraryDir
    private val nativeBinary: File get() = File(nativeDir, "lib$BINARY.so")
    private val dataBinary: File get() = File(ctx.filesDir, BINARY)

    /** El interprete dinamico de Android para esta ABI. */
    private val linker: String
        get() = if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) "/system/bin/linker64" else "/system/bin/linker"

    fun deviceInfo(): String = buildString {
        appendLine("device      = ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("android     = ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("abi         = ${Build.SUPPORTED_ABIS.joinToString()}")
        appendLine("targetSdk   = ${ctx.applicationInfo.targetSdkVersion}")
        appendLine("nativeDir   = $nativeDir")
        appendLine("filesDir    = ${ctx.filesDir}")
        appendLine("linker      = $linker (existe=${File(linker).exists()})")
    }

    /** Copia el binario de nativeLibraryDir a filesDir. */
    fun stageIntoFilesDir(): String = buildString {
        val src = nativeBinary
        if (!src.exists()) {
            appendLine("FALLO: no existe $src")
            appendLine("El packager no extrajo el binario. Revisar useLegacyPackaging/extractNativeLibs.")
            return@buildString
        }
        src.copyTo(dataBinary, overwrite = true)
        val chmod = dataBinary.setExecutable(true, false)
        appendLine("copiado  -> $dataBinary (${dataBinary.length()} bytes)")
        appendLine("setExecutable=$chmod canExecute=${dataBinary.canExecute()}")
    }

    /** Nivel 0: execve directo sobre filesDir. Deberia fallar en Android 10+. */
    fun level0(): String {
        if (!dataBinary.exists()) return "Correr primero: copiar a filesDir"
        return NativeExec.run(arrayOf(dataBinary.absolutePath, "nivel0"), ctx.filesDir.absolutePath)
    }

    /** Nivel 1: execve desde nativeLibraryDir. Es el piso garantizado. */
    fun level1(): String {
        val bin = nativeBinary
        if (!bin.exists()) return "FALLO: no existe $bin (revisar empaquetado)"
        return NativeExec.run(arrayOf(bin.absolutePath, "nivel1"), ctx.filesDir.absolutePath)
    }

    /** Nivel 2: execve del linker, con el binario de filesDir como argumento. */
    fun level2(): String {
        if (!dataBinary.exists()) return "Correr primero: copiar a filesDir"
        return NativeExec.run(
            arrayOf(linker, dataBinary.absolutePath, "nivel2"),
            ctx.filesDir.absolutePath,
        )
    }

    /** Nivel 2 sobre el binario de nativeLibraryDir. */
    fun level2Control(): String {
        val bin = nativeBinary
        if (!bin.exists()) return "FALLO: no existe $bin"
        return NativeExec.run(
            arrayOf(linker, bin.absolutePath, "nivel2-control"),
            ctx.filesDir.absolutePath,
        )
    }

    fun runAll(): String = buildString {
        appendLine("=== device ===")
        append(deviceInfo())
        appendLine()
        appendLine("=== staging ===")
        append(stageIntoFilesDir())
        appendLine()
        appendLine("=== nivel 0: execve directo sobre filesDir ===")
        appendLine(level0())
        appendLine("=== nivel 1: execve desde nativeLibraryDir ===")
        appendLine(level1())
        appendLine("=== nivel 2: linker + binario en filesDir ===")
        appendLine(level2())
        appendLine("=== nivel 2 (control): linker + binario en nativeLibraryDir ===")
        appendLine(level2Control())
    }

    private companion object {
        const val BINARY = "tdhello"
    }
}

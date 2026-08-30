package com.termdroid.exec

import android.content.Context
import android.os.Build
import java.io.File

/** Rutas fijas del entorno de ejecucion de esta app en este device. */
class ExecEnvironment(
    val nativeLibDir: File,
    val filesDir: File,
) {
    constructor(context: Context) : this(
        File(context.applicationInfo.nativeLibraryDir ?: ""),
        context.filesDir ?: File(""),
    )

    /** Raiz del rootfs. Equivale al prefix de un sistema Unix. */
    val prefix: File = File(filesDir, "usr")

    /** El interprete dinamico de Android para esta ABI. */
    val linker: File = File(
        if (Build.SUPPORTED_64_BIT_ABIS?.isNotEmpty() == true) "/system/bin/linker64" else "/system/bin/linker",
    )

    /** Ruta del binario del bootstrap que viaja en el APK. */
    fun packaged(name: String): File = File(nativeLibDir, "lib$name.so")
}

data class ExecResult(val output: String, val exitCode: Int) {
    val ok: Boolean get() = exitCode == 0
}

/** Traduce "quiero correr esto" al `argv` que este device acepta. */
class Executor(
    private val env: ExecEnvironment,
    private val backend: ExecBackend,
) {

    /** Arma el `argv` final para ejecutar [file]. */
    fun buildArgv(file: File, args: List<String> = emptyList()): List<String> {
        val shebang = readShebang(file)
        if (shebang != null) {
            return buildArgv(File(shebang.interpreter), shebang.arg?.let { listOf(it) } ?: emptyList()) +
                file.absolutePath + args
        }

        val underNativeLibDir = file.absolutePath.startsWith(env.nativeLibDir.absolutePath)
        return when {
            underNativeLibDir -> listOf(file.absolutePath) + args
            backend == ExecBackend.DIRECT -> listOf(file.absolutePath) + args
            backend == ExecBackend.LINKER -> listOf(env.linker.absolutePath, file.absolutePath) + args
            else -> listOf(file.absolutePath) + args
        }
    }

    fun run(file: File, args: List<String> = emptyList(), cwd: File = env.filesDir): ExecResult {
        if (backend == ExecBackend.NONE) {
            return ExecResult("Este device no puede ejecutar binarios.", -1)
        }
        val raw = NativeExec.run(buildArgv(file, args).toTypedArray(), cwd.absolutePath)
        return ExecResult(raw, parseExit(raw))
    }

    private data class Shebang(val interpreter: String, val arg: String?)

    private fun readShebang(file: File): Shebang? {
        if (!file.isFile || file.length() < 2) return null
        val head = runCatching {
            file.inputStream().use { s ->
                val b = ByteArray(256)
                val n = s.read(b)
                if (n <= 0) "" else String(b, 0, n)
            }
        }.getOrNull() ?: return null

        if (!head.startsWith("#!")) return null
        val line = head.substringBefore('\n').removePrefix("#!").trim()
        if (line.isEmpty()) return null
        val parts = line.split(Regex("\\s+"), limit = 2)
        return Shebang(parts[0], parts.getOrNull(1)?.takeIf { it.isNotBlank() })
    }

    private companion object {
        val EXIT = Regex("""\[exit=(-?\d+)]""")

        fun parseExit(raw: String): Int =
            EXIT.findAll(raw).lastOrNull()?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }
}

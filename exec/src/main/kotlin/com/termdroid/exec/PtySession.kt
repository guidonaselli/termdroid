package com.termdroid.exec

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Un proceso corriendo sobre una terminal de verdad.
 *
 * Un pipe no alcanza: sin tty no hay control de trabajos, ni edicion de linea,
 * ni programas de pantalla completa.
 */
class PtySession private constructor(
    private val masterFd: Int,
    val pid: Int,
) {
    private val closed = AtomicBoolean(false)

    /** Lee del proceso. Devuelve -1 cuando el otro extremo se cerro. */
    fun read(buf: ByteArray): Int = NativePty.read(masterFd, buf)

    fun write(data: ByteArray): Int = NativePty.write(masterFd, data)

    fun write(text: String): Int = write(text.toByteArray())

    /** Avisa el tamano de la vista. Sin esto los programas de pantalla completa dibujan mal. */
    fun resize(rows: Int, cols: Int) = NativePty.resize(masterFd, rows, cols)

    /** Bloquea hasta que el proceso termina. Devuelve su exit code. */
    fun waitFor(): Int = NativePty.waitFor(pid)

    fun close() {
        if (closed.compareAndSet(false, true)) {
            NativePty.close(masterFd, pid)
        }
    }

    companion object {
        /** Falla con [PtyUnavailable] en vez de devolver un fd invalido silencioso. */
        fun start(
            argv: List<String>,
            env: Map<String, String>,
            cwd: File,
            rows: Int = 24,
            cols: Int = 80,
        ): PtySession {
            val result = NativePty.open(
                argv.toTypedArray(),
                env.map { "${it.key}=${it.value}" }.toTypedArray(),
                cwd.absolutePath,
                rows,
                cols,
            )
            val fd = result[0]
            if (fd < 0) throw PtyUnavailable(-fd)
            return PtySession(fd, result[1])
        }
    }
}

class PtyUnavailable(val errno: Int) : Exception("forkpty fallo con errno=$errno")

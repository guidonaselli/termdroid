package com.termdroid.exec

internal object NativeExec {
    init {
        System.loadLibrary("tdexec")
    }

    /** fork + execv capturando stdout y stderr. Devuelve la salida y el exit code. */
    @JvmStatic
    external fun run(argv: Array<String>, cwd: String?): String
}

internal object NativePty {
    init {
        System.loadLibrary("tdpty")
    }

    /** Devuelve `[masterFd, pid]`. Un `masterFd` negativo es `-errno`. */
    @JvmStatic
    external fun open(
        argv: Array<String>,
        env: Array<String>,
        cwd: String?,
        rows: Int,
        cols: Int,
    ): IntArray

    @JvmStatic external fun read(fd: Int, buf: ByteArray): Int
    @JvmStatic external fun write(fd: Int, buf: ByteArray): Int
    @JvmStatic external fun resize(fd: Int, rows: Int, cols: Int)
    @JvmStatic external fun waitFor(pid: Int): Int
    @JvmStatic external fun close(fd: Int, pid: Int)
}

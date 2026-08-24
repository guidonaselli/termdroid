package com.termdroid.spike

object NativePty {
    init {
        System.loadLibrary("tdpty")
    }

    /** Devuelve [masterFd, pid]. Un masterFd negativo es -errno. */
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

package com.termdroid.spike

object NativeExec {
    init {
        System.loadLibrary("tdexec")
    }

    /** fork + execv con captura de stdout/stderr. Devuelve la salida y el exit code. */
    @JvmStatic
    external fun run(argv: Array<String>, cwd: String?): String
}

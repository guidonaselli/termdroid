package com.termdroid.spike

import android.content.Context
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Experimentos de PTY y shebangs (F-001 / S3).
 *
 * Un pipe no alcanza para un shell util: sin tty no hay control de trabajos,
 * ni edicion de linea, ni programas de pantalla completa. Lo que se verifica
 * es que el hijo tenga una terminal de verdad y que respete el tamano.
 */
class PtyExperiments(private val ctx: Context) {

    /**
     * Abre un shell sobre PTY, le manda un guion y devuelve lo que salio.
     *
     * `tty` prueba que hay terminal; `stty size` prueba que TIOCSWINSZ llego.
     */
    fun shellSession(rows: Int = 24, cols: Int = 80): String {
        val argv = arrayOf(SH)
        val env = arrayOf(
            "PATH=/system/bin:/system/xbin",
            "HOME=${ctx.filesDir}",
            "TERM=xterm-256color",
        )
        val (fd, pid) = NativePty.open(argv, env, ctx.filesDir.absolutePath, rows, cols).let {
            it[0] to it[1]
        }
        if (fd < 0) return "FALLO: forkpty errno=${-fd}"

        val out = StringBuilder()
        val done = CountDownLatch(1)
        val reader = Thread {
            val buf = ByteArray(4096)
            while (true) {
                val n = NativePty.read(fd, buf)
                if (n <= 0) break
                out.append(String(buf, 0, n))
            }
            done.countDown()
        }
        reader.isDaemon = true
        reader.start()

        fun send(cmd: String) {
            NativePty.write(fd, "$cmd\n".toByteArray())
            Thread.sleep(120)
        }

        send("echo PTY_OK")
        send("tty")
        send("stty size")
        send("echo shell=\$0 term=\$TERM")
        send("exit")

        done.await(4, TimeUnit.SECONDS)
        val code = NativePty.waitFor(pid)
        NativePty.close(fd, pid)

        return buildString {
            appendLine("pedido: rows=$rows cols=$cols")
            appendLine("exit=$code")
            appendLine("--- salida ---")
            append(out.toString())
        }
    }

    /** Cambiar el tamano en vivo debe reflejarse en `stty size`. */
    fun resizeDuringSession(): String {
        val argv = arrayOf(SH)
        val env = arrayOf("PATH=/system/bin:/system/xbin", "TERM=xterm")
        val r = NativePty.open(argv, env, ctx.filesDir.absolutePath, 24, 80)
        val fd = r[0]
        val pid = r[1]
        if (fd < 0) return "FALLO: forkpty errno=${-fd}"

        val out = StringBuilder()
        val done = CountDownLatch(1)
        Thread {
            val buf = ByteArray(4096)
            while (true) {
                val n = NativePty.read(fd, buf)
                if (n <= 0) break
                out.append(String(buf, 0, n))
            }
            done.countDown()
        }.apply { isDaemon = true }.start()

        NativePty.write(fd, "stty size\n".toByteArray())
        Thread.sleep(250)
        NativePty.resize(fd, 40, 120)
        Thread.sleep(150)
        NativePty.write(fd, "stty size\n".toByteArray())
        Thread.sleep(250)
        NativePty.write(fd, "exit\n".toByteArray())

        done.await(4, TimeUnit.SECONDS)
        NativePty.close(fd, pid)
        return buildString {
            appendLine("esperado: primero '24 80', despues '40 120'")
            appendLine("--- salida ---")
            append(out.toString())
        }
    }

    /**
     * Shebangs.
     *
     * Un script en filesDir no se puede ejecutar directo (mismo bloqueo que el
     * nivel 0) y tampoco pasa por el linker, porque no es ELF. La unica salida
     * es leer el `#!` y ejecutar al interprete con el script como argumento:
     * eso es lo que hara el exec shim.
     */
    fun shebang(): String {
        val script = File(ctx.filesDir, "hola.sh")
        script.writeText("#!$SH\necho SHEBANG_OK argv0=\$0 arg1=\$1\n")
        script.setExecutable(true, false)

        val directo = NativeExec.run(arrayOf(script.absolutePath, "x"), ctx.filesDir.absolutePath)
        val viaInterprete = NativeExec.run(
            arrayOf(interpreterOf(script) ?: SH, script.absolutePath, "x"),
            ctx.filesDir.absolutePath,
        )

        return buildString {
            appendLine("interprete leido del shebang: ${interpreterOf(script)}")
            appendLine("--- ejecucion directa (deberia fallar) ---")
            appendLine(directo)
            appendLine("--- via interprete (lo que hara el shim) ---")
            appendLine(viaInterprete)
        }
    }

    /** Lee la primera linea y devuelve el interprete si hay `#!`. */
    private fun interpreterOf(f: File): String? {
        val first = f.bufferedReader().use { it.readLine() } ?: return null
        if (!first.startsWith("#!")) return null
        return first.removePrefix("#!").trim().substringBefore(' ').ifEmpty { null }
    }

    fun runAll(): String = buildString {
        appendLine("=== shell sobre PTY ===")
        appendLine(shellSession())
        appendLine("=== resize en vivo ===")
        appendLine(resizeDuringSession())
        appendLine("=== shebang ===")
        appendLine(shebang())
    }

    private companion object {
        const val SH = "/system/bin/sh"
    }
}

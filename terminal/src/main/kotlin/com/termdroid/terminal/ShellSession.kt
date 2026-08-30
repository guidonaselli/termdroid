package com.termdroid.terminal

import android.content.Context
import com.termdroid.exec.ExecBackend
import com.termdroid.exec.ExecEnvironment
import com.termdroid.exec.PtySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Un shell corriendo, con su pantalla. */
class ShellSession(
    private val context: Context,
    private val backend: ExecBackend,
    private val scope: CoroutineScope,
) {
    private val env = ExecEnvironment(context)

    // El buffer lo mutan dos hilos: el lector del PTY y el de UI cuando cambia
    private val lock = Any()

    val buffer = TerminalBuffer(rows = 24, cols = 80)
    private val parser = VtParser(buffer)

    private val _screen = MutableStateFlow(ScreenSnapshot.EMPTY)
    val screen: StateFlow<ScreenSnapshot> = _screen

    private val _alive = MutableStateFlow(false)
    val alive: StateFlow<Boolean> = _alive

    private var pty: PtySession? = null
    private var readJob: Job? = null

    /** El shell del rootfs si esta instalado; si no, el del sistema. */
    private val shellPath: File
        get() = File(env.prefix, "bin/bash").takeIf { it.exists() } ?: File(SYSTEM_SH)

    fun start(rows: Int = 24, cols: Int = 80) {
        if (pty != null) return
        buffer.resize(rows, cols)

        val shell = shellPath
        val argv = if (shell.absolutePath == SYSTEM_SH) {
            listOf(SYSTEM_SH)
        } else {
            com.termdroid.exec.Executor(env, backend).buildArgv(shell)
        }

        val homeDir = File(context.filesDir, "home").apply { mkdirs() }
        val session = PtySession.start(
            argv = argv,
            env = mapOf(
                "PATH" to "${env.prefix}/bin:${env.nativeLibDir.absolutePath}:/system/bin:/system/xbin",
                "HOME" to homeDir.absolutePath,
                "TERM" to "xterm-256color",
                "TMPDIR" to context.cacheDir.absolutePath,
                "PREFIX" to env.prefix.absolutePath,
                "LD_LIBRARY_PATH" to "${env.prefix}/lib:${env.nativeLibDir.absolutePath}",
                "NODE_PATH" to "${env.prefix}/lib/node_modules",
            ),
            cwd = homeDir,
            rows = rows,
            cols = cols,
        )
        pty = session
        _alive.value = true

        readJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(8192)
            while (isActive) {
                val n = session.read(buf)
                if (n <= 0) break
                _screen.value = synchronized(lock) {
                    parser.feed(buf, n)
                    snapshot()
                }
            }
            _alive.value = false
        }
        _screen.value = synchronized(lock) { snapshot() }
    }

    suspend fun send(text: String) = withContext(Dispatchers.IO) {
        pty?.write(text)
        Unit
    }

    fun resize(rows: Int, cols: Int) {
        _screen.value = synchronized(lock) {
            buffer.resize(rows, cols)
            snapshot()
        }
        // El ioctl va fuera del lock: no toca el buffer y no conviene sostenerlo
        pty?.resize(rows, cols)
    }

    fun stop() {
        readJob?.cancel()
        pty?.close()
        pty = null
        _alive.value = false
    }

    private fun snapshot(): ScreenSnapshot = ScreenSnapshot(
        rows = buffer.rows,
        cols = buffer.cols,
        cells = Array(buffer.rows) { r -> Array(buffer.cols) { c -> buffer.cellAt(r, c) } },
        cursorRow = buffer.cursorRow,
        cursorCol = buffer.cursorCol,
        title = parser.title,
        scrollback = buffer.scrollbackLines.takeLast(SCROLLBACK_VISIBLE),
    )

    private companion object {
        const val SYSTEM_SH = "/system/bin/sh"
        const val SCROLLBACK_VISIBLE = 500
    }
}

/** Copia inmutable de la pantalla. */
class ScreenSnapshot(
    val rows: Int,
    val cols: Int,
    val cells: Array<Array<Cell>>,
    val cursorRow: Int,
    val cursorCol: Int,
    val title: String?,
    val scrollback: List<Array<Cell>> = emptyList(),
) {
    val totalRows: Int get() = scrollback.size + rows

    /** Fila [i] contando el scrollback desde arriba. */
    fun rowAt(i: Int): Array<Cell> =
        if (i < scrollback.size) scrollback[i] else cells[i - scrollback.size]

    fun text(): String = (0 until rows)
        .joinToString("\n") { r -> String(CharArray(cols) { cells[r][it].char }).trimEnd() }
        .trimEnd('\n')

    /** Todo lo que se vio, scrollback incluido. */
    fun fullText(): String = (0 until totalRows)
        .joinToString("\n") { i -> rowAt(i).let { row -> String(CharArray(row.size) { row[it].char }) }.trimEnd() }
        .trimEnd('\n')

    companion object {
        val EMPTY = ScreenSnapshot(0, 0, emptyArray(), 0, 0, null)
    }
}

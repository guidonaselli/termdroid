package com.termdroid.terminal

/**
 * La pantalla del terminal: una grilla de celdas, un cursor y un scrollback.
 *
 * Es deliberadamente tonto. No interpreta secuencias de escape —de eso se
 * ocupa [VtParser]— para poder testearse solo, sin parser de por medio.
 */
class TerminalBuffer(
    rows: Int = 24,
    cols: Int = 80,
    private val scrollbackLimit: Int = 2000,
) {
    var rows: Int = rows
        private set
    var cols: Int = cols
        private set

    private var grid: Array<Array<Cell>> = blank(rows, cols)
    private val scrollback = ArrayDeque<Array<Cell>>()

    var cursorRow: Int = 0
        private set
    var cursorCol: Int = 0
        private set

    var style: CellStyle = CellStyle.DEFAULT

    /** Lineas historicas que salieron de la pantalla, de la mas vieja a la mas nueva. */
    val scrollbackLines: List<Array<Cell>> get() = scrollback.toList()

    fun cellAt(row: Int, col: Int): Cell =
        if (row in 0 until rows && col in 0 until cols) grid[row][col] else Cell.EMPTY

    fun lineText(row: Int): String =
        if (row in 0 until rows) String(CharArray(cols) { grid[row][it].char }).trimEnd() else ""

    fun screenText(): String = (0 until rows).joinToString("\n") { lineText(it) }.trimEnd('\n')

    // --- escritura -------------------------------------------------------

    /**
     * Escribe un caracter en el cursor y avanza.
     *
     * El wrap al llegar al borde derecho es lo que espera cualquier shell; sin
     * el, las lineas largas se pisan a si mismas.
     */
    fun put(c: Char) {
        if (cursorCol >= cols) {
            cursorCol = 0
            lineFeed()
        }
        grid[cursorRow][cursorCol] = Cell(c, style)
        cursorCol++
    }

    fun carriageReturn() {
        cursorCol = 0
    }

    fun lineFeed() {
        if (cursorRow >= rows - 1) scrollUp() else cursorRow++
    }

    fun backspace() {
        if (cursorCol > 0) cursorCol--
    }

    /** Tabulacion cada 8 columnas, como espera todo el mundo. */
    fun tab() {
        val next = ((cursorCol / TAB_WIDTH) + 1) * TAB_WIDTH
        cursorCol = next.coerceAtMost(cols - 1)
    }

    fun scrollUp() {
        val first = grid[0]
        if (scrollbackLimit > 0) {
            scrollback.addLast(first)
            while (scrollback.size > scrollbackLimit) scrollback.removeFirst()
        }
        for (r in 0 until rows - 1) grid[r] = grid[r + 1]
        grid[rows - 1] = Array(cols) { Cell.EMPTY }
    }

    // --- cursor ----------------------------------------------------------

    /** Mueve el cursor a una posicion absoluta, recortando a la pantalla. */
    fun moveTo(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, cols - 1)
    }

    fun moveBy(dRow: Int, dCol: Int) = moveTo(cursorRow + dRow, cursorCol + dCol)

    // --- borrado ---------------------------------------------------------

    enum class EraseMode { TO_END, TO_START, ALL }

    fun eraseInLine(mode: EraseMode) {
        val range = when (mode) {
            EraseMode.TO_END -> cursorCol until cols
            EraseMode.TO_START -> 0..cursorCol
            EraseMode.ALL -> 0 until cols
        }
        for (c in range) grid[cursorRow][c] = Cell(' ', style)
    }

    fun eraseInDisplay(mode: EraseMode) {
        when (mode) {
            EraseMode.TO_END -> {
                eraseInLine(EraseMode.TO_END)
                for (r in cursorRow + 1 until rows) clearRow(r)
            }
            EraseMode.TO_START -> {
                for (r in 0 until cursorRow) clearRow(r)
                eraseInLine(EraseMode.TO_START)
            }
            EraseMode.ALL -> for (r in 0 until rows) clearRow(r)
        }
    }

    private fun clearRow(r: Int) {
        for (c in 0 until cols) grid[r][c] = Cell(' ', style)
    }

    // --- tamano ----------------------------------------------------------

    /**
     * Cambia el tamano preservando el contenido que entra.
     *
     * Se conserva la parte de arriba: es donde esta lo que el usuario ya leyo.
     */
    fun resize(newRows: Int, newCols: Int) {
        if (newRows == rows && newCols == cols) return
        if (newRows <= 0 || newCols <= 0) return
        val next = blank(newRows, newCols)
        for (r in 0 until minOf(rows, newRows)) {
            for (c in 0 until minOf(cols, newCols)) next[r][c] = grid[r][c]
        }
        grid = next
        rows = newRows
        cols = newCols
        cursorRow = cursorRow.coerceIn(0, rows - 1)
        cursorCol = cursorCol.coerceIn(0, cols - 1)
    }

    private companion object {
        const val TAB_WIDTH = 8

        fun blank(rows: Int, cols: Int): Array<Array<Cell>> =
            Array(rows) { Array(cols) { Cell.EMPTY } }
    }
}

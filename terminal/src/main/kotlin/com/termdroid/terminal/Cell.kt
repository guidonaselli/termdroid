package com.termdroid.terminal

/** Color de una celda. */
sealed interface TermColor {
    data object Default : TermColor

    /** Indice 0..255 de la paleta xterm. */
    @JvmInline
    value class Indexed(val index: Int) : TermColor

    data class Rgb(val r: Int, val g: Int, val b: Int) : TermColor
}

data class CellStyle(
    val fg: TermColor = TermColor.Default,
    val bg: TermColor = TermColor.Default,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val inverse: Boolean = false,
) {
    companion object {
        val DEFAULT = CellStyle()
    }
}

data class Cell(val char: Char = ' ', val style: CellStyle = CellStyle.DEFAULT) {
    companion object {
        val EMPTY = Cell()
    }
}

package com.termdroid.terminal

/**
 * Color de una celda.
 *
 * Se guarda el indice de la paleta y no un ARGB: el tema decide como se ve un
 * "rojo" en claro y en oscuro, y el buffer no tiene por que saberlo.
 */
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

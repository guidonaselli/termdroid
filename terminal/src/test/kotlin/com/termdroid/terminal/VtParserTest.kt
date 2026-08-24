package com.termdroid.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ESC = "\u001B"
private const val CSI = "$ESC["

class VtParserTest {

    private fun terminal(rows: Int = 5, cols: Int = 20): Pair<TerminalBuffer, VtParser> {
        val b = TerminalBuffer(rows, cols)
        return b to VtParser(b)
    }

    @Test
    fun escribeTextoPlano() {
        val (b, p) = terminal()
        p.feed("hola")
        assertEquals("hola", b.lineText(0))
        assertEquals(4, b.cursorCol)
    }

    @Test
    fun retornoDeCarroYSaltoDeLinea() {
        val (b, p) = terminal()
        p.feed("uno\r\ndos")
        assertEquals("uno", b.lineText(0))
        assertEquals("dos", b.lineText(1))
    }

    /** Sin wrap, una linea larga se pisaria a si misma. */
    @Test
    fun hacenWrapLasLineasLargas() {
        val (b, p) = terminal(rows = 3, cols = 5)
        p.feed("abcdefgh")
        assertEquals("abcde", b.lineText(0))
        assertEquals("fgh", b.lineText(1))
    }

    @Test
    fun backspaceYTab() {
        val (b, p) = terminal()
        p.feed("abc\b\bZ")
        assertEquals("aZc", b.lineText(0))

        val (b2, p2) = terminal()
        p2.feed("a\tb")
        assertEquals(9, b2.cursorCol)
        assertEquals('b', b2.cellAt(0, 8).char)
    }

    @Test
    fun cursorAbsolutoEsUnoBased() {
        val (b, p) = terminal()
        p.feed("${CSI}3;5Hx")
        assertEquals(2, b.cursorRow)
        assertEquals('x', b.cellAt(2, 4).char)
    }

    @Test
    fun cursorRelativo() {
        val (b, p) = terminal()
        p.feed("${CSI}3;3H")
        p.feed("${CSI}2A")
        assertEquals(0, b.cursorRow)
        p.feed("${CSI}4C")
        assertEquals(6, b.cursorCol)
    }

    /** Sin parametro, CUU/CUD mueven 1: un 0 implicito significa 1. */
    @Test
    fun parametroAusenteValeUno() {
        val (b, p) = terminal()
        p.feed("${CSI}3;3H${CSI}B")
        assertEquals(3, b.cursorRow)
    }

    @Test
    fun borrarHastaElFinDeLinea() {
        val (b, p) = terminal()
        p.feed("abcdef")
        p.feed("${CSI}3G")
        p.feed("${CSI}K")
        assertEquals("ab", b.lineText(0))
    }

    @Test
    fun borrarPantallaCompleta() {
        val (b, p) = terminal()
        p.feed("uno\r\ndos")
        p.feed("${CSI}2J")
        assertEquals("", b.screenText().trim())
    }

    @Test
    fun sgrAplicaColorYNegrita() {
        val (b, p) = terminal()
        p.feed("${CSI}1;31mrojo")
        val cell = b.cellAt(0, 0)
        assertTrue(cell.style.bold)
        assertEquals(TermColor.Indexed(1), cell.style.fg)

        p.feed("${CSI}0mnormal")
        assertFalse(b.cellAt(0, 4).style.bold)
    }

    @Test
    fun sgrColorDe256YRgb() {
        val (b, p) = terminal()
        p.feed("${CSI}38;5;196mX")
        assertEquals(TermColor.Indexed(196), b.cellAt(0, 0).style.fg)

        p.feed("${CSI}48;2;10;20;30mY")
        assertEquals(TermColor.Rgb(10, 20, 30), b.cellAt(0, 1).style.bg)
    }

    /** El caso que rompe a un parser ingenuo: las secuencias llegan partidas entre lecturas del PTY. */
    @Test
    fun secuenciaPartidaEntreLecturas() {
        val (b, p) = terminal()
        p.feed("${ESC}")
        p.feed("[")
        p.feed("2")
        p.feed(";")
        p.feed("4")
        p.feed("H")
        p.feed("ok")
        assertEquals(1, b.cursorRow)
        assertEquals('o', b.cellAt(1, 3).char)
    }

    /** Una secuencia desconocida no puede tirar el terminal ni ensuciar la pantalla. */
    @Test
    fun secuenciaDesconocidaSeIgnora() {
        val (b, p) = terminal()
        p.feed("a${CSI}99Zb")
        assertEquals("ab", b.lineText(0))
    }

    @Test
    fun modosPrivadosNoRompen() {
        val (b, p) = terminal()
        p.feed("${CSI}?25la${CSI}?25hb")
        assertEquals("ab", b.lineText(0))
    }

    @Test
    fun oscFijaElTitulo() {
        val (_, p) = terminal()
        p.feed("${ESC}]0;mi titulo\u0007")
        assertEquals("mi titulo", p.title)
    }

    @Test
    fun oscTerminadoConStringTerminator() {
        val (b, p) = terminal()
        p.feed("${ESC}]2;otro${ESC}\\texto")
        assertEquals("otro", p.title)
        assertEquals("texto", b.lineText(0))
    }

    @Test
    fun scrollGuardaLineasEnElScrollback() {
        val (b, p) = terminal(rows = 2, cols = 10)
        p.feed("a\r\nb\r\nc")
        assertEquals("b", b.lineText(0))
        assertEquals("c", b.lineText(1))
        assertEquals(1, b.scrollbackLines.size)
        assertEquals('a', b.scrollbackLines[0][0].char)
    }

    @Test
    fun resizeRecortaElCursorAlNuevoTamano() {
        val (b, p) = terminal(rows = 5, cols = 20)
        p.feed("${CSI}5;15Hhola")
        b.resize(3, 10)
        assertEquals(3, b.rows)
        assertEquals(10, b.cols)
        assertTrue(b.cursorRow < 3)
        assertTrue(b.cursorCol < 10)
    }

    /** Achicar la pantalla no puede borrar lo que ya estaba. */
    @Test
    fun resizeConservaElTextoQueEntra() {
        val (b, p) = terminal(rows = 10, cols = 40)
        p.feed("primera linea\r\nsegunda linea")

        b.resize(6, 30)

        assertEquals("primera linea", b.lineText(0))
        assertEquals("segunda linea", b.lineText(1))
    }

    /** Agrandar tampoco: es el mismo evento al cerrarse el teclado. */
    @Test
    fun resizeHaciaArribaConservaElTexto() {
        val (b, p) = terminal(rows = 6, cols = 30)
        p.feed("hola mundo")

        b.resize(20, 60)

        assertEquals("hola mundo", b.lineText(0))
        assertEquals(20, b.rows)
        assertEquals(60, b.cols)
    }

    /** Un prompt de shell real: color, reset y texto. */
    @Test
    fun promptDeShellRealizable() {
        val (b, p) = terminal()
        p.feed("${CSI}1;32muser${CSI}0m:${CSI}1;34m~${CSI}0m$ ls")
        assertEquals("user:~$ ls", b.lineText(0))
        assertEquals(TermColor.Indexed(2), b.cellAt(0, 0).style.fg)
        assertEquals(TermColor.Default, b.cellAt(0, 4).style.fg)
    }
}

package com.termdroid.terminal

/**
 * Interpreta la salida de un programa de terminal y la aplica sobre un [TerminalBuffer].
 *
 * Es una maquina de estados sobre bytes, no un parser de lineas: las secuencias
 * llegan partidas entre lecturas del PTY y hay que poder retomarlas.
 *
 * Cubre el subconjunto que usa un shell de verdad: movimiento de cursor, borrado
 * y SGR. Lo que no se entiende se descarta en silencio, que es lo que hace un
 * terminal real y lo que evita que basura en pantalla se vuelva un crash.
 */
class VtParser(private val buffer: TerminalBuffer) {

    private enum class State { GROUND, ESC, CSI, OSC, OSC_ESC }

    private var state = State.GROUND
    private val params = StringBuilder()
    private var private = false

    /** Titulo pedido por el programa via OSC 0/2. */
    var title: String? = null
        private set

    private val osc = StringBuilder()

    fun feed(bytes: ByteArray, length: Int = bytes.size) {
        for (i in 0 until length) feed(bytes[i].toInt().and(0xFF).toChar())
    }

    fun feed(text: String) = text.forEach { feed(it) }

    fun feed(c: Char) {
        when (state) {
            State.GROUND -> ground(c)
            State.ESC -> esc(c)
            State.CSI -> csi(c)
            State.OSC -> oscChar(c)
            State.OSC_ESC -> {
                // ESC \ termina un OSC (String Terminator)
                finishOsc()
                if (c != '\\') feed(c)
            }
        }
    }

    private fun ground(c: Char) {
        when (c) {
            '\u001B' -> state = State.ESC
            '\r' -> buffer.carriageReturn()
            '\n', '\u000B', '\u000C' -> buffer.lineFeed()
            '\b' -> buffer.backspace()
            '\t' -> buffer.tab()
            '\u0007' -> Unit // bell: sin sonido, sin efecto visual
            else -> if (c >= ' ') buffer.put(c)
        }
    }

    private fun esc(c: Char) {
        when (c) {
            '[' -> {
                params.clear()
                private = false
                state = State.CSI
            }
            ']' -> {
                osc.clear()
                state = State.OSC
            }
            // Index / Next Line: bajan una linea
            'D' -> { buffer.lineFeed(); state = State.GROUND }
            'E' -> { buffer.carriageReturn(); buffer.lineFeed(); state = State.GROUND }
            'c' -> { buffer.eraseInDisplay(TerminalBuffer.EraseMode.ALL); buffer.moveTo(0, 0); state = State.GROUND }
            else -> state = State.GROUND
        }
    }

    private fun csi(c: Char) {
        when {
            c == '?' -> private = true
            c in '0'..'9' || c == ';' -> params.append(c)
            c in ' '..'/' -> Unit // bytes intermedios: se ignoran
            else -> {
                dispatch(c)
                state = State.GROUND
            }
        }
    }

    private fun dispatch(cmd: Char) {
        // Los modos privados (DECSET/DECRST, p.ej. cursor visible o pantalla
        // alternativa) no cambian el contenido: se aceptan y se ignoran.
        if (private) return

        val args = params.toString().split(';').map { it.toIntOrNull() ?: 0 }
        fun arg(i: Int, default: Int = 1): Int = args.getOrNull(i)?.takeIf { it != 0 } ?: default
        fun arg0(i: Int): Int = args.getOrNull(i) ?: 0

        when (cmd) {
            'A' -> buffer.moveBy(-arg(0), 0)
            'B' -> buffer.moveBy(arg(0), 0)
            'C' -> buffer.moveBy(0, arg(0))
            'D' -> buffer.moveBy(0, -arg(0))
            'E' -> buffer.moveTo(buffer.cursorRow + arg(0), 0)
            'F' -> buffer.moveTo(buffer.cursorRow - arg(0), 0)
            'G' -> buffer.moveTo(buffer.cursorRow, arg(0) - 1)
            // CUP y HVP usan coordenadas 1-based
            'H', 'f' -> buffer.moveTo(arg(0) - 1, arg(1) - 1)
            'J' -> buffer.eraseInDisplay(eraseMode(arg0(0)))
            'K' -> buffer.eraseInLine(eraseMode(arg0(0)))
            'd' -> buffer.moveTo(arg(0) - 1, buffer.cursorCol)
            'm' -> applySgr(args)
            else -> Unit
        }
    }

    private fun eraseMode(n: Int) = when (n) {
        1 -> TerminalBuffer.EraseMode.TO_START
        2, 3 -> TerminalBuffer.EraseMode.ALL
        else -> TerminalBuffer.EraseMode.TO_END
    }

    /** Select Graphic Rendition: color y atributos. */
    private fun applySgr(args: List<Int>) {
        if (params.isEmpty()) {
            buffer.style = CellStyle.DEFAULT
            return
        }
        var i = 0
        var s = buffer.style
        while (i < args.size) {
            when (val n = args[i]) {
                0 -> s = CellStyle.DEFAULT
                1 -> s = s.copy(bold = true)
                3 -> s = s.copy(italic = true)
                4 -> s = s.copy(underline = true)
                7 -> s = s.copy(inverse = true)
                22 -> s = s.copy(bold = false)
                23 -> s = s.copy(italic = false)
                24 -> s = s.copy(underline = false)
                27 -> s = s.copy(inverse = false)
                in 30..37 -> s = s.copy(fg = TermColor.Indexed(n - 30))
                39 -> s = s.copy(fg = TermColor.Default)
                in 40..47 -> s = s.copy(bg = TermColor.Indexed(n - 40))
                49 -> s = s.copy(bg = TermColor.Default)
                in 90..97 -> s = s.copy(fg = TermColor.Indexed(n - 90 + 8))
                in 100..107 -> s = s.copy(bg = TermColor.Indexed(n - 100 + 8))
                38, 48 -> {
                    val extended = readExtendedColor(args, i)
                    if (extended != null) {
                        s = if (n == 38) s.copy(fg = extended.first) else s.copy(bg = extended.first)
                        i += extended.second
                    }
                }
                else -> Unit
            }
            i++
        }
        buffer.style = s
    }

    /** Devuelve el color y cuantos argumentos extra consumio, o null si viene truncado. */
    private fun readExtendedColor(args: List<Int>, at: Int): Pair<TermColor, Int>? = when {
        args.getOrNull(at + 1) == 5 && args.size > at + 2 ->
            TermColor.Indexed(args[at + 2]) to 2
        args.getOrNull(at + 1) == 2 && args.size > at + 4 ->
            TermColor.Rgb(args[at + 2], args[at + 3], args[at + 4]) to 4
        else -> null
    }

    private fun oscChar(c: Char) {
        when (c) {
            '\u0007' -> finishOsc()
            '\u001B' -> state = State.OSC_ESC
            else -> osc.append(c)
        }
    }

    private fun finishOsc() {
        val text = osc.toString()
        // OSC 0 y 2 fijan el titulo de la ventana.
        val code = text.substringBefore(';')
        if (code == "0" || code == "2") title = text.substringAfter(';', "")
        osc.clear()
        state = State.GROUND
    }
}

package com.termdroid.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.AnnotatedString

/**
 * Paleta del terminal.
 *
 * Los 16 colores base se resuelven segun el tema: el buffer guarda "rojo", no un
 * ARGB, asi que un mismo output se ve legible en claro y en oscuro.
 * Ver 10_TECH/UX_PRINCIPLES.md.
 */
data class TerminalPalette(
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val ansi: List<Color>,
) {
    fun resolve(color: TermColor, default: Color): Color = when (color) {
        is TermColor.Default -> default
        is TermColor.Rgb -> Color(color.r, color.g, color.b)
        is TermColor.Indexed -> indexed(color.index, default)
    }

    private fun indexed(i: Int, default: Color): Color = when {
        i in ansi.indices -> ansi[i]
        // Cubo de 6x6x6 de xterm.
        i in 16..231 -> {
            val n = i - 16
            val steps = intArrayOf(0, 95, 135, 175, 215, 255)
            Color(steps[n / 36], steps[(n / 6) % 6], steps[n % 6])
        }
        // Rampa de grises.
        i in 232..255 -> (8 + (i - 232) * 10).let { Color(it, it, it) }
        else -> default
    }

    companion object {
        private val ANSI_DARK = listOf(
            Color(0xFF1E1E1E), Color(0xFFF44747), Color(0xFF6A9955), Color(0xFFD7BA7D),
            Color(0xFF569CD6), Color(0xFFC586C0), Color(0xFF4EC9B0), Color(0xFFD4D4D4),
            Color(0xFF808080), Color(0xFFFF7B72), Color(0xFF7EE787), Color(0xFFE3B341),
            Color(0xFF79C0FF), Color(0xFFD2A8FF), Color(0xFF56D4DD), Color(0xFFFFFFFF),
        )

        // En claro los brillantes se oscurecen: sobre fondo blanco son ilegibles.
        private val ANSI_LIGHT = listOf(
            Color(0xFF2E2E2E), Color(0xFFCD3131), Color(0xFF107C10), Color(0xFF8A6D00),
            Color(0xFF0451A5), Color(0xFFA31DB1), Color(0xFF0598A0), Color(0xFF3B3B3B),
            Color(0xFF6E6E6E), Color(0xFFA31515), Color(0xFF0B6A0B), Color(0xFF7A5C00),
            Color(0xFF0043A8), Color(0xFF871094), Color(0xFF007A7A), Color(0xFF111111),
        )

        val Dark = TerminalPalette(
            background = Color(0xFF12131A),
            foreground = Color(0xFFD4D4D4),
            cursor = Color(0xFF7EE787),
            ansi = ANSI_DARK,
        )

        val Light = TerminalPalette(
            background = Color(0xFFFBFBFD),
            foreground = Color(0xFF2E2E2E),
            cursor = Color(0xFF0B6A0B),
            ansi = ANSI_LIGHT,
        )
    }
}

/**
 * Dibuja la pantalla del terminal.
 *
 * Se dibuja en Canvas y no con Text por celda: una grilla de 24x80 son 1920
 * composables por frame, que es insostenible en un telefono.
 */
@Composable
fun TerminalView(
    screen: ScreenSnapshot,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    palette: TerminalPalette = if (isSystemInDarkTheme()) TerminalPalette.Dark else TerminalPalette.Light,
    onGridSize: (rows: Int, cols: Int) -> Unit = { _, _ -> },
) {
    val measurer = rememberTextMeasurer()
    val baseStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = fontSize)

    // Ancho de celda medido una vez: en monoespaciada todas miden igual.
    val metrics = measurer.measure(AnnotatedString("M"), baseStyle)
    val cellW = metrics.size.width.toFloat()
    val cellH = metrics.size.height.toFloat()

    Box(
        modifier = modifier
            .background(palette.background)
            .padding(4.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (cellW <= 0f || cellH <= 0f) return@Canvas

            // La grilla sale de la celda MEDIDA, no de una estimacion: si el
            // buffer y el renderer no coinciden en cuantas columnas entran, el
            // texto se dibuja fuera de lugar.
            onGridSize(
                (size.height / cellH).toInt().coerceIn(4, 200),
                (size.width / cellW).toInt().coerceIn(20, 400),
            )

            if (screen.rows == 0) return@Canvas
            drawScreen(screen, palette, baseStyle, cellW, cellH, measurer)
        }
    }
}

private fun DrawScope.drawScreen(
    screen: ScreenSnapshot,
    palette: TerminalPalette,
    baseStyle: TextStyle,
    cellW: Float,
    cellH: Float,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    for (r in 0 until screen.rows) {
        var c = 0
        while (c < screen.cols) {
            val cell = screen.cells[r][c]
            val style = cell.style

            var fg = palette.resolve(style.fg, palette.foreground)
            var bg = palette.resolve(style.bg, palette.background)
            if (style.inverse) {
                val t = fg; fg = bg; bg = t
            }

            // Agrupa celdas contiguas con el mismo estilo: dibujar caracter por
            // caracter multiplica las llamadas de dibujo sin necesidad.
            val start = c
            val sb = StringBuilder()
            while (c < screen.cols && screen.cells[r][c].style == style) {
                sb.append(screen.cells[r][c].char)
                c++
            }

            val x = start * cellW
            val y = r * cellH

            if (bg != palette.background) {
                drawRect(color = bg, topLeft = Offset(x, y), size = Size(sb.length * cellW, cellH))
            }

            val text = sb.toString()
            if (text.isNotBlank()) {
                drawText(
                    textMeasurer = measurer,
                    text = text,
                    topLeft = Offset(x, y),
                    style = baseStyle.copy(
                        color = fg,
                        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                    ),
                    // Sin esto drawText envuelve el texto por su cuenta cuando la
                    // corrida excede el ancho del canvas, y las lineas que agrega
                    // caen encima de la fila siguiente. El wrap ya lo hizo el
                    // buffer: aca cada corrida es una linea y solo una.
                    softWrap = false,
                    maxLines = 1,
                )
            }
        }
    }

    // Cursor como bloque semitransparente: se ve sobre cualquier fondo.
    if (screen.cursorRow in 0 until screen.rows) {
        drawRect(
            color = palette.cursor.copy(alpha = 0.6f),
            topLeft = Offset(screen.cursorCol * cellW, screen.cursorRow * cellH),
            size = Size(cellW, cellH),
        )
    }
}

package com.termdroid.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
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

/** Paleta del terminal. */
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

/** Dibuja la pantalla del terminal. */
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

    val metrics = measurer.measure(AnnotatedString("M"), baseStyle)
    val cellW = metrics.size.width.toFloat()
    val cellH = metrics.size.height.toFloat()

    val scroll = rememberScrollState()
    val density = LocalDensity.current

    // Se sigue el final salvo que el usuario haya subido a leer.
    val pegadoAlFondo by remember {
        derivedStateOf { scroll.value >= scroll.maxValue - 2 }
    }
    var seguirElFondo by remember { mutableStateOf(true) }

    LaunchedEffect(pegadoAlFondo) { seguirElFondo = pegadoAlFondo }

    LaunchedEffect(screen.totalRows, scroll.maxValue) {
        if (seguirElFondo) scroll.scrollTo(scroll.maxValue)
    }

    BoxWithConstraints(
        modifier = modifier
            .background(palette.background)
            .padding(4.dp),
    ) {
        if (cellW > 0f && cellH > 0f) {
            with(density) {
                onGridSize(
                    (maxHeight.toPx() / cellH).toInt().coerceIn(4, 200),
                    (maxWidth.toPx() / cellW).toInt().coerceIn(20, 400),
                )
            }
        }

        val alto = with(density) { (screen.totalRows.coerceAtLeast(1) * cellH).toDp() }

        Box(Modifier.verticalScroll(scroll)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(alto)) {
                if (cellW <= 0f || screen.totalRows == 0) return@Canvas
                drawScreen(screen, palette, baseStyle, cellW, cellH, measurer)
            }
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
    for (r in 0 until screen.totalRows) {
        val fila = screen.rowAt(r)
        var c = 0
        while (c < fila.size) {
            val cell = fila[c]
            val style = cell.style

            var fg = palette.resolve(style.fg, palette.foreground)
            var bg = palette.resolve(style.bg, palette.background)
            if (style.inverse) {
                val t = fg; fg = bg; bg = t
            }

            // Agrupa celdas contiguas con el mismo estilo: dibujar caracter por
            val start = c
            val sb = StringBuilder()
            while (c < fila.size && fila[c].style == style) {
                sb.append(fila[c].char)
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
            topLeft = Offset(
                screen.cursorCol * cellW,
                (screen.scrollback.size + screen.cursorRow) * cellH,
            ),
            size = Size(cellW, cellH),
        )
    }
}

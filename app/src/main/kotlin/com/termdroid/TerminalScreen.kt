package com.termdroid

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termdroid.terminal.ShellSession
import com.termdroid.terminal.TerminalView
import kotlinx.coroutines.launch

/** Pantalla de terminal. */
@Composable
fun TerminalScreen(vm: TerminalViewModel, modifier: Modifier = Modifier) {
    val sesiones by vm.sessions.collectAsState()
    val activa by vm.activeIndex.collectAsState()
    val session = sesiones.getOrNull(activa)

    if (session == null) {
        Text(androidx.compose.ui.res.stringResource(R.string.terminal_no_disponible), modifier = modifier.padding(16.dp))
        return
    }

    Column(modifier.fillMaxSize()) {
        SesionesBar(
            cantidad = sesiones.size,
            activa = activa,
            onSeleccionar = vm::seleccionar,
            onNueva = vm::nueva,
            onCerrar = vm::cerrar,
        )
        TerminalPane(session, Modifier.weight(1f))
    }
}

@Composable
private fun SesionesBar(
    cantidad: Int,
    activa: Int,
    onSeleccionar: (Int) -> Unit,
    onNueva: () -> Unit,
    onCerrar: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(cantidad) { i ->
            FilterChip(
                selected = i == activa,
                onClick = { onSeleccionar(i) },
                label = { Text("sh ${i + 1}", fontSize = 12.sp) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        TextButton(onClick = onNueva) { Text("+", fontSize = 16.sp) }
        if (cantidad > 1) {
            TextButton(onClick = { onCerrar(activa) }) { Text("cerrar", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun TerminalPane(session: ShellSession, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val screen by session.screen.collectAsState()
    val alive by session.alive.collectAsState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        // La grilla la reporta la vista desde la celda medida.
        var grid by remember { mutableStateOf(0 to 0) }

        LaunchedEffect(grid, alive) {
            val (rows, cols) = grid
            if (alive && rows > 0 && cols > 0) session.resize(rows, cols)
        }

        TerminalView(
            screen = screen,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            fontSize = FONT_SIZE,
            onGridSize = { rows, cols ->
                if (grid != rows to cols) grid = rows to cols
            },
        )

        if (!alive) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    "La sesion termino.",
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        QuickCommandsRow(
            onCommand = { cmd -> scope.launch { session.send("$cmd\n") } },
        )

        KeyRow(
            onKey = { seq -> scope.launch { session.send(seq) } },
            onCopiar = {
                val cb = context.getSystemService(android.content.ClipboardManager::class.java)
                cb?.setPrimaryClip(
                    android.content.ClipData.newPlainText("terminal", screen.fullText()),
                )
            },
            onPegar = {
                val cb = context.getSystemService(android.content.ClipboardManager::class.java)
                val text = cb?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                if (text.isNotEmpty()) {
                    scope.launch { session.send(text) }
                }
            },
        )

        TextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            placeholder = { Text("comando", fontFamily = FontFamily.Monospace) },
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(
                onSend = {
                    val cmd = input
                    input = ""
                    scope.launch { session.send("$cmd\n") }
                },
            ),
            colors = TextFieldDefaults.colors(),
        )
    }
}

/** Barra de comandos rápidos para terminal. */
@Composable
private fun QuickCommandsRow(onCommand: (String) -> Unit) {
    val quicks = listOf(
        "claude",
        "codex",
        "agy",
        "claude login",
        "codex login",
        "termdroid info",
        "termdroid battery",
        "ls -la",
        "pwd",
        "clear",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        quicks.forEach { cmd ->
            FilterChip(
                selected = false,
                onClick = { onCommand(cmd) },
                label = { Text(cmd, fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
            )
        }
    }
}

/** Barra de teclas contextual para terminal. */
@Composable
private fun KeyRow(
    onKey: (String) -> Unit,
    onCopiar: () -> Unit,
    onPegar: () -> Unit,
) {
    val keys = listOf(
        "Esc" to "\u001B",
        "Tab" to "\t",
        "^C" to "\u0003",
        "^D" to "\u0004",
        "^Z" to "\u001A",
        "^L" to "\u000C",
        "↑" to "\u001B[A",
        "↓" to "\u001B[B",
        "←" to "\u001B[D",
        "→" to "\u001B[C",
        "/" to "/",
        "|" to "|",
        "~" to "~",
        "-" to "-",
        "_" to "_",
        "$" to "$",
        "&" to "&",
        "\"" to "\"",
        "'" to "'",
        ";" to ";",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextButton(onClick = onPegar) {
            Text("📋 Pegar", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
        TextButton(onClick = onCopiar) {
            Text("📄 Copiar", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        keys.forEach { (label, seq) ->
            TextButton(onClick = { onKey(seq) }) {
                Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}

private val FONT_SIZE = 12.sp


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
fun TerminalScreen(session: ShellSession, modifier: Modifier = Modifier) {
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

        KeyRow(
            onKey = { seq -> scope.launch { session.send(seq) } },
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

/** Barra de teclas contextual. */
@Composable
private fun KeyRow(onKey: (String) -> Unit) {
    val keys = listOf(
        "Esc" to "\u001B",
        "Tab" to "\t",
        "^C" to "\u0003",
        "^D" to "\u0004",
        "^L" to "\u000C",
        "/" to "/",
        "|" to "|",
        "~" to "~",
        "-" to "-",
        "↑" to "\u001B[A",
        "↓" to "\u001B[B",
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
        keys.forEach { (label, seq) ->
            TextButton(onClick = { onKey(seq) }) {
                Text(label, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
        }
    }
}

private val FONT_SIZE = 12.sp

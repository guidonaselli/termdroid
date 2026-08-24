package com.termdroid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termdroid.agent.AutonomyMode

/** El chat con el agente. */
@Composable
fun ChatScreen(vm: AgentViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().imePadding().navigationBarsPadding()) {

        if (state.needsApiKey) {
            ApiKeyPrompt(onSave = vm::saveApiKey)
            return@Column
        }

        AutonomyBar(
            mode = state.autonomy,
            onMode = vm::setAutonomy,
            tokensIn = state.tokensIn,
            tokensOut = state.tokensOut,
            cacheRead = state.cacheRead,
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, key = { it.id }) { item -> ChatBubble(item) }
        }

        state.accessNeeded?.let { access ->
            AccessCard(access = access, onDismiss = vm::dismissAccessPrompt)
        }

        state.pending?.let { pending ->
            ApprovalCard(pending = pending, onDecide = vm::approve)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Pedile algo") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        vm.send(input)
                        input = ""
                    },
                ),
            )
            if (state.busy) {
                OutlinedButton(onClick = vm::cancel) { Text("Parar") }
            } else {
                Button(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                ) { Text("Enviar") }
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(item.text, modifier = Modifier.padding(10.dp))
        }

        is ChatItem.Assistant -> Text(
            item.text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        is ChatItem.Thinking -> {
            var open by remember { mutableStateOf(false) }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().clickable { open = !open },
            ) {
                Column(Modifier.padding(8.dp)) {
                    Text(
                        if (open) "pensando ▾" else "pensando ▸",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    if (open) {
                        Text(item.text, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        is ChatItem.Note -> Text(
            item.text,
            color = if (item.isError) MaterialTheme.colorScheme.error else Color.Unspecified,
            style = MaterialTheme.typography.labelLarge,
        )

        is ChatItem.ToolCard -> ToolCardView(item)
    }
}

/** Un tool call es un componente, no texto plano. */
@Composable
private fun ToolCardView(card: ChatItem.ToolCard) {
    var open by remember { mutableStateOf(false) }
    val (label, color) = when (card.status) {
        ToolStatus.PENDIENTE -> "…" to MaterialTheme.colorScheme.surfaceVariant
        ToolStatus.ESPERANDO_APROBACION -> "espera aprobacion" to MaterialTheme.colorScheme.tertiaryContainer
        ToolStatus.CORRIENDO -> "corriendo" to MaterialTheme.colorScheme.surfaceVariant
        ToolStatus.LISTO -> "ok" to MaterialTheme.colorScheme.secondaryContainer
        ToolStatus.ERROR -> "error" to MaterialTheme.colorScheme.errorContainer
        ToolStatus.RECHAZADO -> "rechazado" to MaterialTheme.colorScheme.errorContainer
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth().clickable { open = !open },
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                card.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                maxLines = if (open) Int.MAX_VALUE else 2,
            )
            if (card.output.isNotBlank()) {
                Text(
                    if (open) card.output else card.output.lineSequence().take(3).joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
                val lines = card.output.count { it == '\n' } + 1
                if (!open && lines > 3) {
                    Text("… $lines lineas", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Ofrece conceder un acceso especial en el momento en que hace falta. */
@Composable
private fun AccessCard(
    access: com.termdroid.tools.android.SpecialAccess,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Falta un permiso: ${access.label}", fontWeight = FontWeight.Bold)
            Text(
                "Android lo concede desde Ajustes. Sin el, esa consulta no esta disponible; " +
                    "el resto sigue funcionando.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.startActivity(
                        access.settingsIntent().addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    onDismiss()
                }) { Text("Abrir Ajustes") }
                OutlinedButton(onClick = onDismiss) { Text("Ahora no") }
            }
        }
    }
}

/** La aprobacion muestra la accion exacta, nunca un resumen del modelo. */
@Composable
private fun ApprovalCard(pending: PendingApproval, onDecide: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("El agente quiere ejecutar ${pending.name}", fontWeight = FontWeight.Bold)
            Text(
                pending.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(true) }) { Text("Aprobar") }
                OutlinedButton(onClick = { onDecide(false) }) { Text("Rechazar") }
            }
        }
    }
}

/** El modo de autonomia esta siempre visible, y el gasto tambien. */
@Composable
private fun AutonomyBar(
    mode: AutonomyMode,
    onMode: (AutonomyMode) -> Unit,
    tokensIn: Long,
    tokensOut: Long,
    cacheRead: Long,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            AutonomyMode.entries.forEach { m ->
                FilterChip(
                    selected = m == mode,
                    onClick = { onMode(m) },
                    label = { Text(m.label(), fontSize = 12.sp) },
                )
            }
        }
        Text(
            "in $tokensIn · out $tokensOut · cache $cacheRead",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun AutonomyMode.label() = when (this) {
    AutonomyMode.ASK_ALL -> "Preguntar todo"
    AutonomyMode.AUTO_READ -> "Auto-lectura"
    AutonomyMode.AUTO_ALL -> "Auto-todo"
}

@Composable
private fun ApiKeyPrompt(onSave: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Clave de la API", style = MaterialTheme.typography.titleMedium)
        Text(
            "Se guarda cifrada con el Keystore del sistema y no sale del telefono.",
            style = MaterialTheme.typography.bodySmall,
        )
        TextField(
            value = key,
            onValueChange = { key = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("sk-ant-…") },
        )
        Button(onClick = { onSave(key) }, enabled = key.isNotBlank()) { Text("Guardar") }
    }
}

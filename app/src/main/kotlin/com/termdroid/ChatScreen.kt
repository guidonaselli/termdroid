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
import androidx.compose.material3.TextButton
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
import com.termdroid.agent.LlmProvider

/** El chat con el agente. */
@Composable
fun ChatScreen(vm: AgentViewModel, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.prefill) {
        state.prefill?.let {
            input = it
            vm.clearPrefill()
        }
    }

    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty()) listState.animateScrollToItem(state.items.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize().imePadding().navigationBarsPadding()) {

        if (state.needsApiKey || state.showSettings) {
            ProviderAuthPrompt(
                activeProvider = state.activeProvider,
                onSave = vm::saveProvider,
                onDismiss = if (state.showSettings && !state.needsApiKey) { { vm.toggleSettings(false) } } else null,
            )
            if (state.needsApiKey) return@Column
        }

        AutonomyBar(
            mode = state.autonomy,
            provider = state.activeProvider,
            onMode = vm::setAutonomy,
            onNueva = vm::nuevaSesion,
            onOpenSettings = { vm.toggleSettings(true) },
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
                placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.pedile_algo)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        vm.send(input)
                        input = ""
                    },
                ),
            )
            DictadoButton(onTexto = { input = (input + " " + it).trim() })

            if (state.busy) {
                OutlinedButton(onClick = vm::cancel) { Text(androidx.compose.ui.res.stringResource(R.string.parar)) }
            } else {
                Button(
                    onClick = {
                        vm.send(input)
                        input = ""
                    },
                ) { Text(androidx.compose.ui.res.stringResource(R.string.enviar)) }
            }
        }
    }
}

@Composable
private fun DictadoButton(onTexto: (String) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val dictado = remember { Dictado(context) }
    var escuchando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val pedirPermiso = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) {
            escuchando = true
            dictado.escuchar { r ->
                escuchando = false
                when (r) {
                    is ResultadoDictado.Texto -> onTexto(r.texto)
                    is ResultadoDictado.Error -> aviso = r.mensaje
                }
            }
        } else {
            aviso = "Sin microfono no se puede dictar."
        }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { dictado.detener() }
    }

    Column {
        OutlinedButton(
            onClick = { pedirPermiso.launch(android.Manifest.permission.RECORD_AUDIO) },
            enabled = dictado.disponible && !escuchando,
        ) {
            Text(if (escuchando) "..." else "Voz")
        }
        aviso?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun ChatBubble(item: ChatItem) {
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
internal fun ToolCardView(card: ChatItem.ToolCard) {
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
                }) { Text(androidx.compose.ui.res.stringResource(R.string.abrir_ajustes)) }
                OutlinedButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(R.string.ahora_no)) }
            }
        }
    }
}

/** La aprobacion muestra la accion exacta, nunca un resumen del modelo. */
@Composable
internal fun ApprovalCard(pending: PendingApproval, onDecide: (Boolean) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(androidx.compose.ui.res.stringResource(R.string.aprobacion_requerida) + ": ${pending.name}", fontWeight = FontWeight.Bold)
            Text(
                pending.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onDecide(true) }) { Text(androidx.compose.ui.res.stringResource(R.string.aprobar)) }
                OutlinedButton(onClick = { onDecide(false) }) { Text(androidx.compose.ui.res.stringResource(R.string.rechazar)) }
            }
        }
    }
}

/** El modo de autonomia esta siempre visible, y el gasto tambien. */
@Composable
private fun AutonomyBar(
    mode: AutonomyMode,
    provider: LlmProvider,
    onMode: (AutonomyMode) -> Unit,
    onNueva: () -> Unit,
    onOpenSettings: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            AutonomyMode.entries.forEach { m ->
                val label = when (m) {
                    AutonomyMode.ASK_ALL -> androidx.compose.ui.res.stringResource(R.string.modo_preguntar)
                    AutonomyMode.AUTO_READ -> androidx.compose.ui.res.stringResource(R.string.modo_auto_read)
                    AutonomyMode.AUTO_ALL -> androidx.compose.ui.res.stringResource(R.string.modo_auto_all)
                }
                FilterChip(
                    selected = m == mode,
                    onClick = { onMode(m) },
                    label = { Text(label, fontSize = 12.sp) },
                )
            }
            FilterChip(
                selected = false,
                onClick = onOpenSettings,
                label = { Text("⚡ ${provider.displayName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
            )
            TextButton(onClick = onNueva) { Text(androidx.compose.ui.res.stringResource(R.string.nueva_sesion), fontSize = 12.sp) }
        }
        Text(
            "in $tokensIn · out $tokensOut · cache $cacheRead",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ProviderAuthPrompt(
    activeProvider: LlmProvider,
    onSave: (LlmProvider, String, String, String) -> Unit,
    onDismiss: (() -> Unit)? = null,
) {
    var selectedProvider by remember { mutableStateOf(activeProvider) }
    var token by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.proveedor_titulo),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (onDismiss != null) {
                    TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(R.string.cancelar)) }
                }
            }

            Text(
                androidx.compose.ui.res.stringResource(R.string.proveedor_explicacion),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LlmProvider.entries.forEach { p ->
                    FilterChip(
                        selected = p == selectedProvider,
                        onClick = { selectedProvider = p },
                        label = { Text(p.displayName, fontSize = 12.sp) },
                    )
                }
            }

            val ayuda = when (selectedProvider) {
                LlmProvider.GEMINI -> androidx.compose.ui.res.stringResource(R.string.proveedor_gemini_ayuda)
                LlmProvider.CLAUDE -> androidx.compose.ui.res.stringResource(R.string.proveedor_claude_ayuda)
                LlmProvider.OPENAI -> androidx.compose.ui.res.stringResource(R.string.proveedor_openai_ayuda)
                LlmProvider.CUSTOM -> androidx.compose.ui.res.stringResource(R.string.proveedor_custom_ayuda)
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    ayuda,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }

            if (selectedProvider == LlmProvider.CUSTOM) {
                TextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.url_servidor)) },
                    placeholder = { Text("http://192.168.1.50:11434/v1") },
                    singleLine = true,
                )
                TextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.modelo_label)) },
                    placeholder = { Text("llama3.2") },
                    singleLine = true,
                )
                TextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Token / API Key (opcional)") },
                    singleLine = true,
                )
            } else {
                TextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(androidx.compose.ui.res.stringResource(R.string.token_label)) },
                    placeholder = {
                        Text(
                            when (selectedProvider) {
                                LlmProvider.GEMINI -> "Token de Google o AIzaSy..."
                                LlmProvider.CLAUDE -> "Token de sesión Claude o sk-ant-..."
                                LlmProvider.OPENAI -> "Token de sesión ChatGPT o sk-..."
                                else -> androidx.compose.ui.res.stringResource(R.string.token_placeholder)
                            },
                        )
                    },
                    singleLine = true,
                )
            }

            Button(
                onClick = { onSave(selectedProvider, token, model, baseUrl) },
                enabled = selectedProvider == LlmProvider.CUSTOM || token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(androidx.compose.ui.res.stringResource(R.string.guardar_proveedor))
            }
        }
    }
}

package com.termdroid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OfficialCliOnboarding(
    state: OfficialCliSetupState,
    onConfigure: () -> Unit,
    onRefresh: () -> Unit,
    onOpenClaude: () -> Unit,
    onOpenCodex: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Herramientas oficiales", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Termdroid prepara y verifica el entorno. Termux sólo se abre para la sesión oficial interactiva.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        when (state) {
            OfficialCliSetupState.Checking -> SetupCard(
                title = "Verificando el entorno",
                detail = "Comprobando Node.js y tus herramientas oficiales.",
                action = "Actualizar",
                onAction = onRefresh,
            )
            is OfficialCliSetupState.ActionRequired -> SetupCard(
                title = "Falta una configuración",
                detail = state.message,
                action = "Continuar",
                onAction = onConfigure,
            )
            is OfficialCliSetupState.Preparing -> SetupCard(
                title = "Preparando tus herramientas",
                detail = state.message,
                action = null,
                onAction = {},
            )
            is OfficialCliSetupState.Failed -> SetupCard(
                title = "Podemos arreglarlo",
                detail = state.message,
                action = "Reintentar",
                onAction = onConfigure,
                isError = true,
            )
            is OfficialCliSetupState.Ready -> ReadyTools(
                versions = state.versions,
                onRefresh = onRefresh,
                onOpenClaude = onOpenClaude,
                onOpenCodex = onOpenCodex,
            )
        }
    }
}

@Composable
private fun SetupCard(
    title: String,
    detail: String,
    action: String?,
    onAction: () -> Unit,
    isError: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodyLarge)
            action?.let {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(it)
                }
            }
        }
    }
}

@Composable
private fun ReadyTools(
    versions: com.termdroid.rootfs.OfficialCliVersions,
    onRefresh: () -> Unit,
    onOpenClaude: () -> Unit,
    onOpenCodex: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Todo listo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Versiones comprobadas en este dispositivo.",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            VersionRow("Node.js", versions.node)
            VersionRow("npm", versions.npm)
            VersionRow("Claude Code", versions.claude)
            VersionRow("Codex", versions.codex)
        }
    }

    Button(onClick = onOpenClaude, modifier = Modifier.fillMaxWidth()) {
        Text("Abrir Claude Code")
    }
    Button(onClick = onOpenCodex, modifier = Modifier.fillMaxWidth()) {
        Text("Abrir Codex")
    }
    OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
        Text("Comprobar de nuevo")
    }
}

@Composable
private fun VersionRow(name: String, version: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, fontWeight = FontWeight.Medium)
        Text(
            version,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

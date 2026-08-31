package com.termdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.termdroid.rootfs.NodeInstaller
import com.termdroid.rootfs.TermuxCommandRunner
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val agent: AgentViewModel by viewModels()
    private val terminal: TerminalViewModel by viewModels()
    private var officialCliState by mutableStateOf<OfficialCliSetupState>(OfficialCliSetupState.Checking)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedTextOf(intent)?.let(agent::prefill)
        AgentCliServer.start(this, lifecycleScope)
        provisionOfficialCli()

        setContent {
            TermdroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Column(Modifier.padding(inner)) {
                        var tab by remember { mutableIntStateOf(0) }

                        PrimaryTabRow(selectedTabIndex = tab) {
                            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Herramientas") })
                            Tab(tab == 1, onClick = {
                                tab = 1
                                agent.refreshCredentials()
                            }, text = { Text(androidx.compose.ui.res.stringResource(R.string.tab_chat)) })
                            Tab(tab == 2, onClick = { tab = 2 }, text = {
                                Text(androidx.compose.ui.res.stringResource(R.string.tab_terminal))
                            })
                        }

                        when (tab) {
                            0 -> OfficialCliOnboarding(
                                state = officialCliState,
                                onConfigure = ::installOfficialCli,
                                onRefresh = ::refreshOfficialCli,
                                onOpenClaude = { openOfficialCli("claude") },
                                onOpenCodex = { openOfficialCli("codex") },
                                modifier = Modifier.fillMaxSize(),
                            )
                            1 -> ChatScreen(agent, Modifier.fillMaxSize())
                            else -> TerminalScreen(terminal, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    private fun provisionOfficialCli() {
        val readiness = TermuxCommandRunner.readinessError(
            TermuxCommandRunner.isInstalled(this),
            TermuxCommandRunner.hasPermission(this),
        )
        officialCliState = setupState(readiness)
        if (readiness != null) return

        if (getSharedPreferences("termdroid", MODE_PRIVATE)
                .getInt("official_cli_revision", 0) >= OFFICIAL_CLI_REVISION
        ) {
            refreshOfficialCli()
        } else {
            installOfficialCli()
        }
    }

    private fun installOfficialCli() {
        val readiness = TermuxCommandRunner.readinessError(
            TermuxCommandRunner.isInstalled(this),
            TermuxCommandRunner.hasPermission(this),
        )
        if (readiness != null) {
            officialCliState = OfficialCliSetupState.ActionRequired(readiness)
            return
        }

        lifecycleScope.launch {
            officialCliState = OfficialCliSetupState.Preparing("Instalando Node.js, Claude Code y Codex oficiales.")
            NodeInstaller.installFullEnvironment(this@MainActivity) { progress ->
                runOnUiThread { officialCliState = OfficialCliSetupState.Preparing(progress) }
            }.onSuccess { versions ->
                getSharedPreferences("termdroid", MODE_PRIVATE)
                    .edit()
                    .putInt("official_cli_revision", OFFICIAL_CLI_REVISION)
                    .apply()
                officialCliState = OfficialCliSetupState.Ready(versions)
            }.onFailure(::showSetupFailure)
        }
    }

    private fun refreshOfficialCli() {
        lifecycleScope.launch {
            officialCliState = OfficialCliSetupState.Checking
            NodeInstaller.checkEnvironment(this@MainActivity)
                .onSuccess { officialCliState = OfficialCliSetupState.Ready(it) }
                .onFailure(::showSetupFailure)
        }
    }

    private fun openOfficialCli(command: String) {
        TermuxCommandRunner.openCli(this, command)
            .onFailure(::showSetupFailure)
    }

    private fun showSetupFailure(error: Throwable) {
        officialCliState = OfficialCliSetupState.Failed(
            error.message.orEmpty().ifBlank { "No se pudo completar la configuración. Reintentá desde Termdroid." },
        )
    }

    private companion object {
        const val OFFICIAL_CLI_REVISION = 2
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        sharedTextOf(intent)?.let(agent::prefill)
    }
}

@Composable
fun TermdroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

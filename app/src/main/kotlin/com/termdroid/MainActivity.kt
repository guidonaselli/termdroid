package com.termdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.termdroid.probe.CapabilityProbe
import com.termdroid.terminal.ShellSession

/**
 * Cero friccion: el primer frame util es el chat.
 *
 * Sin pantalla de bienvenida y sin pedir un solo permiso al arrancar.
 * Ver 10_TECH/ONBOARDING.md.
 */
class MainActivity : ComponentActivity() {

    private val agent: AgentViewModel by viewModels()
    private lateinit var session: ShellSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val caps = CapabilityProbe(this).get()
        session = ShellSession(this, caps.backend, lifecycleScope)
        if (caps.hasShell) session.start()

        setContent {
            TermdroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Column(Modifier.padding(inner)) {
                        // Chat primero, terminal segundo: la jerarquia esta
                        // invertida a proposito respecto de una terminal clasica.
                        var tab by remember { mutableIntStateOf(0) }

                        TabRow(selectedTabIndex = tab) {
                            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Chat") })
                            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Terminal") })
                        }

                        when (tab) {
                            0 -> ChatScreen(agent, Modifier.fillMaxSize())
                            else -> TerminalScreen(session, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session.stop()
    }
}

@Composable
fun TermdroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

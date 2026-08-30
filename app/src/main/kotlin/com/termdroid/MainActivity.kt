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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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

/** Cero friccion: el primer frame util es el chat. */
class MainActivity : ComponentActivity() {

    private val agent: AgentViewModel by viewModels()

    private val terminal: TerminalViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedTextOf(intent)?.let(agent::prefill)

        setContent {
            TermdroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
                    Column(Modifier.padding(inner)) {
                        var tab by remember { mutableIntStateOf(0) }

                        PrimaryTabRow(selectedTabIndex = tab) {
                            Tab(tab == 0, onClick = { tab = 0 }, text = { Text("Chat") })
                            Tab(tab == 1, onClick = { tab = 1 }, text = { Text("Terminal") })
                        }

                        when (tab) {
                            0 -> ChatScreen(agent, Modifier.fillMaxSize())
                            else -> TerminalScreen(terminal, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
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

package com.termdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.termdroid.probe.CapabilityProbe
import com.termdroid.terminal.ShellSession

/**
 * Cero friccion: el primer frame util es el terminal.
 *
 * Nada de pantalla de bienvenida y ningun permiso pedido al arrancar. El probe
 * corre una sola vez y se cachea; en el peor caso son decenas de milisegundos.
 * Ver 10_TECH/ONBOARDING.md.
 */
class MainActivity : ComponentActivity() {

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
                    TerminalScreen(
                        session = session,
                        modifier = Modifier.padding(inner),
                    )
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

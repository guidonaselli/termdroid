package com.termdroid.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log

class SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                SpikeScreen()
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun SpikeScreen() {
    val ctx = LocalContext.current
    val exp = remember { ExecExperiments(ctx) }
    var output by remember { mutableStateOf(exp.deviceInfo()) }

    fun show(label: String, body: () -> String) {
        val text = runCatching(body).getOrElse { "EXCEPCION: $it" }
        output = "=== $label ===\n$text"
        // logcat para poder capturar el resultado sin leer la pantalla
        Log.i(TAG, "=== $label ===\n$text")
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("F-001 exec spike", style = MaterialTheme.typography.titleMedium)

            Button(onClick = { show("todo") { exp.runAll() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Correr todo")
            }
            Button(onClick = { show("staging") { exp.stageIntoFilesDir() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Copiar a filesDir")
            }
            Button(onClick = { show("nivel 0") { exp.level0() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Nivel 0 - execve directo")
            }
            Button(onClick = { show("nivel 1") { exp.level1() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Nivel 1 - nativeLibraryDir")
            }
            Button(onClick = { show("nivel 2") { exp.level2() } }, modifier = Modifier.fillMaxWidth()) {
                Text("Nivel 2 - linker explicito")
            }

            Text(
                text = output,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

private const val TAG = "TermdroidSpike"

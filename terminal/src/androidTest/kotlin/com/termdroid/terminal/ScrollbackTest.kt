package com.termdroid.terminal

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.exec.ExecBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollbackTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: ShellSession? = null

    @After
    fun tearDown() {
        session?.stop()
        scope.cancel()
    }

    private suspend fun esperar(s: ShellSession, cond: (ScreenSnapshot) -> Boolean): Boolean =
        withTimeoutOrNull(8000) {
            while (!cond(s.screen.value)) delay(50)
            true
        } ?: false

    @Test
    fun loQueSaleDePantallaQuedaEnElScrollback() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start(rows = 10, cols = 60)

        s.send("i=0; while [ \$i -lt 40 ]; do echo FILA-\$i; i=\$((i+1)); done\n")

        val listo = esperar(s) { it.text().contains("FILA-39") }
        assertTrue("no termino de imprimir:\n${s.screen.value.text()}", listo)

        val pantalla = s.screen.value
        assertTrue("deberia haber scrollback", pantalla.scrollback.isNotEmpty())
        assertTrue(
            "el total tiene que superar la grilla visible",
            pantalla.totalRows > pantalla.rows,
        )

        val todo = pantalla.fullText()
        assertTrue("las primeras filas se perdieron", todo.contains("FILA-0"))
        assertTrue(todo.contains("FILA-39"))

        assertFalse("la primera fila ya no esta en la grilla visible", pantalla.text().contains("FILA-0"))
    }

    @Test
    fun rowAtRecorreScrollbackYPantallaEnOrden() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start(rows = 8, cols = 40)

        s.send("i=0; while [ \$i -lt 25 ]; do echo L\$i; i=\$((i+1)); done\n")
        assertTrue(esperar(s) { it.text().contains("L24") })

        val p = s.screen.value
        val lineas = (0 until p.totalRows).map { i ->
            p.rowAt(i).let { fila -> String(CharArray(fila.size) { fila[it].char }) }.trimEnd()
        }

        val indiceL0 = lineas.indexOfFirst { it == "L0" }
        val indiceL24 = lineas.indexOfFirst { it == "L24" }
        assertTrue("L0 deberia aparecer", indiceL0 >= 0)
        assertTrue("L24 deberia aparecer", indiceL24 >= 0)
        assertTrue("el orden tiene que ser cronologico", indiceL0 < indiceL24)
    }
}

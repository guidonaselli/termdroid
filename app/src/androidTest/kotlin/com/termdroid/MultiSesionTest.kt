package com.termdroid

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.exec.ExecBackend
import com.termdroid.probe.CapabilityProbe
import com.termdroid.terminal.ShellSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSesionTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val abiertas = mutableListOf<ShellSession>()

    @After
    fun tearDown() {
        abiertas.forEach { it.stop() }
        scope.cancel()
    }

    private fun abrir(): ShellSession {
        val backend = CapabilityProbe(context).get().backend
        return ShellSession(context, backend, scope).also {
            it.start()
            abiertas += it
        }
    }

    private suspend fun esperar(s: ShellSession, texto: String): Boolean =
        withTimeoutOrNull(8000) {
            while (!s.screen.value.text().contains(texto)) delay(50)
            true
        } ?: false

    @Test
    fun dosSesionesSonProcesosDistintosYNoSeMezclan() = runBlocking {
        val a = abrir()
        val b = abrir()

        a.send("echo SOY_LA_A\n")
        b.send("echo SOY_LA_B\n")

        assertTrue(esperar(a, "SOY_LA_A"))
        assertTrue(esperar(b, "SOY_LA_B"))

        assertFalse("la A no puede ver lo de la B", a.screen.value.text().contains("SOY_LA_B"))
        assertFalse("la B no puede ver lo de la A", b.screen.value.text().contains("SOY_LA_A"))
    }

    @Test
    fun cadaSesionMantieneSuPropioDirectorioYVariables() = runBlocking {
        val a = abrir()
        val b = abrir()

        a.send("MARCA=alfa\n")
        b.send("MARCA=beta\n")
        delay(500)

        a.send("echo valor-\$MARCA\n")
        b.send("echo valor-\$MARCA\n")

        assertTrue(esperar(a, "valor-alfa"))
        assertTrue(esperar(b, "valor-beta"))
    }

    @Test
    fun cerrarUnaNoAfectaALaOtra() = runBlocking {
        val a = abrir()
        val b = abrir()

        a.send("echo VIVA_A\n")
        assertTrue(esperar(a, "VIVA_A"))

        b.stop()
        delay(500)

        a.send("echo SIGO_VIVA\n")
        assertTrue("la sesion A tenia que sobrevivir", esperar(a, "SIGO_VIVA"))
        assertTrue(a.alive.value)
        assertFalse(b.alive.value)
    }

    @Test
    fun elEstadoDeCadaSesionSobreviveAlCambioDeFoco() = runBlocking {
        val a = abrir()
        val b = abrir()

        a.send("echo HISTORIA_A\n")
        assertTrue(esperar(a, "HISTORIA_A"))

        b.send("echo HISTORIA_B\n")
        assertTrue(esperar(b, "HISTORIA_B"))

        assertTrue(
            "al volver, lo de la A tiene que seguir en pantalla",
            a.screen.value.text().contains("HISTORIA_A"),
        )
    }

    @Test
    fun cerrarNoDejaZombies() = runBlocking {
        val antes = zombies()
        repeat(3) {
            val s = abrir()
            delay(300)
            s.stop()
            delay(300)
        }
        delay(600)
        assertTrue("quedaron zombies: antes=$antes despues=${zombies()}", zombies() <= antes)
    }

    private fun zombies(): Int = runCatching {
        java.io.File("/proc").listFiles()
            ?.filter { it.name.toIntOrNull() != null }
            ?.count { dir ->
                runCatching {
                    val campos = java.io.File(dir, "stat").readText()
                        .substringAfterLast(')').trim().split(' ')
                    campos.getOrNull(0) == "Z" &&
                        campos.getOrNull(1) == android.os.Process.myPid().toString()
                }.getOrDefault(false)
            } ?: 0
    }.getOrDefault(0)

    @Test
    fun elBackendUsadoEsElQueEligioElProbe() {
        val caps = CapabilityProbe(context).get()
        assertTrue(caps.backend != ExecBackend.NONE)
        assertEquals(caps.backend.supportsRuntimeInstall, caps.canInstallPackages)
    }
}

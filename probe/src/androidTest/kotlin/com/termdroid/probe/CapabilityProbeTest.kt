package com.termdroid.probe

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.exec.ExecBackend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El probe se verifica en un device. */
class CapabilityProbeTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun probeEligeUnBackendUsable() {
        val caps = CapabilityProbe(context).run()
        android.util.Log.i("TermdroidProbe", caps.summary())

        // El nivel 1 es el piso garantizado. Si falla, el empaquetado esta roto.
        assertTrue(
            "nativeLibraryDir deberia poder ejecutar. Fallos: ${caps.failures}",
            caps.nativeLibDirExec,
        )
        assertTrue("el backend no puede quedar en NONE", caps.backend != ExecBackend.NONE)
        assertTrue("deberia haber shell utilizable", caps.hasShell)
    }

    @Test
    fun elBackendEsCoherenteConLoMedido() {
        val caps = CapabilityProbe(context).run()
        val esperado = when {
            caps.directExec -> ExecBackend.DIRECT
            caps.linkerExec -> ExecBackend.LINKER
            caps.nativeLibDirExec -> ExecBackend.NATIVE_LIB_DIR
            else -> ExecBackend.NONE
        }
        assertEquals(esperado, caps.backend)
        assertEquals(caps.backend.supportsRuntimeInstall, caps.canInstallPackages)
    }

    /** Presupuesto del plan: el probe no puede demorar el primer arranque. */
    @Test
    fun probeCorreEnMenosDeDosSegundos() {
        val caps = CapabilityProbe(context).run()
        assertTrue("el probe tardo ${caps.probeMillis}ms", caps.probeMillis < 2000)
    }

    @Test
    fun elCacheDevuelveLoMismoQueLaMedicion() {
        val probe = CapabilityProbe(context)
        val medido = probe.run()
        val primero = probe.get()
        val segundo = probe.get()
        assertNotNull(primero)
        assertEquals(medido.backend, primero.backend)
        assertEquals(primero, segundo)
    }
}

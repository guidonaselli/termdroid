package com.termdroid

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedTextTest {

    @Test
    fun tomaElTextoDeUnShare() {
        val i = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "resumime esto")

        assertEquals("resumime esto", sharedTextOf(i))
    }

    @Test
    fun tomaElTextoSeleccionadoDeOtraApp() {
        val i = Intent(Intent.ACTION_PROCESS_TEXT)
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "seleccion" as CharSequence)

        assertEquals("seleccion", sharedTextOf(i))
    }

    @Test
    fun elLanzadorNormalNoTraeTexto() {
        assertNull(sharedTextOf(Intent(Intent.ACTION_MAIN)))
        assertNull(sharedTextOf(null))
    }

    @Test
    fun unShareVacioNoCuenta() {
        val i = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "   ")
        assertNull(sharedTextOf(i))

        assertNull(sharedTextOf(Intent(Intent.ACTION_SEND)))
    }

    @Test
    fun laAppEstaRegistradaComoDestinoDeCompartir() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")

        val destinos = context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }

        assertTrue("la app deberia aparecer al compartir texto", destinos.contains(context.packageName))
    }
}

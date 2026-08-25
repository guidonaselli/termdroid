package com.termdroid

import android.speech.SpeechRecognizer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DictadoTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun sinReconocedorAvisaEnVezDeFallar() {
        val d = Dictado(context)
        if (d.disponible) return

        var resultado: ResultadoDictado? = null
        val listo = CountDownLatch(1)
        d.escuchar {
            resultado = it
            listo.countDown()
        }

        assertTrue("deberia responder al toque", listo.await(3, TimeUnit.SECONDS))
        assertTrue(resultado is ResultadoDictado.Error)
        assertTrue((resultado as ResultadoDictado.Error).mensaje.contains("reconocimiento"))
    }

    @Test
    fun cadaErrorTieneUnMensajeEntendible() {
        val codigos = listOf(
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        )

        codigos.forEach { c ->
            val m = Dictado.descripcionDeError(c)
            assertTrue("el codigo $c no tiene mensaje", m.isNotBlank())
            assertFalse("el mensaje no puede ser un numero: $m", m.all { it.isDigit() })
        }
    }

    @Test
    fun elPermisoFaltanteSeExplicaEnCastellano() {
        assertEquals(
            "Falta el permiso de microfono.",
            Dictado.descripcionDeError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
    }

    @Test
    fun uncodigoDesconocidoNoRompe() {
        assertTrue(Dictado.descripcionDeError(9999).isNotBlank())
    }

    @Test
    fun detenerSinHaberEmpezadoNoRompe() {
        Dictado(context).detener()
    }
}

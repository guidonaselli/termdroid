package com.termdroid.core

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SecretStoreTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var store: SecretStore

    @Before
    fun setUp() {
        store = SecretStore(context)
        store.remove("prueba")
        store.apiKey = null
    }

    @Test
    fun guardaYRecupera() {
        store.put("prueba", "valor secreto")
        assertEquals("valor secreto", store.get("prueba"))
    }

    @Test
    fun loQueNoExisteEsNull() {
        assertNull(store.get("nunca-guardado"))
        assertFalse(store.has("nunca-guardado"))
    }

    /** Lo que queda en disco no puede ser el secreto en claro. */
    @Test
    fun elValorNoSeGuardaEnTextoPlano() {
        store.put("prueba", "sk-ant-secreto-123")

        val prefs = context.getSharedPreferences("termdroid_secrets", 0)
        val guardado = prefs.getString("prueba", "") ?: ""

        assertTrue("no se guardo nada", guardado.isNotBlank())
        assertFalse("el secreto quedo legible en disco", guardado.contains("sk-ant-secreto-123"))
        assertNotEquals("sk-ant-secreto-123", guardado)
    }

    /** GCM necesita un IV distinto por mensaje: dos cifrados del mismo texto difieren. */
    @Test
    fun dosCifradosDelMismoTextoNoSonIguales() {
        store.put("a", "mismo texto")
        val primero = context.getSharedPreferences("termdroid_secrets", 0).getString("a", "")
        store.put("b", "mismo texto")
        val segundo = context.getSharedPreferences("termdroid_secrets", 0).getString("b", "")

        assertNotEquals(primero, segundo)
        assertEquals("mismo texto", store.get("a"))
        assertEquals("mismo texto", store.get("b"))
    }

    @Test
    fun sobrescribirReemplaza() {
        store.put("prueba", "uno")
        store.put("prueba", "dos")
        assertEquals("dos", store.get("prueba"))
    }

    @Test
    fun laApiKeyUsaElMismoCamino() {
        assertFalse(store.has("anthropic_api_key"))
        store.apiKey = "sk-ant-xyz"
        assertEquals("sk-ant-xyz", store.apiKey)

        store.apiKey = null
        assertNull(store.apiKey)
    }

    /** Un dato corrupto devuelve null y se limpia, en vez de tirar una excepcion. */
    @Test
    fun unValorCorruptoNoRompe() {
        context.getSharedPreferences("termdroid_secrets", 0)
            .edit().putString("roto", "esto-no-es-base64-valido!!!").apply()

        assertNull(store.get("roto"))
        assertFalse("deberia haberse limpiado", store.has("roto"))
    }
}

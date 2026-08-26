package com.termdroid.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbHandshakeTest {

    private val key = AdbKey.generate()
    private val handshake = AdbHandshake(key)

    private fun recibir(m: AdbMessage): HandshakeStep {
        val bytes = m.encode()
        val header = AdbMessage.parseHeader(bytes)!!
        return handshake.onMessage(header, m.payload)
    }

    private fun token(bytes: Int = 20) =
        AdbMessage(AdbCommand.AUTH, AdbMessage.AUTH_TOKEN, 0, ByteArray(bytes) { it.toByte() })

    @Test
    fun arrancaMandandoConexion() {
        val paso = handshake.start()
        assertTrue(paso is HandshakeStep.Send)
        assertEquals(AdbCommand.CNXN, (paso as HandshakeStep.Send).message.command)
    }

    @Test
    fun alPrimerTokenRespondeConLaFirma() {
        handshake.start()
        val paso = recibir(token())

        assertTrue(paso.toString(), paso is HandshakeStep.Send)
        val m = (paso as HandshakeStep.Send).message
        assertEquals(AdbCommand.AUTH, m.command)
        assertEquals(AdbMessage.AUTH_SIGNATURE, m.arg0)
        assertEquals(256, m.payload.size)
    }

    @Test
    fun alSegundoTokenPideAutorizacionDelUsuario() {
        handshake.start()
        recibir(token())
        val paso = recibir(token())

        assertTrue(paso.toString(), paso is HandshakeStep.WaitingForUser)
        assertTrue((paso as HandshakeStep.WaitingForUser).fingerprint.contains(":"))
    }

    @Test
    fun elTercerTokenEsUnRechazo() {
        handshake.start()
        recibir(token())
        recibir(token())
        val paso = recibir(token())

        assertTrue(paso.toString(), paso is HandshakeStep.Failed)
        assertTrue((paso as HandshakeStep.Failed).reason.contains("rechaz"))
    }

    @Test
    fun unaConexionDeVueltaEsExito() {
        handshake.start()
        recibir(token())
        val paso = recibir(
            AdbMessage(AdbCommand.CNXN, AdbMessage.VERSION, 0, "device::ro.product=x".toByteArray()),
        )

        assertTrue(paso.toString(), paso is HandshakeStep.Connected)
        assertTrue((paso as HandshakeStep.Connected).banner.startsWith("device::"))
    }

    @Test
    fun unCierreEsFallo() {
        handshake.start()
        val paso = recibir(AdbMessage(AdbCommand.CLSE))
        assertTrue(paso is HandshakeStep.Failed)
    }

    @Test
    fun unMensajeInesperadoNoSeInterpretaComoExito() {
        handshake.start()
        val paso = recibir(AdbMessage(AdbCommand.WRTE, 0, 0, "cualquier cosa".toByteArray()))

        assertTrue(paso.toString(), paso is HandshakeStep.Failed)
        assertTrue((paso as HandshakeStep.Failed).reason.contains("inesperado"))
    }

    @Test
    fun unPayloadQueNoCoincideConLaCabeceraSeRechaza() {
        handshake.start()
        val m = token()
        val header = AdbMessage.parseHeader(m.encode())!!

        val paso = handshake.onMessage(header, "otra cosa".toByteArray())

        assertTrue(paso.toString(), paso is HandshakeStep.Failed)
        assertTrue((paso as HandshakeStep.Failed).reason.contains("no coincide"))
    }

    @Test
    fun unAuthQueNoEsTokenSeRechaza() {
        handshake.start()
        val paso = recibir(AdbMessage(AdbCommand.AUTH, AdbMessage.AUTH_SIGNATURE, 0, ByteArray(4)))

        assertTrue(paso.toString(), paso is HandshakeStep.Failed)
    }

    @Test
    fun elMensajeDeClavePublicaEsValido() {
        val m = handshake.publicKeyMessage()
        assertEquals(AdbCommand.AUTH, m.command)
        assertEquals(AdbMessage.AUTH_RSAPUBLICKEY, m.arg0)
        assertEquals(0, m.payload.last().toInt())
    }

    @Test
    fun laHuellaEsEstableYPropiaDeLaClave() {
        val a = AdbHandshake(key).fingerprint()
        assertEquals(a, handshake.fingerprint())
        assertNotEquals(a, AdbHandshake(AdbKey.generate()).fingerprint())
    }

    @Test
    fun laHuellaTieneElFormatoQueMuestraElDialogo() {
        val h = handshake.fingerprint()
        val partes = h.split(":")
        assertEquals(16, partes.size)
        assertTrue(partes.all { it.length == 2 })
    }

    @Test
    fun firmaElTokenQueLlego() {
        handshake.start()
        val t = token()
        val paso = recibir(t) as HandshakeStep.Send

        assertFalse(
            "la firma no puede ser la del token vacio",
            paso.message.payload.contentEquals(key.sign(ByteArray(20))),
        )
        assertTrue(paso.message.payload.contentEquals(key.sign(t.payload)))
    }
}

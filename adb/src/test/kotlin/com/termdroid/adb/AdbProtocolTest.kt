package com.termdroid.adb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbMessageTest {

    @Test
    fun laCabeceraMideVeinticuatroBytes() {
        val m = AdbMessage(AdbCommand.OKAY)
        assertEquals(24, m.encode().size)
    }

    @Test
    fun losComandosUsanLosValoresDelProtocolo() {
        assertEquals(0x4e584e43, AdbCommand.CNXN.value)
        assertEquals(0x48545541, AdbCommand.AUTH.value)
        assertEquals(0x4e45504f, AdbCommand.OPEN.value)
        assertEquals(0x59414b4f, AdbCommand.OKAY.value)
        assertEquals(0x45534c43, AdbCommand.CLSE.value)
        assertEquals(0x45545257, AdbCommand.WRTE.value)
    }

    @Test
    fun losComandosSonSuNombreEnAscii() {
        fun ascii(v: Int) = String(
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array(),
        )
        AdbCommand.entries.forEach { assertEquals(it.name, ascii(it.value)) }
    }

    @Test
    fun elMagicEsElComandoNegado() {
        val bytes = AdbMessage(AdbCommand.CNXN).encode()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val comando = b.int
        b.position(20)
        assertEquals(comando.inv(), b.int)
    }

    @Test
    fun laCabeceraLlevaLargoYChecksumDelPayload() {
        val payload = "shell:ls".toByteArray()
        val bytes = AdbMessage(AdbCommand.OPEN, 1, 0, payload).encode()

        val header = AdbMessage.parseHeader(bytes)!!
        assertEquals(AdbCommand.OPEN, header.command)
        assertEquals(1, header.arg0)
        assertEquals(payload.size, header.payloadLength)
        assertTrue(header.matches(payload))
    }

    @Test
    fun unPayloadCambiadoNoValidaContraLaCabecera() {
        val bytes = AdbMessage(AdbCommand.WRTE, 1, 2, "hola".toByteArray()).encode()
        val header = AdbMessage.parseHeader(bytes)!!

        assertFalse(header.matches("chau".toByteArray()))
        assertFalse(header.matches("hola ".toByteArray()))
    }

    @Test
    fun elPayloadViajaDespuesDeLaCabecera() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val bytes = AdbMessage(AdbCommand.WRTE, 0, 0, payload).encode()

        assertEquals(24 + payload.size, bytes.size)
        assertArrayEquals(payload, bytes.copyOfRange(24, bytes.size))
    }

    @Test
    fun unaCabeceraConMagicRotoSeRechaza() {
        val bytes = AdbMessage(AdbCommand.CNXN).encode()
        bytes[20] = (bytes[20] + 1).toByte()
        assertNull(AdbMessage.parseHeader(bytes))
    }

    @Test
    fun unComandoDesconocidoSeRechaza() {
        val b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        val basura = 0x11223344
        b.putInt(basura).putInt(0).putInt(0).putInt(0).putInt(0).putInt(basura.inv())
        assertNull(AdbMessage.parseHeader(b.array()))
    }

    @Test
    fun unLargoAbsurdoSeRechaza() {
        val b = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
        b.putInt(AdbCommand.WRTE.value).putInt(0).putInt(0)
            .putInt(Int.MAX_VALUE).putInt(0).putInt(AdbCommand.WRTE.value.inv())
        assertNull("un largo mayor al maximo tiene que rechazarse", AdbMessage.parseHeader(b.array()))
    }

    @Test
    fun unaCabeceraCortadaSeRechaza() {
        assertNull(AdbMessage.parseHeader(ByteArray(10)))
        assertNull(AdbMessage.parseHeader(ByteArray(0)))
    }

    @Test
    fun elMensajeDeConexionAnunciaVersionYTamano() {
        val m = AdbMessage.connect()
        assertEquals(AdbCommand.CNXN, m.command)
        assertEquals(0x01000001, m.arg0)
        assertEquals(256 * 1024, m.arg1)
        assertTrue(String(m.payload).startsWith("host::"))
    }

    @Test
    fun losMensajesDeAuthUsanSuSubtipo() {
        assertEquals(AdbMessage.AUTH_SIGNATURE, AdbMessage.signature(ByteArray(256)).arg0)
        assertEquals(AdbMessage.AUTH_RSAPUBLICKEY, AdbMessage.publicKey(ByteArray(10)).arg0)
    }

    @Test
    fun openTerminaElDestinoEnNulo() {
        val m = AdbMessage.open(7, "shell:id")
        assertEquals(7, m.arg0)
        assertEquals("shell:id\u0000", String(m.payload))
        assertEquals(0, m.payload.last().toInt())
    }

    @Test
    fun dosMensajesIgualesSonIguales() {
        val a = AdbMessage(AdbCommand.WRTE, 1, 2, byteArrayOf(9))
        val b = AdbMessage(AdbCommand.WRTE, 1, 2, byteArrayOf(9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}

class AdbKeyTest {

    private val key by lazy { AdbKey.generate() }

    @Test
    fun laClavePublicaMideLoQueEsperaAdbd() {
        assertEquals(524, AdbKey.PUBLIC_KEY_BYTES)
        assertEquals(524, key.publicKeyBytes.size)
    }

    @Test
    fun arrancaConLaCantidadDePalabrasDelModulo() {
        val b = ByteBuffer.wrap(key.publicKeyBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(64, b.int)
    }

    @Test
    fun elExponenteEsElUltimoCampo() {
        val b = ByteBuffer.wrap(key.publicKeyBytes).order(ByteOrder.LITTLE_ENDIAN)
        b.position(AdbKey.PUBLIC_KEY_BYTES - 4)
        assertEquals(65537, b.int)
    }

    @Test
    fun elModuloViajaInvertido() {
        val publica = key.keyPair.public as java.security.interfaces.RSAPublicKey
        val bigEndian = publica.modulus.toByteArray().let {
            if (it.size > 256) it.copyOfRange(it.size - 256, it.size) else it
        }

        val enviado = key.publicKeyBytes.copyOfRange(8, 8 + 256)
        assertArrayEquals(bigEndian.reversedArray(), enviado)
    }

    @Test
    fun loQueViajaPorElCableEsBase64ConNombre() {
        val bytes = key.publicKeyForAdb()
        assertEquals("tiene que terminar en nulo", 0, bytes.last().toInt())

        val partes = String(bytes).trimEnd('\u0000').split(" ")
        assertEquals(2, partes.size)
        assertEquals("termdroid@android", partes[1])
        assertEquals(524, java.util.Base64.getDecoder().decode(partes[0]).size)
    }

    @Test
    fun firmaUnTokenYLaFirmaMideLoDelModulo() {
        val token = ByteArray(20) { it.toByte() }
        val firma = key.sign(token)
        assertEquals(256, firma.size)
    }

    @Test
    fun dosTokensDistintosDanFirmasDistintas() {
        val a = key.sign(ByteArray(20) { 1 })
        val b = key.sign(ByteArray(20) { 2 })
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun laMismaClaveFirmaIgualElMismoToken() {
        val token = ByteArray(20) { 7 }
        assertArrayEquals(key.sign(token), key.sign(token))
    }

    @Test
    fun cadaClaveGeneradaEsDistinta() {
        assertFalse(AdbKey.generate().publicKeyBytes.contentEquals(AdbKey.generate().publicKeyBytes))
    }

    @Test
    fun elMensajeDeClavePublicaSeArmaEntero() {
        val m = AdbMessage.publicKey(key.publicKeyForAdb())
        assertEquals(AdbCommand.AUTH, m.command)
        assertEquals(AdbMessage.AUTH_RSAPUBLICKEY, m.arg0)
        assertNotNull(AdbMessage.parseHeader(m.encode()))
    }
}

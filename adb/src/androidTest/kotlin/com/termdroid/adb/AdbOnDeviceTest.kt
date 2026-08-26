package com.termdroid.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket

class AdbOnDeviceTest {

    private val port = 5555

    private var motivo = ""

    private fun conectar(): Socket? = runCatching {
        val destino = InetSocketAddress("127.0.0.1", port)
        val s = Socket()
        s.connect(destino, 2000)
        s.soTimeout = 8000
        s
    }.onFailure {
        motivo = "$it"
        android.util.Log.i("TDAdb", "no conecto a 127.0.0.1:$port -> $it")
    }.getOrNull()

    @Test
    fun elClienteHablaConElAdbdDelPropioTelefono() {
        val socket = conectar()
        assumeTrue("no conecto: $motivo", socket != null)

        socket!!.use { s ->
            val entrada = DataInputStream(s.getInputStream())
            s.getOutputStream().write(AdbMessage.connect().encode())
            s.getOutputStream().flush()

            val cabecera = ByteArray(AdbMessage.HEADER_SIZE)
            entrada.readFully(cabecera)
            val header = AdbMessage.parseHeader(cabecera)

            assertNotNull("adbd devolvio algo que no parsea como protocolo ADB", header)

            val payload = ByteArray(header!!.payloadLength)
            if (header.payloadLength > 0) entrada.readFully(payload)

            android.util.Log.i(
                "TDAdb",
                "respuesta=${header.command} largo=${header.payloadLength} " +
                    "checksum=${header.payloadChecksum} payload=${String(payload).take(80)}",
            )

            assertTrue(
                "una respuesta real tiene que validar contra su cabecera",
                header.matches(payload),
            )
            assertTrue(
                "se esperaba CNXN o AUTH, llego ${header.command}",
                header.command == AdbCommand.CNXN || header.command == AdbCommand.AUTH,
            )
            assertEquals(
                "el magic tiene que ser el complemento del comando",
                header.command.value.inv(),
                java.nio.ByteBuffer.wrap(cabecera)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(20),
            )
        }
    }

    @Test
    fun elHandshakeAvanzaContraAdbdReal() {
        val socket = conectar()
        assumeTrue(socket != null)

        val handshake = AdbHandshake(AdbKey.generate())

        socket!!.use { s ->
            val entrada = DataInputStream(s.getInputStream())
            s.getOutputStream().write((handshake.start() as HandshakeStep.Send).message.encode())
            s.getOutputStream().flush()

            val cabecera = ByteArray(AdbMessage.HEADER_SIZE)
            entrada.readFully(cabecera)
            val header = AdbMessage.parseHeader(cabecera)!!
            val payload = ByteArray(header.payloadLength)
            if (header.payloadLength > 0) entrada.readFully(payload)

            val paso = handshake.onMessage(header, payload)
            android.util.Log.i("TDAdb", "handshake -> $paso")

            assertTrue(
                "el handshake no puede fallar contra un adbd real: $paso",
                paso !is HandshakeStep.Failed,
            )
        }
    }
}

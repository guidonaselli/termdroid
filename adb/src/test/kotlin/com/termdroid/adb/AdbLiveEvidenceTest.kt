package com.termdroid.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket

class AdbLiveEvidenceTest {

    private val host: String = System.getProperty("adb.host") ?: "127.0.0.1"
    private val port: Int = System.getProperty("adb.port")?.toIntOrNull() ?: 5555
    private val exigido = System.getProperty("adb.required") == "true"

    private var motivo: String = ""

    private fun conectar(): Socket? = runCatching {
        val destino = InetSocketAddress(host, port)
        val s = Socket()
        s.connect(destino, 2000)
        s.soTimeout = 8000
        s
    }.onFailure { motivo = "$it" }.getOrNull()

    @Test
    fun elClienteHablaConUnAdbdDeVerdad() {
        val socket = conectar()

        if (socket == null) {
            println("EVIDENCIA no se pudo conectar a $host:$port -> $motivo")
            assertTrue(
                "se pidio adbd en $host:$port y no habia ninguno escuchando: $motivo",
                !exigido,
            )
            assumeTrue("sin adbd en $host:$port", false)
            return
        }

        socket.use { s ->
            val entrada = DataInputStream(s.getInputStream())
            s.getOutputStream().write(AdbMessage.connect().encode())
            s.getOutputStream().flush()

            val cabecera = ByteArray(AdbMessage.HEADER_SIZE)
            entrada.readFully(cabecera)
            val header = AdbMessage.parseHeader(cabecera)

            assertNotNull("adbd devolvio algo que no parsea como protocolo ADB", header)
            assertEquals(
                "el magic tiene que ser el complemento del comando",
                header!!.command.value.inv(),
                java.nio.ByteBuffer.wrap(cabecera)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(20),
            )

            val payload = ByteArray(header.payloadLength)
            if (header.payloadLength > 0) entrada.readFully(payload)

            println("EVIDENCIA $host:$port -> ${header.command} largo=${header.payloadLength} " +
                "checksum=${header.payloadChecksum}")
            println("  payload: " + String(payload).take(90))

            assertTrue(
                "una respuesta real de adbd tiene que validar contra su cabecera",
                header.matches(payload),
            )
            assertTrue(
                "se esperaba CNXN o AUTH, llego ${header.command}",
                header.command == AdbCommand.CNXN || header.command == AdbCommand.AUTH,
            )
        }
    }

    @Test
    fun adbdAceptaNuestroMensajeDeConexion() {
        val socket = conectar()
        assumeTrue("sin adbd en $host:$port", socket != null)

        socket!!.use { s ->
            val entrada = DataInputStream(s.getInputStream())
            s.getOutputStream().write(AdbMessage.connect().encode())
            s.getOutputStream().flush()

            val cabecera = ByteArray(AdbMessage.HEADER_SIZE)
            entrada.readFully(cabecera)
            val header = AdbMessage.parseHeader(cabecera)!!

            assertTrue(
                "si adbd no hubiera entendido el CNXN, cerraria: llego ${header.command}",
                header.command != AdbCommand.CLSE,
            )
        }
    }
}

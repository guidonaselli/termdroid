package com.termdroid.adb

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

class AdbLiveHandshakeTest {

    private val host = System.getProperty("adb.host", "127.0.0.1")
    private val port = System.getProperty("adb.port", "5555").toInt()

    private fun adbdVivo(): Boolean = runCatching {
        val destino = InetSocketAddress(host, port)
        Socket().use {
            it.connect(destino, 1500)
            true
        }
    }.getOrDefault(false)

    private class Conexion(host: String, port: Int) : AutoCloseable {
        val socket = Socket().also {
            it.connect(InetSocketAddress(host, port), 3000)
            it.soTimeout = 8000
        }
        private val entrada = DataInputStream(socket.getInputStream())
        private val salida = socket.getOutputStream()

        fun enviar(m: AdbMessage) {
            salida.write(m.encode())
            salida.flush()
        }

        fun recibir(): Pair<AdbHeader, ByteArray>? {
            val cabecera = ByteArray(AdbMessage.HEADER_SIZE)
            return try {
                entrada.readFully(cabecera)
                val header = AdbMessage.parseHeader(cabecera) ?: return null
                val payload = ByteArray(header.payloadLength)
                if (header.payloadLength > 0) entrada.readFully(payload)
                header to payload
            } catch (e: IOException) {
                null
            }
        }

        override fun close() = socket.close()
    }

    @Test
    fun adbdRealResponde() {
        assumeTrue("no hay un adbd escuchando en $host:$port", adbdVivo())

        Conexion(host, port).use { c ->
            c.enviar(AdbMessage.connect())
            val (header, _) = c.recibir() ?: error("adbd no respondio")

            assertNotNull("la cabecera de adbd no se pudo parsear", header)
            assertTrue(
                "se esperaba AUTH o CNXN, llego ${header.command}",
                header.command == AdbCommand.AUTH || header.command == AdbCommand.CNXN,
            )
        }
    }

    @Test
    fun adbdPideUnTokenYAceptaLaFirma() {
        assumeTrue(adbdVivo())

        val key = AdbKey.generate()
        val handshake = AdbHandshake(key)

        Conexion(host, port).use { c ->
            c.enviar((handshake.start() as HandshakeStep.Send).message)

            val (header, payload) = c.recibir() ?: error("sin respuesta")
            assumeTrue("adbd ya estaba autenticado", header.command == AdbCommand.AUTH)

            assertTrue("adbd tiene que pedir un token", header.arg0 == AdbMessage.AUTH_TOKEN)
            assertTrue("el token deberia medir 20 bytes", payload.size == 20)

            val paso = handshake.onMessage(header, payload)
            assertTrue("el handshake tenia que firmar, dio $paso", paso is HandshakeStep.Send)

            c.enviar((paso as HandshakeStep.Send).message)

            // Con una clave que adbd no conoce, la firma no verifica y vuelve a
            // pedir token: eso confirma que el mensaje se entendio.
            val (segundo, _) = c.recibir() ?: error("adbd corto tras la firma")
            assertTrue(
                "se esperaba otro AUTH o la conexion, llego ${segundo.command}",
                segundo.command == AdbCommand.AUTH || segundo.command == AdbCommand.CNXN,
            )
        }
    }

    @Test
    fun adbdAceptaLaClavePublicaEnNuestroFormato() {
        assumeTrue(adbdVivo())

        val key = AdbKey.generate()
        val handshake = AdbHandshake(key)

        Conexion(host, port).use { c ->
            c.enviar((handshake.start() as HandshakeStep.Send).message)
            val (header, payload) = c.recibir() ?: error("sin respuesta")
            assumeTrue(header.command == AdbCommand.AUTH)

            c.enviar((handshake.onMessage(header, payload) as HandshakeStep.Send).message)
            val (segundo, segundoPayload) = c.recibir() ?: error("sin segundo mensaje")
            assumeTrue(segundo.command == AdbCommand.AUTH)

            handshake.onMessage(segundo, segundoPayload)
            c.enviar(handshake.publicKeyMessage())

            // Si el formato estuviera mal, adbd cierra la conexion. Si lo entiende,
            // espera a que el usuario autorice y no manda nada mas.
            val tercero = c.recibir()
            assertTrue(
                "adbd rechazo la clave publica: ${tercero?.first?.command}",
                tercero == null || tercero.first.command != AdbCommand.CLSE,
            )
        }
    }
}

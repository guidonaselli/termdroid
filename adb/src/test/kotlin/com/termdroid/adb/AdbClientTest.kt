package com.termdroid.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.Executors

class AdbClientTest {

    @Test
    fun ejecutaComandoCompletoContraServidorSimulado() {
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            val socket = server.accept()
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            // 1. Recibir CNXN
            val cnxnHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
            input.readFully(cnxnHeaderBytes)
            val cnxnHeader = AdbMessage.parseHeader(cnxnHeaderBytes)!!
            val cnxnPayload = ByteArray(cnxnHeader.payloadLength)
            if (cnxnHeader.payloadLength > 0) input.readFully(cnxnPayload)

            // 2. Responder AUTH con token
            val token = ByteArray(20) { it.toByte() }
            output.write(AdbMessage(AdbCommand.AUTH, AdbMessage.AUTH_TOKEN, 0, token).encode())
            output.flush()

            // 3. Recibir AUTH firma
            val signHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
            input.readFully(signHeaderBytes)
            val signHeader = AdbMessage.parseHeader(signHeaderBytes)!!
            val signPayload = ByteArray(signHeader.payloadLength)
            if (signHeader.payloadLength > 0) input.readFully(signPayload)

            // 4. Responder CNXN conectado
            output.write(AdbMessage(AdbCommand.CNXN, AdbMessage.VERSION, 256 * 1024, "device::ro.product=test\u0000".toByteArray()).encode())
            output.flush()

            // 5. Recibir OPEN
            val openHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
            input.readFully(openHeaderBytes)
            val openHeader = AdbMessage.parseHeader(openHeaderBytes)!!
            val openPayload = ByteArray(openHeader.payloadLength)
            if (openHeader.payloadLength > 0) input.readFully(openPayload)

            val localId = openHeader.arg0
            val remoteId = 42

            // 6. Responder OKAY
            output.write(AdbMessage(AdbCommand.OKAY, remoteId, localId).encode())
            output.flush()

            // 7. Enviar salida WRTE
            val outputText = "uid=2000(shell) gid=2000(shell)"
            output.write(AdbMessage(AdbCommand.WRTE, remoteId, localId, outputText.toByteArray()).encode())
            output.flush()

            // 8. Recibir ACK OKAY
            val ackHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
            input.readFully(ackHeaderBytes)

            // 9. Enviar CLSE
            output.write(AdbMessage(AdbCommand.CLSE, remoteId, localId).encode())
            output.flush()

            socket.close()
            server.close()
        }

        val client = AdbClient(host = "127.0.0.1", port = port)
        val result = client.execute("id", timeoutMs = 5000L)

        assertFalse(result.isError)
        assertEquals("uid=2000(shell) gid=2000(shell)", result.output)
        executor.shutdown()
    }

    @Test(expected = IOException::class)
    fun lanzaExcepcionSiPuertoInaccesible() {
        val client = AdbClient(host = "127.0.0.1", port = 59999)
        client.execute("id", timeoutMs = 1000L)
    }

    @Test
    fun manejaCierreDeStreamPorRechazo() {
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        executor.submit {
            val socket = server.accept()
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            // Recibir CNXN
            val cnxnHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
            input.readFully(cnxnHeaderBytes)
            val cnxnHeader = AdbMessage.parseHeader(cnxnHeaderBytes)!!
            if (cnxnHeader.payloadLength > 0) input.readFully(ByteArray(cnxnHeader.payloadLength))

            // Responder CNXN directo
            output.write(AdbMessage(AdbCommand.CNXN, AdbMessage.VERSION, 256 * 1024, "device::test\u0000".toByteArray()).encode())
            output.flush()

            // Recibir OPEN
            val openHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
            input.readFully(openHeaderBytes)
            val openHeader = AdbMessage.parseHeader(openHeaderBytes)!!
            if (openHeader.payloadLength > 0) input.readFully(ByteArray(openHeader.payloadLength))

            // Responder CLSE (rechazo)
            output.write(AdbMessage(AdbCommand.CLSE, 0, openHeader.arg0).encode())
            output.flush()

            socket.close()
            server.close()
        }

        val client = AdbClient(host = "127.0.0.1", port = port)
        try {
            client.execute("reboot", timeoutMs = 5000L)
            assertTrue("deberia haber lanzado IOException", false)
        } catch (e: IOException) {
            assertTrue(e.message?.contains("rechazo") == true)
        } finally {
            executor.shutdown()
        }
    }
}

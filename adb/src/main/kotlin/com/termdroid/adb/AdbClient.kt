package com.termdroid.adb

import java.io.DataInputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

data class AdbExecResult(
    val output: String,
    val exitCode: Int = 0,
    val isError: Boolean = exitCode != 0,
)

/** Cliente ADB sobre TCP que realiza autenticacion y ejecucion de comandos. */
class AdbClient(
    private val key: AdbKey = AdbKey.generate(),
    private val host: String = "127.0.0.1",
    private val port: Int = 5555,
) {
    private val localIdCounter = AtomicInteger(1)

    fun execute(command: String, timeoutMs: Long = 15000L): AdbExecResult {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), 3000)
            socket.soTimeout = timeoutMs.toInt()

            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val handshake = AdbHandshake(key)
            var step: HandshakeStep = handshake.start()

            while (step !is HandshakeStep.Connected) {
                when (step) {
                    is HandshakeStep.Send -> {
                        output.write(step.message.encode())
                        output.flush()
                    }
                    is HandshakeStep.WaitingForUser -> {
                        output.write(handshake.publicKeyMessage().encode())
                        output.flush()
                    }
                    is HandshakeStep.Failed -> {
                        throw IOException("Fallo en handshake ADB: ${step.reason}")
                    }
                    is HandshakeStep.Idle -> Unit
                    is HandshakeStep.Connected -> break
                }

                val headerBytes = ByteArray(AdbMessage.HEADER_SIZE)
                input.readFully(headerBytes)
                val header = AdbMessage.parseHeader(headerBytes)
                    ?: throw IOException("Cabecera ADB invalida durante handshake.")

                val payload = ByteArray(header.payloadLength)
                if (header.payloadLength > 0) {
                    input.readFully(payload)
                }

                step = handshake.onMessage(header, payload)
            }

            val localId = localIdCounter.getAndIncrement()
            val openMsg = AdbMessage.open(localId, "exec:$command")
            output.write(openMsg.encode())
            output.flush()

            val openHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
            input.readFully(openHeaderBytes)
            val openHeader = AdbMessage.parseHeader(openHeaderBytes)
                ?: throw IOException("Cabecera ADB invalida al abrir stream.")

            if (openHeader.payloadLength > 0) {
                input.readFully(ByteArray(openHeader.payloadLength))
            }

            if (openHeader.command == AdbCommand.CLSE) {
                throw IOException("adbd rechazo abrir el stream para el comando.")
            }

            if (openHeader.command != AdbCommand.OKAY) {
                throw IOException("Se esperaba OKAY al abrir stream, llego ${openHeader.command}")
            }

            val remoteId = openHeader.arg0
            val outputBuffer = StringBuilder()

            while (true) {
                val msgHeaderBytes = ByteArray(AdbMessage.HEADER_SIZE)
                try {
                    input.readFully(msgHeaderBytes)
                } catch (e: IOException) {
                    break
                }
                val msgHeader = AdbMessage.parseHeader(msgHeaderBytes) ?: break

                val msgPayload = ByteArray(msgHeader.payloadLength)
                if (msgHeader.payloadLength > 0) {
                    input.readFully(msgPayload)
                }

                when (msgHeader.command) {
                    AdbCommand.WRTE -> {
                        outputBuffer.append(String(msgPayload))
                        val ack = AdbMessage(AdbCommand.OKAY, localId, remoteId)
                        output.write(ack.encode())
                        output.flush()
                    }
                    AdbCommand.CLSE -> {
                        val closeAck = AdbMessage(AdbCommand.CLSE, localId, remoteId)
                        runCatching {
                            output.write(closeAck.encode())
                            output.flush()
                        }
                        break
                    }
                    AdbCommand.OKAY -> Unit
                    else -> Unit
                }
            }

            val text = outputBuffer.toString()
            return AdbExecResult(output = text, exitCode = 0, isError = false)
        } catch (e: SocketTimeoutException) {
            throw IOException("Timeout al comunicarse con adbd ($host:$port).", e)
        } finally {
            runCatching { socket.close() }
        }
    }
}

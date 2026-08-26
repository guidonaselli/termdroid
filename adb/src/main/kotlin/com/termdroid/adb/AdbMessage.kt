package com.termdroid.adb

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

enum class AdbCommand(val value: Int) {
    CNXN(0x4e584e43),
    AUTH(0x48545541),
    OPEN(0x4e45504f),
    OKAY(0x59414b4f),
    CLSE(0x45534c43),
    WRTE(0x45545257),
    ;

    companion object {
        fun of(value: Int): AdbCommand? = entries.firstOrNull { it.value == value }
    }
}

data class AdbMessage(
    val command: AdbCommand,
    val arg0: Int = 0,
    val arg1: Int = 0,
    val payload: ByteArray = ByteArray(0),
) {
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(command.value)
        buffer.putInt(arg0)
        buffer.putInt(arg1)
        buffer.putInt(payload.size)
        buffer.putInt(checksum(payload))
        buffer.putInt(command.value.inv())
        buffer.put(payload)
        return buffer.array()
    }

    override fun equals(other: Any?): Boolean =
        other is AdbMessage &&
            command == other.command &&
            arg0 == other.arg0 &&
            arg1 == other.arg1 &&
            payload.contentEquals(other.payload)

    override fun hashCode(): Int =
        (((command.hashCode() * 31 + arg0) * 31 + arg1) * 31) + payload.contentHashCode()

    companion object {
        const val HEADER_SIZE = 24
        const val VERSION = 0x01000001
        const val MAX_PAYLOAD = 256 * 1024

        const val AUTH_TOKEN = 1
        const val AUTH_SIGNATURE = 2
        const val AUTH_RSAPUBLICKEY = 3

        fun checksum(payload: ByteArray): Int =
            CRC32().apply { update(payload) }.value.toInt()

        fun connect(banner: String = "host::termdroid\u0000"): AdbMessage =
            AdbMessage(AdbCommand.CNXN, VERSION, MAX_PAYLOAD, banner.toByteArray())

        fun signature(firmado: ByteArray): AdbMessage =
            AdbMessage(AdbCommand.AUTH, AUTH_SIGNATURE, 0, firmado)

        fun publicKey(clave: ByteArray): AdbMessage =
            AdbMessage(AdbCommand.AUTH, AUTH_RSAPUBLICKEY, 0, clave)

        fun open(localId: Int, destino: String): AdbMessage =
            AdbMessage(AdbCommand.OPEN, localId, 0, (destino + "\u0000").toByteArray())

        /** Lee la cabecera. Devuelve null si no es una cabecera de ADB valida. */
        fun parseHeader(bytes: ByteArray): AdbHeader? {
            if (bytes.size < HEADER_SIZE) return null
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val command = b.int
            val arg0 = b.int
            val arg1 = b.int
            val length = b.int
            val crc = b.int
            val magic = b.int

            if (magic != command.inv()) return null
            if (length < 0 || length > MAX_PAYLOAD) return null
            val known = AdbCommand.of(command) ?: return null

            return AdbHeader(known, arg0, arg1, length, crc)
        }
    }
}

data class AdbHeader(
    val command: AdbCommand,
    val arg0: Int,
    val arg1: Int,
    val payloadLength: Int,
    val payloadChecksum: Int,
) {
    fun matches(payload: ByteArray): Boolean =
        payload.size == payloadLength && AdbMessage.checksum(payload) == payloadChecksum
}

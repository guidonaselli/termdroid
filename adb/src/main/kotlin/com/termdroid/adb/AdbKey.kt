package com.termdroid.adb

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class AdbKey(val keyPair: KeyPair) {

    val publicKeyBytes: ByteArray by lazy { encodePublicKey(keyPair.public as RSAPublicKey) }

    fun publicKeyForAdb(nombre: String = "termdroid@android"): ByteArray {
        val b64 = Base64.getEncoder().encodeToString(publicKeyBytes)
        return "$b64 $nombre\u0000".toByteArray()
    }

    fun sign(token: ByteArray): ByteArray =
        Signature.getInstance("NONEwithRSA").run {
            initSign(keyPair.private as RSAPrivateKey)
            update(SHA1_DIGEST_PREFIX + token)
            sign()
        }

    companion object {
        const val KEY_SIZE = 2048
        const val MODULUS_BYTES = KEY_SIZE / 8
        const val MODULUS_WORDS = MODULUS_BYTES / 4
        const val PUBLIC_KEY_BYTES = 4 + 4 + MODULUS_BYTES + MODULUS_BYTES + 4

        private val SHA1_DIGEST_PREFIX = byteArrayOf(
            0x00, 0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b,
            0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14,
        )

        fun generate(): AdbKey = AdbKey(
            KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair(),
        )

        fun encodePublicKey(publica: RSAPublicKey): ByteArray {
            val n = publica.modulus
            val r32 = BigInteger.ZERO.setBit(32)
            val r = BigInteger.ZERO.setBit(KEY_SIZE)
            val rr = r.modPow(BigInteger.TWO, n)
            val n0inv = n.mod(r32).modInverse(r32).let { r32.subtract(it) }

            val buffer = ByteBuffer.allocate(PUBLIC_KEY_BYTES).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(MODULUS_WORDS)
            buffer.putInt(n0inv.toInt())
            putLittleEndian(buffer, n)
            putLittleEndian(buffer, rr)
            buffer.putInt(publica.publicExponent.toInt())
            return buffer.array()
        }

        private fun putLittleEndian(buffer: ByteBuffer, valor: BigInteger) {
            val bytes = valor.toByteArray()
            val sinSigno = if (bytes.size > MODULUS_BYTES) {
                bytes.copyOfRange(bytes.size - MODULUS_BYTES, bytes.size)
            } else {
                ByteArray(MODULUS_BYTES - bytes.size) + bytes
            }
            for (i in sinSigno.indices.reversed()) buffer.put(sinSigno[i])
        }
    }
}

package com.termdroid.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda la credencial de la API. */
class SecretStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray())
        // El IV va junto al texto cifrado.
        val blob = cipher.iv + encrypted
        prefs.edit().putString(name, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
            String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES))
        }.getOrElse {
            // Dato indescifrable: se borra.
            remove(name)
            null
        }
    }

    fun has(name: String): Boolean = prefs.contains(name)

    fun remove(name: String) {
        prefs.edit().remove(name).apply()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "termdroid_secrets"
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "termdroid_secret_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128

        const val ANTHROPIC_API_KEY = "anthropic_api_key"
        const val ACTIVE_PROVIDER = "active_provider"
        const val GEMINI_TOKEN = "gemini_token"
        const val CLAUDE_TOKEN = "claude_token"
        const val OPENAI_TOKEN = "openai_token"
        const val CUSTOM_URL = "custom_url"
        const val CUSTOM_MODEL = "custom_model"
    }

    var activeProvider: String
        get() = get(ACTIVE_PROVIDER) ?: "GEMINI"
        set(value) = put(ACTIVE_PROVIDER, value)

    var geminiToken: String?
        get() = get(GEMINI_TOKEN)
        set(value) {
            if (value.isNullOrBlank()) remove(GEMINI_TOKEN) else put(GEMINI_TOKEN, value)
        }

    var claudeToken: String?
        get() = get(CLAUDE_TOKEN) ?: get(ANTHROPIC_API_KEY)
        set(value) {
            if (value.isNullOrBlank()) {
                remove(CLAUDE_TOKEN)
                remove(ANTHROPIC_API_KEY)
            } else {
                put(CLAUDE_TOKEN, value)
                put(ANTHROPIC_API_KEY, value)
            }
        }

    var openaiToken: String?
        get() = get(OPENAI_TOKEN)
        set(value) {
            if (value.isNullOrBlank()) remove(OPENAI_TOKEN) else put(OPENAI_TOKEN, value)
        }

    var customUrl: String?
        get() = get(CUSTOM_URL)
        set(value) {
            if (value.isNullOrBlank()) remove(CUSTOM_URL) else put(CUSTOM_URL, value)
        }

    var customModel: String?
        get() = get(CUSTOM_MODEL)
        set(value) {
            if (value.isNullOrBlank()) remove(CUSTOM_MODEL) else put(CUSTOM_MODEL, value)
        }

    var apiKey: String?
        get() = claudeToken
        set(value) {
            claudeToken = value
        }

    fun hasActiveCredentials(): Boolean = when (activeProvider) {
        "GEMINI" -> !geminiToken.isNullOrBlank()
        "CLAUDE" -> !claudeToken.isNullOrBlank()
        "OPENAI" -> !openaiToken.isNullOrBlank()
        "CUSTOM" -> true // Custom local endpoint puede no requerir token
        else -> false
    }
}

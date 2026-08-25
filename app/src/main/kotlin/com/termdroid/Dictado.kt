package com.termdroid

import android.content.Context
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

sealed interface ResultadoDictado {
    data class Texto(val texto: String) : ResultadoDictado
    data class Error(val mensaje: String) : ResultadoDictado
}

class Dictado(private val context: Context) {

    val disponible: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    private var recognizer: SpeechRecognizer? = null

    fun escuchar(onResultado: (ResultadoDictado) -> Unit) {
        if (!disponible) {
            onResultado(ResultadoDictado.Error("Este telefono no tiene reconocimiento de voz."))
            return
        }

        detener()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle?) {
                val texto = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                onResultado(
                    if (texto.isBlank()) ResultadoDictado.Error("No se entendio nada.")
                    else ResultadoDictado.Texto(texto),
                )
                detener()
            }

            override fun onError(error: Int) {
                onResultado(ResultadoDictado.Error(descripcionDeError(error)))
                detener()
            }

            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })

        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag()),
        )
    }

    fun detener() {
        recognizer?.destroy()
        recognizer = null
    }

    companion object {
        fun descripcionDeError(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Fallo el audio."
            SpeechRecognizer.ERROR_CLIENT -> "Fallo el reconocedor."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de microfono."
            SpeechRecognizer.ERROR_NETWORK -> "Sin conexion para reconocer voz."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "La red tardo demasiado."
            SpeechRecognizer.ERROR_NO_MATCH -> "No se entendio nada."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocedor esta ocupado."
            SpeechRecognizer.ERROR_SERVER -> "El servicio de voz devolvio un error."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se escucho nada."
            else -> "No se pudo dictar."
        }
    }
}

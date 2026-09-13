package com.xnglo.bord

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * Hindi voice -> xi38 text, for the keyboard's mic key.
 *
 * IMEs can't show a runtime-permission dialog themselves (there's no
 * Activity context inside an InputMethodService window for the system
 * permission UI) -- so [hasPermission] just checks whether
 * RECORD_AUDIO is already granted, and the caller (XngloIME) is
 * expected to route the user to [SettingsActivity] to grant it there
 * if not.
 */
class MicVoiceInput(private val context: Context) {

    interface Callback {
        /** Called with the xi38-converted text once a final result comes back. */
        fun onXi38Result(xi38Text: String)
        /** Called for an interim (not-yet-final) result, if you want live preview -- xi38-converted already. */
        fun onXi38Partial(xi38Text: String) {}
        fun onListeningStateChanged(isListening: Boolean)
        fun onError(message: String) {}
    }

    private var recognizer: SpeechRecognizer? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(callback: Callback) {
        if (!hasPermission()) {
            callback.onError("Microphone permission not granted")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            callback.onError("Speech recognition isn't available on this device")
            return
        }

        stop() // clean up any previous session first

        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                callback.onListeningStateChanged(true)
            }

            override fun onResults(results: Bundle?) {
                val text = firstResult(results)
                if (text != null) {
                    callback.onXi38Result(DevanagariToXi38.hindiSentenceToXi38(text))
                }
                callback.onListeningStateChanged(false)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = firstResult(partialResults)
                if (text != null) {
                    callback.onXi38Partial(DevanagariToXi38.hindiSentenceToXi38(text))
                }
            }

            override fun onError(error: Int) {
                callback.onListeningStateChanged(false)
                callback.onError("Speech recognition error (code $error)")
            }

            override fun onEndOfSpeech() {
                callback.onListeningStateChanged(false)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        r.startListening(intent)
    }

    fun stop() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}

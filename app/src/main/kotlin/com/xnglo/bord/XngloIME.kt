package com.xnglo.bord

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.InputConnection
import java.util.Locale

class XngloIME : InputMethodService(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    
    // Define the custom key code you assigned to the microphone key in keys_xi38.xml
    // (Update this constant to match whatever integer android:codes value your mic key uses)
    private val KEY_CODE_MIC = -101 

    override fun onCreateInputView(): View {
        // Inflate your keyboard layout (which references keys_xi38.xml)
        val keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as? XngloKeyboardView // Replace with your actual keyboard view class/layout if different
        
        // Initialize SpeechRecognizer safely on the UI/service thread
        initializeSpeechRecognizer()

        return keyboardView ?: super.onCreateInputView()
    }

    private fun initializeSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(this@XngloIME)
            }
        }
    }

    // This method handles key presses dispatched from your keyboard view layout
    fun onKey(primaryCode: Int) {
        val ic: InputConnection? = currentInputConnection

        when (primaryCode) {
            KEY_CODE_MIC -> {
                // Trigger voice transcription when the mic key is pressed
                startListening()
            }
            else -> {
                // Handle regular character/action keys
                if (primaryCode == 32) {
                    ic?.commitText(" ", 1)
                } else if (primaryCode == 10) {
                    ic?.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                } else {
                    val codeChar = primaryCode.toChar()
                    ic?.commitText(codeChar.toString(), 1)
                }
            }
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
        }
        
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}

    override fun onError(error: Int) {
        // Handle potential recognition failures gracefully if needed
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0]
            
            // Format text output per your xi38 requirement and commit it to the focused field
            val finalOutput = "$spokenText xi38"
            
            val ic = currentInputConnection
            ic?.commitText(finalOutput, 1)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

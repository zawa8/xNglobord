package com.xnglo.bord

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.io.IOException

class XngloIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: XngloKeyboardView
    private lateinit var letterKeyboard: Keyboard
    private lateinit var numericKeyboard: Keyboard
    private lateinit var candidatesRow: LinearLayout
    private lateinit var rootView: View
    private var isNumericMode = false
    private var isNumericLocked = false
    private var lastModeSwitchTapTime = 0L

    private val currentWord = StringBuilder()
    private var selectedTypeface: android.graphics.Typeface? = null

    private var isShiftActive = false
    private var isCapsLock = false
    private var lastShiftTapTime = 0L

    private val spaceLongPressHandler = Handler(Looper.getMainLooper())
    private var spaceLongPressTriggered = false
    private val spaceLongPressRunnable = Runnable {
        spaceLongPressTriggered = true
        FontPickerPopup.show(this, rootView) { _ ->
            selectedTypeface = FontManager.getSelectedTypeface(this)
            keyboardView.setKeyTypeface(selectedTypeface)
            renderCandidates()
        }
    }

    private val letterLongPressHandler = Handler(Looper.getMainLooper())
    private var letterLongPressTriggered = false
    private var letterLongPressCode = -1
    private val letterLongPressRunnable = Runnable {
        letterLongPressTriggered = true
        val ic = currentInputConnection
        val upper = letterLongPressCode.toChar().uppercaseChar()
        if (ic != null) {
            ic.commitText(upper.toString(), 1)
            currentWord.append(upper)
            if (isShiftActive && !isCapsLock) {
                isShiftActive = false
                keyboardView.setShiftActive(false)
            }
            renderCandidates()
        }
    }

    private val commaLongPressHandler = Handler(Looper.getMainLooper())
    private var commaLongPressTriggered = false
    private val commaLongPressRunnable = Runnable {
        commaLongPressTriggered = true
        SymbolAltPopup.show(this, rootView, listOf(",", ":", ";","<",">","\"")) { symbol ->
            currentInputConnection?.commitText(symbol, 1)
            currentWord.setLength(0)
            renderCandidates()
        }
    }

    // Vosk fields
    private var speechService: SpeechService? = null
    private var voskModel: Model? = null

    // Devanagari to xi38 mapping (from hsciistr_file.ts)
    private val devanagariToXi38Array = arrayOf(
        "", "N", "N", ":", "xe", "x", "a", "_i", "_i", "_u", "_u", "ri", "li",
        "_e", "_e", "_e", "_e", "ao", "_o", "o", "ou",
        "k", "K", "g", "gh", "N", "c", "C", "z", "Z", "n", "t", "T", "d", "D", "n",
        "j", "J", "q", "Q", "n", "n", "p", "f", "b", "B", "m", "y", "r", "r", "l", "l",
        "l", "w", "S", "s", "s", "v", "oe", "ui", "", "!", "a", "i", "i", "u", "u",
        "ri", "r", "e", "e", "e", "ye", "o", "oe", "o", "ou", "", "", "ou", "om",
        "", "", "`", "'", "eei", "ui", "uui", "k", "K", "g", "z", "R", "R", "f", "y",
        "ri", "li", "li", "li", ".", ".", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9",
        "_", "__", "x", "xo", "xo", "xo", "ui", "ui", "q", "Z", "y", "n", "z", "?", "d", "b"
    )

    override fun onCreate() {
        super.onCreate()
        XngloDictionary.loadAll(this)
    }

    override fun onCreateInputView(): View {
        letterKeyboard = Keyboard(this, R.xml.keys_xi38)
        numericKeyboard = Keyboard(this, R.xml.keys_numeric)
        val root = layoutInflater.inflate(R.layout.keyboard_view, null) as ViewGroup
        keyboardView = root.findViewById(R.id.xnglo_keyboard_view)
        candidatesRow = root.findViewById(R.id.candidates_row)
        keyboardView.keyboard = letterKeyboard
        keyboardView.setOnKeyboardActionListener(this)
        keyboardView.isPreviewEnabled = false
        rootView = root
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isNumericMode = false
        isNumericLocked = false
        isShiftActive = false
        isCapsLock = false
        keyboardView.keyboard = letterKeyboard
        keyboardView.setShiftActive(false)
        currentWord.setLength(0)
        selectedTypeface = FontManager.getSelectedTypeface(this)
        keyboardView.setKeyTypeface(selectedTypeface)
        renderCandidates()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        Toast.makeText(this, "onKey: $primaryCode", Toast.LENGTH_SHORT).show()

   // Handle mic before any currentInputConnection check
        if (primaryCode == MIC_CODE) {
            startVoiceInput()
            return
        }

        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                ic.deleteSurroundingText(1, 0)
                if (currentWord.isNotEmpty()) currentWord.setLength(currentWord.length - 1)
                renderCandidates()
            }
            KEYCODE_ENTER -> {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentWord.setLength(0)
                renderCandidates()
            }
            WORD_BOUNDARY_SPACE -> {
                if (spaceLongPressTriggered) {
                    spaceLongPressTriggered = false
                } else {
                    ic.commitText(" ", 1)
                    currentWord.setLength(0)
                    renderCandidates()
                }
            }
            WORD_BOUNDARY_COMMA -> {
                if (commaLongPressTriggered) {
                    commaLongPressTriggered = false
                } else {
                    ic.commitText(",", 1)
                    currentWord.setLength(0)
                    renderCandidates()
                    maybeAutoReturnFromOneShotNumeric()
                }
            }
            WORD_BOUNDARY_PERIOD -> {
                ic.commitText(".", 1)
                currentWord.setLength(0)
                renderCandidates()
                maybeAutoReturnFromOneShotNumeric()
            }
            MODE_SWITCH_CODE -> handleModeSwitchTap()
            SHIFT_CODE -> handleShiftTap()
            in HEX_LETTER_CODES -> {
                ic.commitText(primaryCode.toChar().toString(), 1)
                currentWord.setLength(0)
                renderCandidates()
                maybeAutoReturnFromOneShotNumeric()
            }
            in OPERATOR_LETTER_CODES -> {
                ic.commitText(primaryCode.toChar().toString(), 1)
                currentWord.setLength(0)
                renderCandidates()
                maybeAutoReturnFromOneShotNumeric()
            }
            else -> {
                if (primaryCode in LOWERCASE_A..LOWERCASE_Z && letterLongPressTriggered && primaryCode == letterLongPressCode) {
                    letterLongPressTriggered = false
                } else {
                    val useShift = isShiftActive && primaryCode in LOWERCASE_A..LOWERCASE_Z
                    val codeToCommit = if (useShift) primaryCode - CASE_OFFSET else primaryCode
                    if (useShift && !isCapsLock) {
                        isShiftActive = false
                        keyboardView.setShiftActive(false)
                    }
                    val committed = commitOrdinaryChar(ic, codeToCommit)
                    if (committed != null) {
                        currentWord.append(committed)
                    } else {
                        currentWord.setLength(0)
                    }
                    renderCandidates()
                    maybeAutoReturnFromOneShotNumeric()
                }
            }
        }
    }

    override fun onPress(primaryCode: Int) {
        if (primaryCode == WORD_BOUNDARY_SPACE) {
            spaceLongPressTriggered = false
            spaceLongPressHandler.postDelayed(spaceLongPressRunnable, LONG_PRESS_MS)
        } else if (primaryCode == WORD_BOUNDARY_COMMA) {
            commaLongPressTriggered = false
            commaLongPressHandler.postDelayed(commaLongPressRunnable, LONG_PRESS_MS)
        } else if (primaryCode in LOWERCASE_A..LOWERCASE_Z) {
            letterLongPressTriggered = false
            letterLongPressCode = primaryCode
            letterLongPressHandler.postDelayed(letterLongPressRunnable, LONG_PRESS_MS)
        }
    }

    override fun onRelease(primaryCode: Int) {
        if (primaryCode == WORD_BOUNDARY_SPACE) {
            spaceLongPressHandler.removeCallbacks(spaceLongPressRunnable)
        } else if (primaryCode == WORD_BOUNDARY_COMMA) {
            commaLongPressHandler.removeCallbacks(commaLongPressRunnable)
        } else if (primaryCode in LOWERCASE_A..LOWERCASE_Z) {
            letterLongPressHandler.removeCallbacks(letterLongPressRunnable)
        }
    }

    private fun handleShiftTap() {
        val now = System.currentTimeMillis()
        when {
            isCapsLock -> {
                isCapsLock = false
                isShiftActive = false
            }
            isShiftActive && (now - lastShiftTapTime) < CAPS_LOCK_DOUBLE_TAP_MS -> {
                isCapsLock = true
            }
            else -> {
                isShiftActive = !isShiftActive
            }
        }
        lastShiftTapTime = now
        keyboardView.setShiftActive(isShiftActive)
    }

    private fun handleModeSwitchTap() {
        val now = System.currentTimeMillis()
        val isDoubleTap = (now - lastModeSwitchTapTime) < CAPS_LOCK_DOUBLE_TAP_MS
        lastModeSwitchTapTime = now

        when {
            !isNumericMode -> {
                isNumericMode = true
                isNumericLocked = false
                keyboardView.keyboard = numericKeyboard
                keyboardView.setKeyTypeface(selectedTypeface)
            }
            isDoubleTap && !isNumericLocked -> {
                isNumericLocked = true
            }
            else -> {
                isNumericMode = false
                isNumericLocked = false
                keyboardView.keyboard = letterKeyboard
                keyboardView.setKeyTypeface(selectedTypeface)
            }
        }
        if (isShiftActive || isCapsLock) {
            isShiftActive = false
            isCapsLock = false
            keyboardView.setShiftActive(false)
        }
    }

    private fun maybeAutoReturnFromOneShotNumeric() {
        if (isNumericMode && !isNumericLocked) {
            isNumericMode = false
            keyboardView.keyboard = letterKeyboard
            keyboardView.setKeyTypeface(selectedTypeface)
        }
    }

    private fun commitOrdinaryChar(ic: InputConnection, primaryCode: Int): Char? {
        val ch = primaryCode.toChar()
        ic.commitText(ch.toString(), 1)
        return if (ch.isLetter()) ch else null
    }

    private fun renderCandidates() {
        candidatesRow.removeAllViews()
        val word = currentWord.toString()
        val suggestions = XngloDictionary.suggestionsFor(word)
        for (suggestion in suggestions) {
            val chip = TextView(this).apply {
                text = suggestion
                setTextColor(0xFFE2E8F0.toInt())
                textSize = 15f
                typeface = selectedTypeface
                setPadding(28, 8, 28, 8)
                setBackgroundResource(R.drawable.candidate_chip_background)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.marginEnd = 12
                layoutParams = params
                setOnClickListener { applyCandidate(suggestion) }
            }
            candidatesRow.addView(chip)
        }
    }

    private fun applyCandidate(word: String) {
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText(word, 1)
        currentWord.setLength(0)
        renderCandidates()
    }

    // --- Vosk Voice Input ---
    private fun startVoiceInput() {
        Toast.makeText(this, "Mic pressed", Toast.LENGTH_SHORT).show()

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission missing", Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (voskModel == null) {
                Toast.makeText(this, "Loading model...", Toast.LENGTH_SHORT).show()
                voskModel = Model("model-hi")
            }
            val recognizer = Recognizer(voskModel, 16000f)
            speechService = SpeechService(recognizer, 16000f)
            speechService?.startListening(object : org.vosk.android.RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) {
                    if (hypothesis != null) {
                        val xi38Text = convertVoskResultToXi38(hypothesis)
                        currentInputConnection?.commitText(xi38Text, 1)
                        currentWord.setLength(0)
                        renderCandidates()
                    }
                }
                override fun onFinalResult(hypothesis: String?) {
                    if (hypothesis != null) {
                        val xi38Text = convertVoskResultToXi38(hypothesis)
                        currentInputConnection?.commitText(xi38Text, 1)
                        currentWord.setLength(0)
                        renderCandidates()
                    }
                    speechService?.stop()
                }
                override fun onError(exception: Exception?) {
                    Toast.makeText(this@XngloIME, "Speech error: ${exception?.message}", Toast.LENGTH_LONG).show()
                    speechService?.stop()
                }
                override fun onTimeout() {
                    Toast.makeText(this@XngloIME, "Speech timeout", Toast.LENGTH_SHORT).show()
                    speechService?.stop()
                }
            })
            Toast.makeText(this, "Listening...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Vosk error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun convertVoskResultToXi38(jsonResult: String): String {
        try {
            val json = org.json.JSONObject(jsonResult)
            val text = json.optString("text", "")
            return if (text.any { it.code in 0x0900..0x097F }) {
                devanagariToXi38(text)
            } else {
                text
            }
        } catch (e: Exception) {
            return jsonResult
        }
    }

    private fun devanagariToXi38(input: String): String {
        var processed = input
            .replace(Regex("(^|[\\b\\s])क्ष"), "$1s")
            .replace(Regex("^क्ष"), "s")
            .replace("ज्ञ", "gy")

        val result = StringBuilder()
        for (ch in processed) {
            val codePoint = ch.code
            if (codePoint in 0x0900..0x097F) {
                val index = codePoint - 0x0900
                if (index < devanagariToXi38Array.size) {
                    result.append(devanagariToXi38Array[index])
                } else {
                    result.append(ch)
                }
            } else {
                result.append(ch)
            }
        }

        var xi38 = result.toString()
        xi38 = xi38
            .replace(Regex("^#S"), "S")
            .replace(Regex("(\\W)#S"), "$1S")
            .replace("#S", "kS")
            .replace(Regex("^_"), "")
            .replace(Regex("(\\W)_"), "$1")
            .replace(Regex("([aiueo])_"), "$1")
            .replace("_i", "yi")
            .replace("_e", "ye")
            .replace("_u", "xu")
            .replace(Regex("N$"), "")
            .replace(Regex("N(\\W)"), "$1")
            .replace("Nb", "mb")
            .replace("NB", "mB")
            .replace("Np", "mp")
            .replace("Nf", "mf")
            .replace(Regex("N(?![kKgG])"), "n")
        return xi38
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService = null
        voskModel?.close()
        voskModel = null
    }

    override fun onText(text: CharSequence?) {
        if (text.isNullOrEmpty()) return
        currentInputConnection?.commitText(text, 1)
        currentWord.setLength(0)
        renderCandidates()
        maybeAutoReturnFromOneShotNumeric()
    }
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    companion object {
        private const val KEYCODE_ENTER = -4
        private const val MODE_SWITCH_CODE = -2
        private const val SHIFT_CODE = -1
        private const val MIC_CODE = -100
        private const val WORD_BOUNDARY_SPACE = 32
        private const val WORD_BOUNDARY_COMMA = 44
        private const val WORD_BOUNDARY_PERIOD = 46
        private const val LONG_PRESS_MS = 500L
        private const val CAPS_LOCK_DOUBLE_TAP_MS = 350L
        private const val LOWERCASE_A = 97
        private const val LOWERCASE_Z = 122
        private const val CASE_OFFSET = 32
        private val HEX_LETTER_CODES: Set<Int> = setOf(76, 89, 86, 87, 80, 70)
        private val OPERATOR_LETTER_CODES: Set<Int> = setOf(69, 85, 73, 79, 77, 88)
    }
}
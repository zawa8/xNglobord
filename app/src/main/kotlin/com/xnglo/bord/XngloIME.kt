package com.xnglo.bord

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView

/**
 * IME service for the xNglobord xi38 keyboard (same idea as Gboard,
 * plus xNglo-specific features -- see xnglofont.md for the original
 * spec).
 *
 * Layout: [R.xml.keys_xi38], standard QWERTY key positions -- the 26
 * lowercase xi38 base graphemes are literally the ordinary Latin a-z
 * letters, so no relabeling was needed.
 *
 * The extra features:
 *   1. In-keyboard font picker: long-press the spacebar to open
 *      [FontPickerPopup], listing all 11 xNglo hscii fonts
 *      ([LocalFonts]). Selecting one re-themes the keyboard (key
 *      labels via [XngloKeyboardView.setKeyTypeface] + candidates
 *      strip text) immediately, and is remembered via [FontManager]
 *      for next time. Default: hindixv38 (xNglohindi). Also reachable
 *      from [SettingsActivity] via the gear icon.
 *   2. Long-press caps on every a-z key, including h: handled entirely
 *      in Kotlin (onPress/onRelease + a Handler timer, same pattern as
 *      the spacebar's long-press font picker below) -- NOT via
 *      android:popupCharacters. That attribute triggers the
 *      framework's own built-in mini-keyboard popup, a separate
 *      internal KeyboardView instance that [XngloKeyboardView]'s
 *      custom onDraw() doesn't reach, so it rendered as a plain white
 *      unthemed rectangle. Long-press 'a' commits 'A' directly instead.
 *
 * (h used to have a special h-suffix aspiration mode -- k+h -> K and
 * so on. Removed: long-press already reaches every capital letter the
 * same way as any other key, so the extra h-suffix behavior was
 * redundant. h is now a plain letter key like any other.)
 *
 * Auto-complete uses the shared xi38 dictionary ([XngloDictionary],
 * pooling every .txt file under assets/dictionaries/ across all xNglo
 * language variants) instead of an English word list -- the current
 * word is tracked in [currentWord] and the candidates strip above the
 * keyboard refreshes on every letter.
 *
 * Numbers/symbols: a "?123" key on the letter layout ([R.xml.keys_xi38])
 * switches to [R.xml.keys_numeric] (digits, common symbols, an "ABC"
 * key to switch back) via [toggleNumericMode]. Digits/symbols aren't
 * tracked as part of xi38 words.
 */
class XngloIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: XngloKeyboardView
    private lateinit var letterKeyboard: Keyboard
    private lateinit var numericKeyboard: Keyboard
    private lateinit var candidatesRow: LinearLayout
    private lateinit var rootView: View
    private var isNumericMode = false

    // The word currently being typed, since the last word boundary
    // (space/punctuation/enter/backspace-to-empty). Used to query
    // XngloDictionary and to know how much text to replace when a
    // candidate is tapped. Not a true composing-text span (no
    // underline) -- a reasonable first pass; upgrading to
    // ic.setComposingText() would be the natural next step.
    private val currentWord = StringBuilder()

    // Re-read each time the keyboard is shown (onStartInputView), so a
    // change made in SettingsActivity (or the in-keyboard font picker)
    // takes effect immediately / on next focus.
    private var selectedTypeface: android.graphics.Typeface? = null

    // Space-key long-press detection for the font picker.
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

    // a-z long-press detection for caps (replaces android:popupCharacters
    // -- see the class doc comment for why). Only one key can be
    // physically held at a time, so a single shared handler/code is
    // enough; letterLongPressCode tracks *which* key's timer is
    // pending/fired so onKey() can tell whether this specific release
    // was already handled by the long-press.
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
            renderCandidates()
        }
    }

    // Comma long-press: shows , : ; via SymbolAltPopup.
    private val commaLongPressHandler = Handler(Looper.getMainLooper())
    private var commaLongPressTriggered = false
    private val commaLongPressRunnable = Runnable {
        commaLongPressTriggered = true
        SymbolAltPopup.show(this, rootView, listOf(",", ":", ";")) { symbol ->
            currentInputConnection?.commitText(symbol, 1)
            currentWord.setLength(0)
            renderCandidates()
        }
    }

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
        // Disable KeyboardView's built-in key-preview bubble (the
        // enlarged-key popup shown on press/long-press) -- it's a
        // separate mechanism from android:popupCharacters (already
        // removed) and from our custom onDraw(), so it still rendered
        // unthemed/plain. We already show pressed-state feedback via
        // our own background drawing in XngloKeyboardView.onDraw().
        keyboardView.isPreviewEnabled = false
        rootView = root
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        isNumericMode = false
        keyboardView.keyboard = letterKeyboard
        currentWord.setLength(0)
        selectedTypeface = FontManager.getSelectedTypeface(this)
        keyboardView.setKeyTypeface(selectedTypeface)
        renderCandidates()
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
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
                    // The long-press already opened the font picker --
                    // don't also insert a space for this same press.
                    spaceLongPressTriggered = false
                } else {
                    ic.commitText(" ", 1)
                    currentWord.setLength(0)
                    renderCandidates()
                }
            }
            WORD_BOUNDARY_COMMA -> {
                if (commaLongPressTriggered) {
                    // The long-press already showed the , : ; popup --
                    // don't also insert a comma for this same press.
                    commaLongPressTriggered = false
                } else {
                    ic.commitText(",", 1)
                    currentWord.setLength(0)
                    renderCandidates()
                }
            }
            WORD_BOUNDARY_PERIOD -> {
                ic.commitText(".", 1)
                currentWord.setLength(0)
                renderCandidates()
            }
            MODE_SWITCH_CODE -> toggleNumericMode()
            in HEX_LETTER_CODES -> {
                // L Y V W P F (hex digits 10-15, xi38's own letters
                // instead of the standard A-F) -- not part of xi38
                // word tracking, same as digits/symbols.
                ic.commitText(primaryCode.toChar().toString(), 1)
                currentWord.setLength(0)
                renderCandidates()
            }
            else -> {
                if (primaryCode in LOWERCASE_A..LOWERCASE_Z && letterLongPressTriggered && primaryCode == letterLongPressCode) {
                    // The long-press already committed the capital --
                    // don't also commit the lowercase for this release.
                    letterLongPressTriggered = false
                } else {
                    val committed = commitOrdinaryChar(ic, primaryCode)
                    if (committed != null) {
                        currentWord.append(committed)
                    } else {
                        // Digits/symbols aren't part of xi38 word tracking.
                        currentWord.setLength(0)
                    }
                    renderCandidates()
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

    /** Switches between the letter keyboard and the numbers/symbols page (the "?123" / "ABC" key). */
    private fun toggleNumericMode() {
        isNumericMode = !isNumericMode
        keyboardView.keyboard = if (isNumericMode) numericKeyboard else letterKeyboard
        keyboardView.setKeyTypeface(selectedTypeface)
    }

    /** Commits a plain character and returns it, or null for non-letter codes we don't track as part of a word. */
    private fun commitOrdinaryChar(ic: InputConnection, primaryCode: Int): Char? {
        val ch = primaryCode.toChar()
        ic.commitText(ch.toString(), 1)
        return if (ch.isLetter()) ch else null
    }

    /** Rebuilds the candidate chip strip from [currentWord]'s dictionary matches. */
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

    /** Replaces the in-progress word in the text field with the tapped candidate. */
    private fun applyCandidate(word: String) {
        val ic = currentInputConnection ?: return
        if (currentWord.isNotEmpty()) {
            ic.deleteSurroundingText(currentWord.length, 0)
        }
        ic.commitText(word, 1)
        currentWord.setLength(0)
        renderCandidates()
    }

    // --- Unused OnKeyboardActionListener callbacks, required by the interface ---
    override fun onText(text: CharSequence?) {}
    override fun swipeLeft() {}
    override fun swipeRight() {}
    override fun swipeDown() {}
    override fun swipeUp() {}

    companion object {
        private const val KEYCODE_ENTER = -4
        private const val MODE_SWITCH_CODE = -2
        private const val WORD_BOUNDARY_SPACE = 32
        private const val WORD_BOUNDARY_COMMA = 44
        private const val WORD_BOUNDARY_PERIOD = 46
        private const val LONG_PRESS_MS = 500L
        private const val LOWERCASE_A = 97
        private const val LOWERCASE_Z = 122

        // L Y V W P F -- hex digits 10-15
        private val HEX_LETTER_CODES: Set<Int> = setOf(76, 89, 86, 87, 80, 70)
    }
}

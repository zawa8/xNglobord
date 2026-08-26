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
 * plus three xNglo-specific features -- see xnglofont.md for the
 * original spec).
 *
 * Layout: [R.xml.keys_xi38], standard QWERTY key positions -- the 26
 * lowercase xi38 base graphemes are literally the ordinary Latin a-z
 * letters, so no relabeling was needed.
 *
 * The three extra features:
 *   1. In-keyboard font picker: long-press the spacebar to open
 *      [FontPickerPopup], listing all 11 xNglo hscii fonts
 *      ([LocalFonts]). Selecting one re-themes the keyboard (key
 *      labels via [XngloKeyboardView.setKeyTypeface] + candidates
 *      strip text) immediately, and is remembered via [FontManager]
 *      for next time. Default: hindixv38 (xNglohindi). Also reachable
 *      from [SettingsActivity] via the gear icon.
 *   2. Long-press caps on every a-z key: handled by KeyboardView
 *      natively via android:popupCharacters in keys_xi38.xml -- no
 *      code needed, the capital is the (only) popup option, so
 *      long-press 'a' types 'A' and so on.
 *   3. h-suffix aspiration, mode selectable in Settings
 *      ([AspirationPrefs]): typing h immediately after one of
 *      k/g/c/z/t/d/j/q/b/s either (a) ASPIRATED mode (default):
 *      deletes that letter and inserts its capital form (k->K, g->G,
 *      c->C, z->Z, t->T, d->D, j->J, q->Q, b->B, s->S) instead of a
 *      literal h, or (b) LITERAL mode: just types "h" normally, so
 *      k+h types "kh". Standalone h (not preceded by one of those 10)
 *      always types normally either way.
 *
 * Auto-complete uses the shared xi38 dictionary ([XngloDictionary],
 * pooling assets/dictionaries/*.txt across all xNglo language
 * variants) instead of an English word list -- the current word is
 * tracked in [currentWord] and the candidates strip above the
 * keyboard refreshes on every letter.
 */
class XngloIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: XngloKeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var candidatesRow: LinearLayout
    private lateinit var rootView: View

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

    override fun onCreate() {
        super.onCreate()
        XngloDictionary.loadAll(this)
    }

    override fun onCreateInputView(): View {
        keyboard = Keyboard(this, R.xml.keys_xi38)
        val root = layoutInflater.inflate(R.layout.keyboard_view, null) as ViewGroup
        keyboardView = root.findViewById(R.id.xnglo_keyboard_view)
        candidatesRow = root.findViewById(R.id.candidates_row)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        rootView = root
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.keyboard = keyboard
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
            WORD_BOUNDARY_COMMA, WORD_BOUNDARY_PERIOD -> {
                ic.commitText(primaryCode.toChar().toString(), 1)
                currentWord.setLength(0)
                renderCandidates()
            }
            LOWERCASE_H_CODE -> handleHKey(ic)
            else -> {
                val committed = commitOrdinaryChar(ic, primaryCode)
                if (committed != null) {
                    currentWord.append(committed)
                    renderCandidates()
                }
            }
        }
    }

    override fun onPress(primaryCode: Int) {
        if (primaryCode == WORD_BOUNDARY_SPACE) {
            spaceLongPressTriggered = false
            spaceLongPressHandler.postDelayed(spaceLongPressRunnable, SPACE_LONG_PRESS_MS)
        }
    }

    override fun onRelease(primaryCode: Int) {
        if (primaryCode == WORD_BOUNDARY_SPACE) {
            spaceLongPressHandler.removeCallbacks(spaceLongPressRunnable)
        }
    }

    /** Commits a plain character and returns it, or null for non-letter codes we don't track as part of a word. */
    private fun commitOrdinaryChar(ic: InputConnection, primaryCode: Int): Char? {
        val ch = primaryCode.toChar()
        ic.commitText(ch.toString(), 1)
        return if (ch.isLetter()) ch else null
    }

    /**
     * h-suffix handling, per the Settings > aspiration-mode choice
     * ([AspirationPrefs]):
     *   - ASPIRATED (default): if the character right before the
     *     cursor is one of the 10 aspirable consonants, swap it for
     *     its capital form instead of inserting "h".
     *   - LITERAL: always just insert "h" normally (k+h types "kh").
     * Either way, the resulting character(s) stay part of
     * [currentWord] for dictionary lookup.
     */
    private fun handleHKey(ic: InputConnection) {
        val mode = AspirationPrefs.getMode(this)
        val prevChar = ic.getTextBeforeCursor(1, 0)?.toString()?.lastOrNull()
        val aspirated = if (mode == AspirationMode.ASPIRATED) prevChar?.let { ASPIRATION_MAP[it] } else null

        if (aspirated != null) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(aspirated.toString(), 1)
            if (currentWord.isNotEmpty()) {
                currentWord.setLength(currentWord.length - 1)
                currentWord.append(aspirated)
            }
        } else {
            ic.commitText("h", 1)
            currentWord.append('h')
        }
        renderCandidates()
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
        private const val LOWERCASE_H_CODE = 104
        private const val WORD_BOUNDARY_SPACE = 32
        private const val WORD_BOUNDARY_COMMA = 44
        private const val WORD_BOUNDARY_PERIOD = 46
        private const val SPACE_LONG_PRESS_MS = 500L

        // k g c z t d j q b s -> K G C Z T D J Q B S
        private val ASPIRATION_MAP: Map<Char, Char> = mapOf(
            'k' to 'K', 'g' to 'G', 'c' to 'C', 'z' to 'Z', 't' to 'T',
            'd' to 'D', 'j' to 'J', 'q' to 'Q', 'b' to 'B', 's' to 'S'
        )
    }
}

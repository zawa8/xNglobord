package com.xnglo.bord

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.LinearLayout
import android.widget.TextView

/**
 * IME service for the xNglobord xi38 keyboard.
 *
 * Layout: [R.xml.keys_xi38], standard QWERTY key positions -- the 26
 * lowercase xi38 base graphemes are literally the ordinary Latin a-z
 * letters, so no relabeling was needed.
 *
 * Spec behaviors implemented here (not in the XML):
 *   1. h-suffix aspiration: typing h immediately after one of
 *      k/g/c/z/t/d/j/q/b/s deletes that letter and inserts its
 *      aspirated capital form (k->K, g->G, c->C, z->Z, t->T, d->D,
 *      j->J, q->Q, b->B, s->S) instead of inserting a literal "h".
 *      Standalone h (not preceded by one of those 10) types normally.
 *   2. Long-press caps: handled by KeyboardView natively via each
 *      key's android:popupCharacters in keys_xi38.xml -- no code
 *      needed here, popup selections arrive through the same onKey().
 *   3. Shared xi38 dictionary auto-complete: [XngloDictionary] pools
 *      word lists from every xNglo language variant present in
 *      assets/dictionaries/ (xe38, xv38, ... more as they're seeded).
 *      The current word being typed is tracked in [currentWord]; on
 *      every letter the candidates strip above the keyboard is
 *      refreshed, and tapping a candidate replaces the in-progress
 *      word in the text field.
 *   4. Local font picker: [FontManager] + [SettingsActivity] mirror
 *      translet-xnglo's LocalFontPicker.tsx (same font list/order).
 *      The chosen font is applied to the candidates strip text.
 */
class XngloIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard
    private lateinit var candidatesRow: LinearLayout

    // The word currently being typed, since the last word boundary
    // (space/punctuation/enter/backspace-to-empty). Used to query
    // XngloDictionary and to know how much text to replace when a
    // candidate is tapped. Not a true composing-text span (no
    // underline) -- a reasonable first pass; upgrading to
    // ic.setComposingText() would be the natural next step.
    private val currentWord = StringBuilder()

    // Re-read each time the keyboard is shown (onStartInputView), so a
    // change made in SettingsActivity takes effect the next time the
    // user switches into a text field. null = use the default Typeface.
    private var selectedTypeface: android.graphics.Typeface? = null

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
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.keyboard = keyboard
        currentWord.setLength(0)
        selectedTypeface = FontManager.getSelectedTypeface(this)
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
            WORD_BOUNDARY_SPACE, WORD_BOUNDARY_COMMA, WORD_BOUNDARY_PERIOD -> {
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

    /** Commits a plain character and returns it, or null for non-letter codes we don't track as part of a word. */
    private fun commitOrdinaryChar(ic: InputConnection, primaryCode: Int): Char? {
        val ch = primaryCode.toChar()
        ic.commitText(ch.toString(), 1)
        return if (ch.isLetter()) ch else null
    }

    /**
     * h-suffix aspiration. If the character right before the cursor is
     * one of the 10 aspirable consonants, swap it for its aspirated
     * capital form instead of inserting "h". Otherwise insert "h" as
     * normal. Either way, the resulting letter stays part of
     * [currentWord] for dictionary lookup.
     */
    private fun handleHKey(ic: InputConnection) {
        val prevChar = ic.getTextBeforeCursor(1, 0)?.toString()?.lastOrNull()
        val aspirated = prevChar?.let { ASPIRATION_MAP[it] }
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
    override fun onPress(primaryCode: Int) {}
    override fun onRelease(primaryCode: Int) {}
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

        // k g c z t d j q b s -> K G C Z T D J Q B S
        private val ASPIRATION_MAP: Map<Char, Char> = mapOf(
            'k' to 'K', 'g' to 'G', 'c' to 'C', 'z' to 'Z', 't' to 'T',
            'd' to 'D', 'j' to 'J', 'q' to 'Q', 'b' to 'B', 's' to 'S'
        )
    }
}

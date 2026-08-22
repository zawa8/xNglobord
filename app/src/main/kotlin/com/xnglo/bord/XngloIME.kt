package com.xnglo.bord

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * IME service for the xNglobord xi38 keyboard.
 *
 * Layout: [R.xml.keys_xi38], standard QWERTY key positions -- the 26
 * lowercase xi38 base graphemes are literally the ordinary Latin a-z
 * letters, so no relabeling was needed.
 *
 * Two spec behaviors implemented here (not in the XML):
 *   1. h-suffix aspiration: typing h immediately after one of
 *      k/g/c/z/t/d/j/q/b/s deletes that letter and inserts its
 *      aspirated capital form (k->K, g->G, c->C, z->Z, t->T, d->D,
 *      j->J, q->Q, b->B, s->S) instead of inserting a literal "h".
 *      Standalone h (not preceded by one of those 10) types normally.
 *   2. Long-press caps: handled by KeyboardView natively via each
 *      key's android:popupCharacters in keys_xi38.xml -- no code
 *      needed here, popup selections arrive through the same onKey().
 *
 * Still to build (see readme.md spec):
 *   - the shared xi38 dictionary for auto-complete across all xNglo
 *     language variants (xe38, xv38, xb38, xp38, xg38, xo38, xj38,
 *     xk38, xt38, xmr38, xm38, xs38)
 */
class XngloIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard

    override fun onCreateInputView(): View {
        keyboard = Keyboard(this, R.xml.keys_xi38)
        keyboardView = layoutInflater.inflate(R.layout.keyboard_view, null) as KeyboardView
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyboardView.keyboard = keyboard
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val ic = currentInputConnection ?: return
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> ic.deleteSurroundingText(1, 0)
            KEYCODE_ENTER -> ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            LOWERCASE_H_CODE -> handleHKey(ic)
            else -> ic.commitText(primaryCode.toChar().toString(), 1)
        }
    }

    /**
     * h-suffix aspiration. If the character right before the cursor is
     * one of the 10 aspirable consonants, swap it for its aspirated
     * capital form instead of inserting "h". Otherwise insert "h" as
     * normal.
     */
    private fun handleHKey(ic: android.view.inputmethod.InputConnection) {
        val prevChar = ic.getTextBeforeCursor(1, 0)?.toString()?.lastOrNull()
        val aspirated = prevChar?.let { ASPIRATION_MAP[it] }
        if (aspirated != null) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(aspirated.toString(), 1)
        } else {
            ic.commitText("h", 1)
        }
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

        // k g c z t d j q b s -> K G C Z T D J Q B S
        private val ASPIRATION_MAP: Map<Char, Char> = mapOf(
            'k' to 'K', 'g' to 'G', 'c' to 'C', 'z' to 'Z', 't' to 'T',
            'd' to 'D', 'j' to 'J', 'q' to 'Q', 'b' to 'B', 's' to 'S'
        )
    }
}


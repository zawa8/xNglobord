package com.xnglo.bord

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * Scaffold IME service for the xNglobord xi38 keyboard.
 *
 * This is deliberately minimal: it proves the full pipeline works
 * (service registers with the system, shows up in the IME picker, can be
 * enabled, draws a keyboard, and sends keystrokes into a focused text
 * field). It uses [R.xml.keys_placeholder], a 2-row stand-in layout.
 *
 * Still to build (see readme.md spec):
 *   - the real 38-sound xi38 layout: x a i u e o N h c g k K g G c C z Z
 *     t T d D j J q Q n p f b B m y r l w s S v
 *   - h-suffix aspiration: pressing h right after k/g/c/z/t/d/j/q/b/s
 *     should turn the just-typed letter into its aspirated form
 *     (k->K, g->G, c->C, z->Z, t->T, d->D, j->J, q->Q, b->B, s->S)
 *     instead of inserting a literal "h"
 *   - long-press on a-z to show/insert the caps variant of that key
 *   - the shared xi38 dictionary for auto-complete across all xNglo
 *     language variants (xe38, xv38, xb38, xp38, xg38, xo38, xj38,
 *     xk38, xt38, xmr38, xm38, xs38)
 */
class XngloIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private lateinit var keyboardView: KeyboardView
    private lateinit var keyboard: Keyboard

    override fun onCreateInputView(): View {
        keyboard = Keyboard(this, R.xml.keys_placeholder)
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
            KEYCODE_ENTER -> ic.sendKeyEvent(
                android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER)
            )
            else -> ic.commitText(primaryCode.toChar().toString(), 1)
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
    }
}

package com.xnglo.bord

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet

/**
 * KeyboardView subclass that lets us set a Typeface for the key
 * labels. The stock android.inputmethodservice.KeyboardView draws
 * labels with a private Paint field and has no public typeface
 * setter, so this reaches it via reflection.
 *
 * If reflection ever fails (OEM keyboard skin, a future Android
 * version that renames the field, ...) this silently falls back to
 * the default system font instead of crashing the keyboard.
 */
class XngloKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    fun setKeyTypeface(typeface: Typeface?) {
        try {
            val paintField = KeyboardView::class.java.getDeclaredField("mPaint")
            paintField.isAccessible = true
            val paint = paintField.get(this) as? Paint
            paint?.typeface = typeface
            invalidateAllKeys()
        } catch (e: Exception) {
            // Fall back to default font rather than crash.
        }
    }
}

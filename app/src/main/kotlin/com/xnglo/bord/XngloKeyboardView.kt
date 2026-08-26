package com.xnglo.bord

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.util.TypedValue

/**
 * KeyboardView subclass that draws key backgrounds and labels itself,
 * so it can apply a custom Typeface to the labels.
 *
 * Earlier version of this reached into the stock
 * android.inputmethodservice.KeyboardView's private `mPaint` field via
 * reflection to set the typeface. That silently did nothing on a real
 * device: Android restricts reflective access to framework-private
 * fields for apps targeting API 28+ (this app targets 34), so
 * setAccessible()/getDeclaredField() either throws or is blocked, and
 * the old code's try/catch swallowed that -- the keyboard just kept
 * rendering in the system font with no visible error.
 *
 * Fix: don't touch the framework's internals at all. Override
 * onDraw() and render every key ourselves -- background rectangle +
 * centered label text -- using our own Paint, which we fully own and
 * can set any Typeface on.
 *
 * Known limitation: the long-press popup (showing the capital form of
 * a key) is drawn by a separate internal KeyboardView the framework
 * creates for that popup, which this override doesn't reach -- so
 * popups still render in the system font. Minor and transient; fixing
 * it would mean reimplementing the popup mechanism too.
 */
class XngloKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : KeyboardView(context, attrs) {

    private var customTypeface: Typeface? = null

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE2E8F0.toInt()
        textAlign = Paint.Align.CENTER
        textSize = spToPx(18f)
    }

    private val fillPaintNormal = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF111827.toInt() }
    private val fillPaintPressed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E293B.toInt() }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF374151.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
    }

    private val cornerRadius = dpToPx(6f)
    private val keyMargin = dpToPx(2f)

    fun setKeyTypeface(typeface: Typeface?) {
        customTypeface = typeface
        labelPaint.typeface = typeface ?: Typeface.DEFAULT
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val kb = keyboard
        if (kb == null) {
            super.onDraw(canvas)
            return
        }

        val rect = RectF()
        for (key in kb.keys) {
            rect.set(
                key.x.toFloat() + keyMargin,
                key.y.toFloat() + keyMargin,
                (key.x + key.width).toFloat() - keyMargin,
                (key.y + key.height).toFloat() - keyMargin
            )
            val fill = if (key.pressed) fillPaintPressed else fillPaintNormal
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fill)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, strokePaint)

            val label = key.label
            if (!label.isNullOrEmpty()) {
                val cx = rect.centerX()
                val cy = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) / 2f
                canvas.drawText(label.toString(), cx, cy, labelPaint)
            }
        }
    }

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

    private fun dpToPx(dp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics)
}

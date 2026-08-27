package com.xnglo.bord

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Small popup showing a few alternate symbols for a key, triggered on
 * long-press (currently used for comma: , : ;). Tapping one commits
 * it via onSelected and dismisses.
 *
 * Same PopupWindow-background fix as FontPickerPopup: without an
 * explicit transparent background drawable, PopupWindow falls back to
 * the platform's default opaque white window background.
 */
object SymbolAltPopup {

    fun show(context: Context, anchor: View, options: List<String>, onSelected: (String) -> Unit) {
        val density = context.resources.displayMetrics.density

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF111827.toInt())
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.elevation = 16f

        for (option in options) {
            val chip = TextView(context).apply {
                text = option
                setTextColor(0xFFE2E8F0.toInt())
                textSize = 30f // sp -- TextView.textSize is already sp-scaled
                gravity = Gravity.CENTER
                // These were raw pixels before (not density-scaled), so
                // they looked tiny on higher-density screens -- scale
                // by density for a real ~56dp-tall touch target.
                setPadding((28 * density).toInt(), (24 * density).toInt(), (28 * density).toInt(), (24 * density).toInt())
                minWidth = (56 * density).toInt()
                setOnClickListener {
                    popup.dismiss()
                    onSelected(option)
                }
            }
            row.addView(chip)
        }

        popup.showAtLocation(
            anchor,
            Gravity.BOTTOM or Gravity.START,
            (16 * density).toInt(),
            (230 * density).toInt()
        )
    }
}

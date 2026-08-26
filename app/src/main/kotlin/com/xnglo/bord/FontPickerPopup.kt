package com.xnglo.bord

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/**
 * The in-keyboard font picker triggered by long-pressing the spacebar
 * (see XngloIME's space long-press handling). A PopupWindow anchored
 * above the keyboard listing all LocalFonts.ALL options; tapping one
 * saves it via FontManager and re-themes the keyboard immediately.
 */
object FontPickerPopup {

    fun show(context: Context, anchor: View, onSelected: (LocalFontOption) -> Unit) {
        val currentId = FontManager.getSelectedFontId(context)

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111827.toInt())
            setPadding(8, 8, 8, 8)
        }

        for (option in LocalFonts.ALL) {
            val row = TextView(context).apply {
                text = option.displayName
                setTextColor(if (option.id == currentId) 0xFF38BDF8.toInt() else 0xFFE2E8F0.toInt())
                textSize = 17f
                setPadding(24, 20, 24, 20)
                gravity = Gravity.START
                try {
                    typeface = Typeface.createFromAsset(context.assets, "fonts/${option.assetFileName}")
                } catch (e: Exception) {
                    // Missing/corrupt font file -- show the label in the default typeface instead of crashing.
                }
            }
            list.addView(row)
        }

        val scroll = ScrollView(context).apply {
            addView(list)
            setBackgroundColor(Color.TRANSPARENT)
        }

        val popup = PopupWindow(
            scroll,
            ViewGroup.LayoutParams.MATCH_PARENT,
            (420 * context.resources.displayMetrics.density).toInt(),
            true
        )
        popup.isOutsideTouchable = true
        popup.elevation = 16f

        for (i in LocalFonts.ALL.indices) {
            val row = list.getChildAt(i) as TextView
            row.setOnClickListener {
                val option = LocalFonts.ALL[i]
                FontManager.setSelectedFontId(context, option.id)
                popup.dismiss()
                onSelected(option)
            }
        }

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0)
        // Position just above the anchor (the keyboard view).
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        popup.update(location[0], location[1] - popup.height, anchor.width, popup.height)
    }
}

package com.xnglo.bord

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/**
 * Popup listing recently copied text ([ClipboardHistory]). Tap an entry
 * to paste it; "clear all" empties the history. Same transparent
 * PopupWindow background fix as [SymbolAltPopup] / [FontPickerPopup].
 */
object ClipboardPopup {

    fun show(context: Context, anchor: View, onSelected: (String) -> Unit) {
        val density = context.resources.displayMetrics.density
        val clips = ClipboardHistory.all(context)

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF111827.toInt())
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val widthPx = (context.resources.displayMetrics.widthPixels - 32 * density).toInt()
        val scroll = ScrollView(context).apply { addView(column) }
        val popup = PopupWindow(scroll, widthPx, (240 * density).toInt(), true)
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.isOutsideTouchable = true
        popup.elevation = 16f

        fun addRow(label: String, color: Int, onClick: () -> Unit) {
            val tv = TextView(context).apply {
                text = label
                setTextColor(color)
                textSize = 15f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding((14 * density).toInt(), (12 * density).toInt(), (14 * density).toInt(), (12 * density).toInt())
                setBackgroundColor(0xFF1E293B.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = (4 * density).toInt() }
                setOnClickListener {
                    popup.dismiss()
                    onClick()
                }
            }
            column.addView(tv)
        }

        if (clips.isEmpty()) {
            val empty = TextView(context).apply {
                text = "clipboard khali hai -- pehle kuch copy karo"
                setTextColor(0xFF94A3B8.toInt())
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, (24 * density).toInt(), 0, (24 * density).toInt())
            }
            column.addView(empty)
        } else {
            for (clip in clips) {
                addRow(clip.replace('\n', ' '), 0xFFE2E8F0.toInt()) { onSelected(clip) }
            }
            addRow("clear all", 0xFFF87171.toInt()) { ClipboardHistory.clear(context) }
        }

        popup.showAtLocation(
            anchor,
            Gravity.BOTTOM or Gravity.START,
            (16 * density).toInt(),
            (230 * density).toInt()
        )
    }
}

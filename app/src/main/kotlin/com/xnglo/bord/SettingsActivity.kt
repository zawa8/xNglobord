package com.xnglo.bord

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/**
 * Settings for xNglobord. Currently just the local font picker --
 * matches translet-xnglo's LocalFontPicker.tsx (same font list/order,
 * same "System Font" default), swapping localStorage for
 * SharedPreferences.
 *
 * Registered as the IME's settingsActivity in AndroidManifest.xml, so
 * it's reachable via the gear icon next to xNglobord in
 * Settings > System > Languages & input > On-screen keyboard.
 */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())
    }

    private fun buildLayout(): ViewGroup {
        val padding = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(0xFF0B0F19.toInt())
        }

        val title = TextView(this).apply {
            text = "xNglobord Settings"
            setTextColor(0xFFE2E8F0.toInt())
            textSize = 20f
            setPadding(0, 0, 0, padding)
        }
        root.addView(title)

        val label = TextView(this).apply {
            text = "Keyboard font"
            setTextColor(0xFF64748B.toInt())
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        root.addView(label)

        val labels = mutableListOf("System Font")
        val ids = mutableListOf(LocalFonts.SYSTEM_FONT_ID)
        for (font in LocalFonts.ALL) {
            labels.add(font.displayName)
            ids.add(font.id)
        }

        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        spinner.adapter = adapter

        val currentId = FontManager.getSelectedFontId(this)
        val currentIndex = ids.indexOf(currentId).let { if (it < 0) 0 else it }
        spinner.setSelection(currentIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                FontManager.setSelectedFontId(this@SettingsActivity, ids[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(spinner)

        val note = TextView(this).apply {
            text = "Applies to the suggestion strip above the keyboard."
            setTextColor(0xFF64748B.toInt())
            textSize = 12f
            setPadding(0, padding, 0, 0)
        }
        root.addView(note)

        return root
    }
}

package com.xnglo.bord

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Settings for xNglobord: the font picker (same list as the
 * in-keyboard long-press-spacebar picker in XngloIME) and the mic
 * key's RECORD_AUDIO permission. Registered as the IME's
 * settingsActivity in AndroidManifest.xml, reachable via the gear
 * icon next to xNglobord in Settings > System > Languages & input >
 * On-screen keyboard -- also opened directly by XngloIME's mic key
 * (with EXTRA_REQUEST_MIC_PERMISSION set) when RECORD_AUDIO isn't
 * granted yet, since an IME's own window has no Activity context to
 * show the system permission dialog from.
 */
class SettingsActivity : Activity() {

    private lateinit var micStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildLayout())

        if (intent?.getBooleanExtra(EXTRA_REQUEST_MIC_PERMISSION, false) == true && !hasMicPermission()) {
            requestMicPermission()
        }
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

        addFontPicker(root, padding)
        addMicPermissionSection(root, padding)

        return root
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST_CODE) {
            updateMicStatusText()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::micStatusText.isInitialized) updateMicStatusText()
    }

    private fun updateMicStatusText() {
        micStatusText.text = if (hasMicPermission()) "\u2713 Microphone permission granted" else "\u2717 Microphone permission not granted"
        micStatusText.setTextColor(if (hasMicPermission()) 0xFF22C55E.toInt() else 0xFFF43F5E.toInt())
    }

    private fun addMicPermissionSection(root: LinearLayout, padding: Int) {
        val label = TextView(this).apply {
            text = "Mic key (voice \u2192 xi38)"
            setTextColor(0xFF64748B.toInt())
            textSize = 13f
            setPadding(0, padding, 0, 8)
        }
        root.addView(label)

        micStatusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }
        root.addView(micStatusText)
        updateMicStatusText()

        val grantButton = Button(this).apply {
            text = "Grant microphone permission"
            setOnClickListener { requestMicPermission() }
        }
        root.addView(grantButton)
    }

    private fun addFontPicker(root: LinearLayout, padding: Int) {
        val label = TextView(this).apply {
            text = "Keyboard font"
            setTextColor(0xFF64748B.toInt())
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }
        root.addView(label)

        val labels = LocalFonts.ALL.map { it.displayName }
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)

        val currentId = FontManager.getSelectedFontId(this)
        val currentIndex = LocalFonts.ALL.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
        spinner.setSelection(currentIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                FontManager.setSelectedFontId(this@SettingsActivity, LocalFonts.ALL[position].id)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        root.addView(spinner)

        val note = TextView(this).apply {
            text = "Also changeable from the keyboard itself: long-press the spacebar."
            setTextColor(0xFF64748B.toInt())
            textSize = 12f
            setPadding(0, 6, 0, padding)
        }
        root.addView(note)
    }

    companion object {
        const val EXTRA_REQUEST_MIC_PERMISSION = "com.xnglo.bord.EXTRA_REQUEST_MIC_PERMISSION"
        private const val MIC_PERMISSION_REQUEST_CODE = 1001
    }
}

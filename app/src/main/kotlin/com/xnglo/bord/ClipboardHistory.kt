package com.xnglo.bord

import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray

/**
 * Small persisted history of copied text (newest first, max [MAX_ITEMS]).
 * Fed by the IME's primary-clip listener; shown by [ClipboardPopup].
 */
object ClipboardHistory {

    private const val PREFS = "xnglo_clipboard"
    private const val KEY = "items"
    private const val MAX_ITEMS = 20

    private val items = mutableListOf<String>()
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) items.add(arr.getString(i))
        } catch (e: Exception) {
            items.clear()
        }
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    /** Adds [text] at the front (moving it up if already present). */
    fun add(context: Context, text: String) {
        ensureLoaded(context)
        if (text.isBlank()) return
        items.remove(text)
        items.add(0, text)
        while (items.size > MAX_ITEMS) items.removeAt(items.size - 1)
        save(context)
    }

    /** Reads whatever is on the system clipboard right now into the history. */
    fun captureCurrent(context: Context) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
            add(context, text)
        } catch (e: Exception) {
            // Clipboard access can be denied by the OS (e.g. no focus) -- ignore.
        }
    }

    fun all(context: Context): List<String> {
        ensureLoaded(context)
        return items.toList()
    }

    fun clear(context: Context) {
        ensureLoaded(context)
        items.clear()
        save(context)
    }
}

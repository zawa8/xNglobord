package com.xnglo.bord

import android.content.Context
import android.graphics.Typeface
import android.content.SharedPreferences

/**
 * Loads and caches Typefaces from assets/fonts/, and reads/writes the
 * user's selected font preference (mirrors translet-xnglo's
 * localStorage-based LocalFontPicker, using SharedPreferences instead).
 */
object FontManager {

    private val cache: MutableMap<String, Typeface?> = mutableMapOf()

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(LocalFonts.PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedFontId(context: Context): String =
        prefs(context).getString(LocalFonts.PREF_KEY_FONT, LocalFonts.SYSTEM_FONT_ID)
            ?: LocalFonts.SYSTEM_FONT_ID

    fun setSelectedFontId(context: Context, fontId: String) {
        prefs(context).edit().putString(LocalFonts.PREF_KEY_FONT, fontId).apply()
    }

    /** Returns the user's chosen Typeface, or null (meaning "use the default") if system font is selected or the file is missing/unreadable. */
    fun getSelectedTypeface(context: Context): Typeface? {
        val fontId = getSelectedFontId(context)
        if (fontId == LocalFonts.SYSTEM_FONT_ID) return null
        val option = LocalFonts.byId(fontId) ?: return null
        return loadTypeface(context, option.assetFileName)
    }

    private fun loadTypeface(context: Context, assetFileName: String): Typeface? {
        cache[assetFileName]?.let { return it }
        return try {
            val typeface = Typeface.createFromAsset(context.assets, "fonts/$assetFileName")
            cache[assetFileName] = typeface
            typeface
        } catch (e: Exception) {
            // Missing/corrupt font file shouldn't crash the keyboard --
            // just fall back to the default.
            null
        }
    }
}

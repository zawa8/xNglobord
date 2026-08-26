package com.xnglo.bord

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface

/**
 * Loads and caches Typefaces from assets/fonts/, and reads/writes the
 * user's selected font preference. Default is hindixv38 (xNglohindi)
 * per spec -- there's no "system font" fallback option, the picker
 * always has one of the 11 xNglo fonts selected.
 */
object FontManager {
    private const val PREFS_NAME = "xnglobord_prefs"
    private const val PREF_KEY_FONT = "user-local-font"

    private val cache: MutableMap<String, Typeface?> = mutableMapOf()

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSelectedFontId(context: Context): String =
        prefs(context).getString(PREF_KEY_FONT, LocalFonts.DEFAULT_FONT_ID)
            ?: LocalFonts.DEFAULT_FONT_ID

    fun setSelectedFontId(context: Context, fontId: String) {
        prefs(context).edit().putString(PREF_KEY_FONT, fontId).apply()
    }

    fun getSelectedOption(context: Context): LocalFontOption =
        LocalFonts.byId(getSelectedFontId(context)) ?: LocalFonts.ALL.first { it.id == LocalFonts.DEFAULT_FONT_ID }

    /** Returns the user's chosen Typeface, or null if the font asset is missing/unreadable (falls back to the system default rendering). */
    fun getSelectedTypeface(context: Context): Typeface? {
        val option = getSelectedOption(context)
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

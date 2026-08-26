package com.xnglo.bord

import android.content.Context

/**
 * The h-suffix setting from xnglofont.md's spec: two radio options --
 * literal (k+h types "kh") or aspirated (k+h types "K", the app's
 * original default behavior). Applies to the 10 aspirable letters:
 * k g c z t d j q b s -> K G C Z T D J Q B S.
 */
enum class AspirationMode { LITERAL, ASPIRATED }

object AspirationPrefs {
    private const val PREFS_NAME = "xnglobord_prefs"
    private const val PREF_KEY_MODE = "aspiration-mode"

    fun getMode(context: Context): AspirationMode {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY_MODE, AspirationMode.ASPIRATED.name)
        return try {
            AspirationMode.valueOf(stored ?: AspirationMode.ASPIRATED.name)
        } catch (e: IllegalArgumentException) {
            AspirationMode.ASPIRATED
        }
    }

    fun setMode(context: Context, mode: AspirationMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_MODE, mode.name)
            .apply()
    }
}

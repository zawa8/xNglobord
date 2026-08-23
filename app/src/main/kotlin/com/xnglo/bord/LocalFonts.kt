package com.xnglo.bord

/**
 * Same font list, same order, as translet-xnglo's
 * components/hsciifp/LocalFontPicker.tsx -- keep the two in sync.
 * `assetFileName` is the matching file in assets/fonts/.
 */
data class LocalFontOption(val id: String, val displayName: String, val assetFileName: String)

object LocalFonts {
    // please do not change order (matches LocalFontPicker.tsx)
    val ALL: List<LocalFontOption> = listOf(
        LocalFontOption("binaryfont", "binary(01)", "binaryfont.ttf"),
        LocalFontOption("eng52font", "e52", "eng52font.ttf"),
        LocalFontOption("xng52font", "xNgloiNgliS", "xng52font.ttf"),
        LocalFontOption("xv38fontid", "xNglovinqi", "xv38fontid.ttf"),
        LocalFontOption("xb38fontid", "xNglobNgali", "xb38fontid.ttf"),
        LocalFontOption("xj38fontid", "xNglojelugu", "xj38fontid.ttf"),
        LocalFontOption("xk38fontid", "xNgloknRa", "xk38fontid.ttf"),
        LocalFontOption("xp38fontid", "xNglopnzabi", "xp38fontid.ttf"),
        LocalFontOption("xm38fontid", "xNglomlyalxm", "xm38fontid.ttf"),
        LocalFontOption("xo38fontid", "xNglooriya", "xo38fontid.ttf"),
        LocalFontOption("xg38fontid", "xNgloguzraji", "xg38fontid.ttf"),
        LocalFontOption("xt38fontid", "xNglotmil", "xt38fontid.ttf"),
        LocalFontOption("xs38fontid", "xNglosinvla", "xs38fontid.ttf"),
        LocalFontOption("korian52font", "korian52", "korian52font.ttf"),
        LocalFontOption("russian52font", "russian52", "russian52font.ttf"),
    )

    const val SYSTEM_FONT_ID = "system"
    const val PREFS_NAME = "xnglobord_prefs"
    const val PREF_KEY_FONT = "user-local-font"

    fun byId(id: String): LocalFontOption? = ALL.find { it.id == id }
}

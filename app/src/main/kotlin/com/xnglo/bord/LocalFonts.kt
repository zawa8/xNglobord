package com.xnglo.bord

/**
 * Same font list, same order, as xnglofont's LocalFonts.kt and
 * translet-xnglo's components/hsciifp/LocalFontPicker.tsx, minus
 * binaryfont (excluded per instruction -- source is
 * zawa8/font/ttf/hscii/englosoftw8/englosoftw8utf/, all 14 files
 * except binarywenglosoftw8utf.ttf). Keep all three in sync.
 * `assetFileName` is the matching file in assets/fonts/.
 */
data class LocalFontOption(val id: String, val displayName: String, val assetFileName: String)

object LocalFonts {
    // please do not change order (matches LocalFontPicker.tsx / xnglofont's LocalFonts.kt, minus binaryfont)
    val ALL: List<LocalFontOption> = listOf(
        LocalFontOption("eNgliSxe38", "xNgloiNgliS", "eNgliSxe38.ttf"),
        LocalFontOption("hindixv38", "xNglovinqi", "hindixv38.ttf"),
        LocalFontOption("bengalixb38", "xNglobNgali", "bengalixb38.ttf"),
        LocalFontOption("jeluguxj38", "xNglojelugu", "jeluguxj38.ttf"),
        LocalFontOption("knRaxk38", "xNgloknRa", "knRaxk38.ttf"),
        LocalFontOption("pnzabixp38", "xNglopnzabi", "pnzabixp38.ttf"),
        LocalFontOption("mlyalxmxm38", "xNglomlyalxm", "mlyalxmxm38.ttf"),
        LocalFontOption("oriyaxo38", "xNglooriya", "oriyaxo38.ttf"),
        LocalFontOption("guzrajixg38", "xNgloguzraji", "guzrajixg38.ttf"),
        LocalFontOption("tmilxt38", "xNglotmil", "tmilxt38.ttf"),
        LocalFontOption("sinhlaxs38", "xNglosinvla", "sinhlaxs38.ttf"),
    )

    const val SYSTEM_FONT_ID = "system"
    const val PREFS_NAME = "xnglobord_prefs"
    const val PREF_KEY_FONT = "user-local-font"

    fun byId(id: String): LocalFontOption? = ALL.find { it.id == id }
}

package com.xnglo.bord

/**
 * Same font list, same order, as xnglofont's LocalFonts.kt and
 * translet-xnglo's components/hsciifp/LocalFontPicker.tsx -- the
 * 11-entry englosoftw8asc set per xnglofont.md, default xNglohindi.
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

    const val DEFAULT_FONT_ID = "hindixv38" // xNglohindi

    fun byId(id: String): LocalFontOption? = ALL.find { it.id == id }
}

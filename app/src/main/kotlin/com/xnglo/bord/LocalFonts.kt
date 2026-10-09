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
        LocalFontOption("xe38", "xNgloiNgliS", "xe38asc.ttf"),
        LocalFontOption("xh38", "xNglovinqi", "xh38asc.ttf"),
        LocalFontOption("xb38", "xNglobNgali", "xb38asc.ttf"),
        LocalFontOption("xj38", "xNglojelugu", "xj38asc.ttf"),
        LocalFontOption("xk38", "xNgloknRa", "xk38asc.ttf"),
        LocalFontOption("xp38", "xNglopnzabi", "xp38asc.ttf"),
        LocalFontOption("xm38", "xNglomlyalxm", "xm38asc.ttf"),
        LocalFontOption("xo38", "xNglooriya", "xo38asc.ttf"),
        LocalFontOption("xg38", "xNgloguzraji", "xg38asc.ttf"),
        LocalFontOption("xt38", "xNglotmil", "xt38asc.ttf"),
        LocalFontOption("xs38", "xNglosinvla", "xs38asc.ttf"),
    )

    const val DEFAULT_FONT_ID = "xh38" // xNglohindi

    fun byId(id: String): LocalFontOption? = ALL.find { it.id == id }
}

package com.xnglo.bord

/**
 * Font list for xNglobord fontpicker.
 * xi38asc (38-char) + xi52asc (52-char) series.
 * `assetFileName` matches file in assets/fonts/.
 */
data class LocalFontOption(val id: String, val displayName: String, val assetFileName: String)

object LocalFonts {
    val ALL: List<LocalFontOption> = listOf(
        // ── xi38asc series ──────────────────────────────
        LocalFontOption("xe38", "xNgloiNgliS",   "xe38asc.ttf"),
        LocalFontOption("xh38", "xNglovinqi",     "xh38asc.ttf"),
        LocalFontOption("xb38", "xNglobNgali",    "xb38asc.ttf"),
        LocalFontOption("xj38", "xNglojelugu",    "xj38asc.ttf"),
        LocalFontOption("xk38", "xNgloknRa",      "xk38asc.ttf"),
        LocalFontOption("xp38", "xNglopnzabi",    "xp38asc.ttf"),
        LocalFontOption("xm38", "xNglomlyalxm",   "xm38asc.ttf"),
        LocalFontOption("xo38", "xNglooriya",     "xo38asc.ttf"),
        LocalFontOption("xg38", "xNgloguzraji",   "xg38asc.ttf"),
        LocalFontOption("xt38", "xNglotmil",      "xt38asc.ttf"),
        LocalFontOption("xs38", "xNglosinvla",    "xs38asc.ttf"),
        // ── xi52asc series ──────────────────────────────
        LocalFontOption("xe52", "xNgloiNgliS x52", "xe52asc.ttf"),
        LocalFontOption("xh52", "xNglovinqi x52",   "xh52asc.ttf"),
        LocalFontOption("xb52", "xNglobNgali x52",  "xb52asc.ttf"),
        LocalFontOption("xj52", "xNglojelugu x52",  "xj52asc.ttf"),
        LocalFontOption("xk52", "xNgloknRa x52",    "xk52asc.ttf"),
        LocalFontOption("xp52", "xNglopnzabi x52",  "xp52asc.ttf"),
        LocalFontOption("xm52", "xNglomlyalxm x52", "xm52asc.ttf"),
        LocalFontOption("xo52", "xNglooriya x52",   "xo52asc.ttf"),
        LocalFontOption("xg52", "xNgloguzraji x52", "xg52asc.ttf"),
        LocalFontOption("xt52", "xNglotmil x52",    "xt52asc.ttf"),
        LocalFontOption("xs52", "xNglosinvla x52",  "xs52asc.ttf"),
    )

    const val DEFAULT_FONT_ID = "xh38"

    fun byId(id: String): LocalFontOption? = ALL.find { it.id == id }
}


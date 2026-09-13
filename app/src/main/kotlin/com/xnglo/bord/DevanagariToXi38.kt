package com.xnglo.bord

/**
 * Devanagari (Hindi) -> xi38 transliteration. Direct Kotlin port of
 * translet-xnglo's hindiToXngloVinqi() (lib/transliterate.ts) -- the
 * same logic already ported to TypeScript for xNglospiik's
 * lib/devanagariToXi38.ts. Keep all three in sync.
 *
 * Used by the mic key: SpeechRecognizer returns Hindi text in
 * Devanagari script, which this converts to xi38 before committing it
 * to the input field (see XngloIME's RecognitionListener).
 */
object DevanagariToXi38 {

    private val charMap: Map<String, String> = mapOf(
        "क्ष" to "S", "त्र" to "jr", "ज्ञ" to "gy", "अं" to "xN", "अः" to "x", "अ" to "x",
        "आ" to "xa", "ऑ" to "ao", "इ" to "_i", "ई" to "_i", "उ" to "_u", "ऊ" to "_u", "ऋ" to "ri", "ृ" to "r",
        "ए" to "_e", "ऐ" to "_e", "ओ" to "o", "औ" to "ou", "ख" to "K", "घ" to "G",
        "ङ" to "N", "ड़" to "R", "ढ़" to "R", "छ" to "C", "झ" to "Z", "ठ" to "T", "ढ" to "D", "थ" to "J",
        "ध" to "Q", "भ" to "B", "श" to "S", "क" to "k", "ग" to "g", "च" to "c",
        "ज" to "z", "ज़" to "z", "ञ" to "n", "ट" to "t", "ड" to "d", "ण" to "n", "त" to "j",
        "द" to "q", "न" to "n", "प" to "p", "फ" to "f", "ब" to "b", "म" to "m",
        "य" to "y", "र" to "r", "ल" to "l", "व" to "w", "ष" to "s", "स" to "s",
        "ह" to "v", "ा" to "a", "ि" to "i", "ी" to "i", "ु" to "u", "ू" to "u",
        "े" to "e", "ै" to "xi", "ो" to "o", "ौ" to "ou", "ं" to "N", "ः" to "", "्" to "", "ँ" to "N", "़" to ""
    )

    // Longest keys first, so multi-char sequences (क्ष, त्र, ज्ञ, अं, अः) match before their single-char prefixes.
    private val keysByLengthDesc: List<String> = charMap.keys.sortedByDescending { it.length }

    fun hindiToXi38(input: String): String {
        if (input.isEmpty()) return ""
        var text = input
        for (key in keysByLengthDesc) {
            text = text.replace(key, charMap.getValue(key))
        }

        text = text
            .replaceFirst(Regex("^_"), "")
            .replace(Regex("(\\W)_"), "$1")
            .replace("_i", "yi")
            .replace("_e", "ye")
            .replace("_u", "xu")

        text = text
            .replace(Regex("N$"), "")
            .replace(Regex("N(\\W)"), "$1")
            .replace(Regex("N([bB])"), "m$1")
            .replace(Regex("N(?![kKgG])"), "n")

        return text
    }

    /** Transliterates a full sentence/utterance word by word, keeping whitespace and punctuation intact. */
    fun hindiSentenceToXi38(input: String): String {
        if (input.isEmpty()) return ""
        return input
            .split(Regex("(?<=\\s)|(?=\\s)")) // split on whitespace boundaries, keeping the whitespace itself
            .joinToString("") { segment ->
                if (segment.isBlank()) segment else hindiToXi38(segment)
            }
    }
}

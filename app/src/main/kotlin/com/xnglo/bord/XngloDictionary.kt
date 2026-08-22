package com.xnglo.bord

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * The shared xi38 dictionary. One word-list file per xNglo language
 * variant lives in assets/dictionaries/ (xe38.txt = xNglo_eNgliS,
 * xv38.txt = xNglo_vinqi, and so on for xb38/xp38/xg38/xo38/xj38/xk38/
 * xt38/xmr38/xm38/xs38 as those get seeded). All of them share the same
 * 38-sound script, so lookups are pooled across every file that's
 * present -- a user typing doesn't pick a language first, suggestions
 * just come from whatever's in the dictionary.
 *
 * Add a new language: drop `assets/dictionaries/<code>.txt`, one word
 * per line. No code changes needed -- loadAll() picks up every .txt
 * file in that folder automatically.
 */
object XngloDictionary {

    private const val DICT_DIR = "dictionaries"

    // First letter -> words starting with it, sorted so shorter/more
    // common-looking entries surface first. Good enough for a first
    // pass; swap for real frequency data once that exists per-language.
    private val wordsByFirstChar: MutableMap<Char, MutableList<String>> = mutableMapOf()
    private var loaded = false

    fun loadAll(context: Context) {
        if (loaded) return
        loaded = true

        val assetManager = context.assets
        val files = assetManager.list(DICT_DIR) ?: return
        for (fileName in files) {
            if (!fileName.endsWith(".txt")) continue
            try {
                BufferedReader(InputStreamReader(assetManager.open("$DICT_DIR/$fileName"))).use { reader ->
                    reader.forEachLine { rawLine ->
                        val word = rawLine.trim()
                        if (word.isNotEmpty()) addWord(word)
                    }
                }
            } catch (e: Exception) {
                // Missing/unreadable language file shouldn't crash the
                // keyboard -- just skip it.
            }
        }
        for (list in wordsByFirstChar.values) {
            list.sortWith(compareBy({ it.length }, { it }))
        }
    }

    private fun addWord(word: String) {
        val key = word.first()
        val list = wordsByFirstChar.getOrPut(key) { mutableListOf() }
        if (!list.contains(word)) list.add(word)
    }

    /**
     * Case-sensitive prefix match (xi38 is case-sensitive: k and K are
     * different sounds), capped at [limit] results.
     */
    fun suggestionsFor(prefix: String, limit: Int = 5): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val candidates = wordsByFirstChar[prefix.first()] ?: return emptyList()
        return candidates.asSequence()
            .filter { it.startsWith(prefix) && it != prefix }
            .take(limit)
            .toList()
    }
}

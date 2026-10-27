package com.knowledgespike.ballbyball.parsecricsheet

import java.text.Normalizer
import java.util.Locale

/**
 * Utility functions for text normalization and phonetic transliteration folding.
 */
object TransliterationNormalizer {

    /**
     * Normalizes a given string by removing diacritics, punctuation, and unnecessary white spaces,
     * and converting the result to lowercase.
     *
     * @param name the input string to normalize
     * @return a normalized string with diacritics and punctuation removed,
     *         white spaces trimmed and reduced, and all characters in lowercase
     */
    fun normalize(name: String): String {
        val splitCamel = name.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        val decomposed = Normalizer.normalize(splitCamel, Normalizer.Form.NFD)
        val withoutDiacritics = decomposed.replace(Regex("\\p{M}+"), "")
        return withoutDiacritics
            .replace(Regex("[.,'-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    /**
     * Normalizes a given string by applying transliteration rules to simplify
     * and standardize its representation. This includes replacing substrings
     * and characters commonly found in various transliteration systems with
     * single, consistent representations, and collapsing consecutive duplicate
     * characters into a single instance.
     *
     * @param name The input string to be normalized. Typically, this will be
     *             a transliterated version of a name or word.
     * @return A normalized string with simplified transliteration and collapsed
     *         consecutive duplicate characters. Returns an empty string if the
     *         input string is empty or only contains non-alphanumeric characters.
     */
    fun normalizeTransliteration(name: String): String {
        var s = normalize(name)
        if (s.isEmpty()) return ""

        s = s.replace("ph", "f")
            .replace("dh", "d")
            .replace("th", "t")
            .replace("bh", "b")
            .replace("gh", "g")
            .replace("kh", "k")
            .replace("jh", "j")
            .replace("ch", "c")
            .replace("sh", "s")
            .replace("ee", "i")
            .replace("ea", "i")
            .replace("ie", "i")
            .replace("oo", "u")
            .replace("ou", "u")
            .replace("ow", "u")

        val sb = StringBuilder(s.length)
        for (ch in s) {
            val mapped = when (ch) {
                'w' -> 'v'
                'y' -> 'i'
                'q' -> 'k'
                'x' -> 's'
                'z' -> 's'
                'c' -> 'k'
                else -> ch
            }
            sb.append(mapped)
        }

        val collapsed = StringBuilder()
        var prev = '\u0000'
        for (i in 0 until sb.length) {
            val c = sb[i]
            if (c != prev) {
                collapsed.append(c)
                prev = c
            }
        }

        return collapsed.toString()
    }
}

package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.parsecricsheet.TextDistance.levenshteinDistance
import com.knowledgespike.ballbyball.parsecricsheet.TransliterationNormalizer.normalize
import com.knowledgespike.ballbyball.parsecricsheet.TransliterationNormalizer.normalizeTransliteration

/**
 * Phonetic and distance-based surname comparison strategies.
 */
object SurnameMatcher {

    /**
     * Determines whether two surnames are a fuzzy match based on their similarity.
     */
    fun isSurnameFuzzyMatch(sourceSurname: String, candidateSurname: String): Boolean {
        val src = normalize(sourceSurname)
        val cand = normalize(candidateSurname)
        if (src == cand) return true

        val minLen = minOf(src.length, cand.length)
        if (minLen <= 3) return false

        val dist = levenshteinDistance(src, cand)
        val maxLen = maxOf(src.length, cand.length)
        val similarity = (maxLen - dist).toDouble() / maxLen

        return when {
            minLen in 4..7 -> dist <= 1 && similarity >= 0.80
            else -> dist <= 2 && similarity >= 0.82
        }
    }

    /**
     * Determines whether two surnames match phonetically based on transliteration normalizations
     * and similarity measurements.
     */
    fun isSurnamePhoneticMatch(sourceSurname: String, candidateSurname: String): Boolean {
        val srcPhonetic = normalizeTransliteration(sourceSurname)
        val candPhonetic = normalizeTransliteration(candidateSurname)
        if (srcPhonetic.isEmpty() || candPhonetic.isEmpty()) return false
        if (srcPhonetic == candPhonetic) return true

        val minLen = minOf(srcPhonetic.length, candPhonetic.length)
        if (minLen <= 3) return false

        val dist = levenshteinDistance(srcPhonetic, candPhonetic)
        val maxLen = maxOf(srcPhonetic.length, candPhonetic.length)
        val similarity = (maxLen - dist).toDouble() / maxLen

        return when {
            minLen in 4..7 -> dist <= 1 && similarity >= 0.80
            else -> dist <= 1 && similarity >= 0.82
        }
    }

    /**
     * Determines whether two surnames are a relaxed fuzzy match (tolerance for distance <= 2 or similarity >= 0.70).
     */
    fun isSurnameRelaxedFuzzyMatch(sourceSurname: String, candidateSurname: String): Boolean {
        val src = normalize(sourceSurname)
        val cand = normalize(candidateSurname)
        if (src == cand) return true

        val minLen = minOf(src.length, cand.length)
        if (minLen <= 3) return false

        val dist = levenshteinDistance(src, cand)
        val maxLen = maxOf(src.length, cand.length)
        val similarity = (maxLen - dist).toDouble() / maxLen

        return dist <= 2 || similarity >= 0.70
    }
}

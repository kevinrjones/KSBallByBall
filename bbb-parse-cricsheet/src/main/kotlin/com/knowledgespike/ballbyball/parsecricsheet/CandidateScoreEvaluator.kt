package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.parsecricsheet.SurnameMatcher.isSurnamePhoneticMatch
import com.knowledgespike.ballbyball.parsecricsheet.SurnameMatcher.isSurnameRelaxedFuzzyMatch
import com.knowledgespike.ballbyball.parsecricsheet.TextDistance.levenshteinDistance
import com.knowledgespike.ballbyball.parsecricsheet.TextDistance.similarityRatio
import com.knowledgespike.ballbyball.parsecricsheet.TokenMatcher.checkTokensCloseEnoughMatch
import com.knowledgespike.ballbyball.parsecricsheet.TokenMatcher.tokenize
import com.knowledgespike.ballbyball.parsecricsheet.TransliterationNormalizer.normalize

/**
 * Heuristics and composite scoring evaluations for fallback / elimination player matching.
 */
object CandidateScoreEvaluator {

    /**
     * Determines whether a candidate player is close enough to the source name.
     */
    fun isCloseEnoughMatch(
        sourceName: String,
        candidate: StatsRosterPlayer
    ): Boolean {
        if (sourceName.isBlank()) return false

        val sourceTokens = tokenize(normalize(sourceName))
        if (sourceTokens.isEmpty()) return false

        val candidateFullNameTokens = tokenize(normalize(candidate.fullName))
        val candidateKnownAsTokens = candidate.knownAs?.let { tokenize(normalize(it)) }.orEmpty()
        val candidateSurnameTokens = candidate.surname?.let { tokenize(normalize(it)) }.orEmpty()
        val candidateExpandedTokens = candidate.expandedFullName?.let { tokenize(normalize(it)) }.orEmpty()

        if (checkTokensCloseEnoughMatch(sourceTokens, candidateFullNameTokens, candidateSurnameTokens)) {
            return true
        }

        if (candidateKnownAsTokens.isNotEmpty() &&
            checkTokensCloseEnoughMatch(sourceTokens, candidateKnownAsTokens, candidateSurnameTokens)
        ) {
            return true
        }

        if (candidateExpandedTokens.isNotEmpty() &&
            checkTokensCloseEnoughMatch(sourceTokens, candidateExpandedTokens, emptyList())
        ) {
            return true
        }

        return false
    }

    /**
     * Calculates a composite similarity score between a source name and a candidate player.
     */
    fun calculateSimilarityScore(sourceName: String, candidate: StatsRosterPlayer): Double {
        val srcNorm = normalize(sourceName)
        val candNorm = normalize(candidate.fullName)
        val candKnownAs = candidate.knownAs?.let { normalize(it) }
        val candExpanded = candidate.expandedFullName?.let { normalize(it) }

        val fullRatio = similarityRatio(srcNorm, candNorm)
        val knownAsRatio = candKnownAs?.let { similarityRatio(srcNorm, it) } ?: 0.0
        val expandedRatio = candExpanded?.let { similarityRatio(srcNorm, it) } ?: 0.0
        val maxFullRatio = maxOf(fullRatio, knownAsRatio, expandedRatio)

        val srcTokens = tokenize(srcNorm)
        val candTokens = tokenize(candNorm)

        val commonTokens = srcTokens.intersect(candTokens.toSet())
        val tokenScore = if (srcTokens.isNotEmpty() && candTokens.isNotEmpty()) {
            commonTokens.size.toDouble() / (srcTokens.size + candTokens.size - commonTokens.size)
        } else 0.0

        val srcSurname = srcTokens.lastOrNull().orEmpty()
        val candSurname = candidate.surname?.let { normalize(it) } ?: candTokens.lastOrNull().orEmpty()
        val surnameScore = if (srcSurname.isNotEmpty() && candSurname.isNotEmpty()) {
            similarityRatio(srcSurname, candSurname)
        } else 0.0

        val firstNameBonus = if (srcTokens.size > 1 && candTokens.size > 1) {
            val srcFirst = srcTokens.first()
            val candFirst = candTokens.first()
            if (srcFirst == candFirst ||
                isSurnameRelaxedFuzzyMatch(srcFirst, candFirst) ||
                isSurnamePhoneticMatch(srcFirst, candFirst) ||
                (levenshteinDistance(srcFirst, candFirst) <= 1 && minOf(srcFirst.length, candFirst.length) >= 4)
            ) {
                0.3
            } else if (srcFirst.firstOrNull() == candFirst.firstOrNull()) {
                0.1
            } else 0.0
        } else if (srcTokens.isNotEmpty() && candTokens.isNotEmpty() && srcTokens.first().firstOrNull() == candTokens.first().firstOrNull()) {
            0.1
        } else 0.0

        return maxOf(maxFullRatio, tokenScore * 0.5 + surnameScore * 0.3 + firstNameBonus)
    }
}

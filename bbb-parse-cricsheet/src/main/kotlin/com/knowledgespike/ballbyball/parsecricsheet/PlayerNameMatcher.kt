package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.parsecricsheet.TokenMatcher.matchesAdjacentExpandedNameTokens
import com.knowledgespike.ballbyball.parsecricsheet.TokenMatcher.matchesInitialsAndFuzzySurname
import com.knowledgespike.ballbyball.parsecricsheet.TokenMatcher.matchesInitialsAndSurname
import com.knowledgespike.ballbyball.parsecricsheet.TokenMatcher.tokenize

/**
 * Object responsible for matching player names from a given source input to a roster of players.
 * It utilizes normalization, tokenization, similarity metrics, and phonetic matching
 * to identify potential matches for player names.
 */
object PlayerNameMatcher {

    /**
     * Attempts to match a player's name from the provided roster to the given source name
     * using multiple stages of normalization, tokenization, and fuzzy matching techniques.
     *
     * @param sourceName The name string to match against the player roster.
     * @param roster A list of `StatsRosterPlayer` objects representing the player roster.
     * @return A `StatsRosterPlayer` object if a unique match is found;
     *         returns `null` if no match is found, if multiple matches exist,
     *         or if the source name or roster is invalid.
     */
    fun matchPlayer(
        sourceName: String,
        roster: List<StatsRosterPlayer>
    ): StatsRosterPlayer? {
        return compatibleCandidates(sourceName, roster).singleOrNull()
    }

    /**
     * Finds all candidates in [roster] compatible with [sourceName] across multi-stage matching tiers:
     * 1. Exact normalized match (fullName, knownAs, expandedFullName)
     * 2. Tokenized Initials and Surname match
     * 3. Fuzzy Surname match with Initials compatibility
     * 4. Transliteration-normalized Phonetic Surname match with Initials compatibility
     */
    fun compatibleCandidates(
        sourceName: String,
        roster: List<StatsRosterPlayer>
    ): List<StatsRosterPlayer> {
        if (sourceName.isBlank() || roster.isEmpty()) return emptyList()

        val normalizedSource = normalize(sourceName)
        if (normalizedSource.isEmpty()) return emptyList()

        // Stage 1: Exact normalized match on fullName or knownAs
        val exactMatches = roster.filter { player ->
            normalize(player.fullName) == normalizedSource ||
                (player.knownAs != null && normalize(player.knownAs) == normalizedSource) ||
                (player.expandedFullName != null && normalize(player.expandedFullName) == normalizedSource)
        }
        if (exactMatches.isNotEmpty()) return exactMatches

        // Stage 2: Tokenized Initials and Surname match
        val sourceTokens = tokenize(normalizedSource)
        if (sourceTokens.isEmpty()) return emptyList()

        val adjacentExpandedNameMatches = roster.filter { player ->
            player.expandedFullName
                ?.let { tokenize(normalize(it)) }
                ?.let { matchesAdjacentExpandedNameTokens(sourceTokens, it) } == true
        }
        if (adjacentExpandedNameMatches.isNotEmpty()) return adjacentExpandedNameMatches

        val initialsSurnameMatches = roster.filter { player ->
            matchesInitialsAndSurname(sourceTokens, player)
        }

        if (initialsSurnameMatches.isNotEmpty()) return initialsSurnameMatches

        // Stage 3: Fuzzy Surname match (Levenshtein distance & similarity ratio) with Initials compatibility
        val fuzzyMatches = roster.filter { player ->
            matchesInitialsAndFuzzySurname(sourceTokens, player, ::isSurnameFuzzyMatch)
        }
        if (fuzzyMatches.isNotEmpty()) return fuzzyMatches

        // Stage 4: Transliteration-normalized Phonetic Surname match with Initials compatibility
        return roster.filter { player ->
            matchesInitialsAndFuzzySurname(sourceTokens, player, ::isSurnamePhoneticMatch)
        }
    }

    /**
     * Attempts to find a "close enough" match from the roster for elimination or fallback matching
     * when high-confidence stages (Tiers 1-4) do not yield a match.
     *
     * @param sourceName The source name string.
     * @param roster The remaining available candidates in the match roster.
     * @return A `StatsRosterPlayer` if an unambiguous close-enough candidate is found; null otherwise.
     */
    fun matchCloseEnough(
        sourceName: String,
        roster: List<StatsRosterPlayer>
    ): StatsRosterPlayer? {
        if (sourceName.isBlank() || roster.isEmpty()) return null

        val candidates = roster.filter { candidate ->
            isCloseEnoughMatch(sourceName, candidate)
        }

        if (candidates.isEmpty()) {
            if (roster.size == 1) {
                val single = roster.first()
                val srcTokens = tokenize(normalize(sourceName))
                val candTokens = tokenize(normalize(single.fullName))
                val srcSurname = srcTokens.lastOrNull().orEmpty()
                val candSurname = tokenize(normalize(single.surname.orEmpty())).lastOrNull().takeIf { it?.isNotEmpty() == true } ?: candTokens.lastOrNull().orEmpty()

                // Guard against short surnames (<= 3 chars) mismatching (e.g. Roy vs Ray)
                if (srcSurname.length <= 3 && candSurname.length <= 3 && srcSurname != candSurname) {
                    return null
                }

                val srcInitial = srcTokens.firstOrNull()?.firstOrNull { it.isLetter() }
                val candInitial = candTokens.firstOrNull()?.firstOrNull { it.isLetter() }
                if (srcInitial != null && candInitial != null && (srcInitial == candInitial || isInitialsCompatible(srcInitial.toString(), candInitial.toString()))) {
                    val ratio = maxOf(
                        similarityRatio(normalize(sourceName), normalize(single.fullName)),
                        single.knownAs?.let { similarityRatio(normalize(sourceName), normalize(it)) } ?: 0.0,
                        single.expandedFullName?.let { similarityRatio(normalize(sourceName), normalize(it)) } ?: 0.0
                    )
                    if (ratio >= 0.40 || (srcTokens.size > 1 && candTokens.size > 1)) {
                        return single
                    }
                }
            }
            return null
        }
        if (candidates.size == 1) return candidates.first()

        val scored = candidates.map { it to calculateSimilarityScore(sourceName, it) }
            .sortedByDescending { it.second }

        val top = scored[0]
        val second = scored[1]

        if (top.second - second.second >= 0.15) {
            return top.first
        }

        return null
    }

    // Facade delegations for backward compatibility
    fun levenshteinDistance(a: String, b: String): Int = TextDistance.levenshteinDistance(a, b)
    fun similarityRatio(a: String, b: String): Double = TextDistance.similarityRatio(a, b)
    fun normalizeTransliteration(name: String): String = TransliterationNormalizer.normalizeTransliteration(name)
    fun isSurnameFuzzyMatch(sourceSurname: String, candidateSurname: String): Boolean = SurnameMatcher.isSurnameFuzzyMatch(sourceSurname, candidateSurname)
    fun isSurnamePhoneticMatch(sourceSurname: String, candidateSurname: String): Boolean = SurnameMatcher.isSurnamePhoneticMatch(sourceSurname, candidateSurname)
    fun isSurnameRelaxedFuzzyMatch(sourceSurname: String, candidateSurname: String): Boolean = SurnameMatcher.isSurnameRelaxedFuzzyMatch(sourceSurname, candidateSurname)
    fun isCloseEnoughMatch(sourceName: String, candidate: StatsRosterPlayer): Boolean = CandidateScoreEvaluator.isCloseEnoughMatch(sourceName, candidate)
    fun calculateSimilarityScore(sourceName: String, candidate: StatsRosterPlayer): Double = CandidateScoreEvaluator.calculateSimilarityScore(sourceName, candidate)
    internal fun normalize(name: String): String = TransliterationNormalizer.normalize(name)
    internal fun isInitialsCompatible(sInit: String, cInit: String): Boolean = TokenMatcher.isInitialsCompatible(sInit, cInit)
    internal fun extractInitials(parts: List<String>): List<String> = TokenMatcher.extractInitials(parts)
}

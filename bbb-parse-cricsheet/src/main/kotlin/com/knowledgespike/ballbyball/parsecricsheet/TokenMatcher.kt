package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.parsecricsheet.SurnameMatcher.isSurnameFuzzyMatch
import com.knowledgespike.ballbyball.parsecricsheet.SurnameMatcher.isSurnamePhoneticMatch
import com.knowledgespike.ballbyball.parsecricsheet.SurnameMatcher.isSurnameRelaxedFuzzyMatch
import com.knowledgespike.ballbyball.parsecricsheet.TextDistance.levenshteinDistance
import com.knowledgespike.ballbyball.parsecricsheet.TextDistance.similarityRatio
import com.knowledgespike.ballbyball.parsecricsheet.TransliterationNormalizer.normalize
import com.knowledgespike.ballbyball.parsecricsheet.TransliterationNormalizer.normalizeTransliteration

/**
 * Tokenization and multi-strategy matching logic for player names, initials, and token sequences.
 */
object TokenMatcher {

    /**
     * Splits the given normalized string into a list of non-blank substrings using spaces as delimiters.
     *
     * @param normalized The input string that has been normalized and needs to be tokenized.
     * @return A list of strings, where each string is a non-blank token extracted from the input.
     */
    fun tokenize(normalized: String): List<String> =
        normalized.split(' ').filter(String::isNotBlank)

    /**
     * Extracts a list of initials from the given list of strings.
     *
     * @param parts A list of strings from which initials are to be extracted.
     * @return A list of extracted initials, derived from the input list.
     */
    fun extractInitials(parts: List<String>): List<String> {
        if (parts.isEmpty()) return emptyList()
        val initials = mutableSetOf<String>()

        val perWord = parts.mapNotNull { it.firstOrNull() }.joinToString("")
        if (perWord.isNotEmpty()) initials.add(perWord)

        if (parts.size == 1) {
            initials.add(parts.first())
        }

        return initials.toList()
    }

    /**
     * Checks if [sub] is an ordered subsequence of [full].
     */
    fun isSubsequence(sub: String, full: String): Boolean {
        if (sub.isEmpty()) return true
        var subIdx = 0
        for (ch in full) {
            if (ch == sub[subIdx]) {
                subIdx++
                if (subIdx == sub.length) return true
            }
        }
        return false
    }

    /**
     * Checks compatibility between source and candidate initials.
     */
    fun isInitialsCompatible(sInit: String, cInit: String): Boolean {
        if (sInit.isEmpty() || cInit.isEmpty()) return false
        if (sInit == cInit) return true
        if (cInit.startsWith(sInit) || sInit.startsWith(cInit)) return true

        val sTrans = normalizeTransliteration(sInit)
        val cTrans = normalizeTransliteration(cInit)
        if (sTrans == cTrans || cTrans.startsWith(sTrans) || sTrans.startsWith(cTrans)) return true

        if (isSubsequence(sInit, cInit) || isSubsequence(cInit, sInit)) return true
        if (isSubsequence(sTrans, cTrans) || isSubsequence(cTrans, sTrans)) return true

        return false
    }

    /**
     * Compares two lists of first name parts and determines if there is a match based on full names
     * or matching initials.
     */
    fun matchFirstNamesOrInitials(
        sourceFirstParts: List<String>,
        candidateFirstParts: List<String>
    ): Boolean {
        if (sourceFirstParts.isEmpty() || candidateFirstParts.isEmpty()) return false

        if (sourceFirstParts == candidateFirstParts) {
            return true
        }

        val sourceInitialsList = extractInitials(sourceFirstParts)
        val candidateInitialsList = extractInitials(candidateFirstParts)

        for (sInit in sourceInitialsList) {
            for (cInit in candidateInitialsList) {
                if (isInitialsCompatible(sInit, cInit)) return true
            }
        }

        if (sourceFirstParts.all { part ->
                part in candidateFirstParts || candidateFirstParts.any { it.startsWith(part) }
            }
        ) {
            return true
        }

        return false
    }

    /**
     * Matches adjacent tokens in an expanded name window.
     */
    fun matchesAdjacentExpandedNameTokens(
        sourceTokens: List<String>,
        expandedNameTokens: List<String>
    ): Boolean {
        if (sourceTokens.size < 2) return false

        return expandedNameTokens.windowed(size = sourceTokens.size).any { candidateTokens ->
            candidateTokens.zip(sourceTokens).all { (candidateToken, sourceToken) ->
                candidateToken.startsWith(sourceToken)
            }
        }
    }

    /**
     * Matches initials against any expanded name token.
     */
    fun matchesInitialsAndAnyExpandedNameToken(
        sourceTokens: List<String>,
        expandedNameTokens: List<String>
    ): Boolean {
        if (sourceTokens.size < 2) return false

        val sourceSurname = sourceTokens.last()
        if (sourceSurname !in expandedNameTokens) return false

        return sourceTokens.dropLast(1).all { sourceGivenName ->
            expandedNameTokens.any { expandedNameToken ->
                expandedNameToken.startsWith(sourceGivenName)
            }
        }
    }

    /**
     * Matches reordered contracted tokens against expanded name.
     */
    fun matchesReorderedContractedExpandedName(
        sourceTokens: List<String>,
        expandedNameTokens: List<String>
    ): Boolean {
        if (sourceTokens.size < 2 || sourceTokens.any { it.length < 2 }) return false

        return sourceTokens.all { sourceToken ->
            expandedNameTokens.any { it == sourceToken } ||
                expandedNameTokens.windowed(size = 2).any { it.joinToString("") == sourceToken }
        }
    }

    /**
     * Checks if the source tokens match the candidate tokens based on specified matching rules.
     */
    fun checkTokensMatch(
        sourceTokens: List<String>,
        candidateTokens: List<String>,
        explicitSurnameTokens: List<String>
    ): Boolean {
        if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) return false

        val candidateSurnameTokensResolved = if (explicitSurnameTokens.isNotEmpty() && candidateTokens.containsAll(explicitSurnameTokens)) {
            explicitSurnameTokens
        } else {
            listOf(candidateTokens.last())
        }

        val surnameLength = candidateSurnameTokensResolved.size

        if (sourceTokens.size == 1) {
            val single = sourceTokens.first()
            val candidateSurname = candidateSurnameTokensResolved.joinToString(" ")
            return single == candidateSurname || single == candidateTokens.first() || single == candidateTokens.last()
        }

        if (candidateTokens.size == 1 || candidateTokens.size == surnameLength) {
            val candidateSurname = candidateSurnameTokensResolved.joinToString(" ")
            return sourceTokens.last() == candidateSurname
        }

        // Candidate with surname at end: [first..., surname...]
        if (sourceTokens.size > surnameLength && candidateTokens.size > surnameLength) {
            val sourceSurnameParts = sourceTokens.takeLast(surnameLength)
            val candidateSurnameParts = candidateTokens.takeLast(surnameLength)
            if (candidateSurnameParts == candidateSurnameTokensResolved && sourceSurnameParts == candidateSurnameTokensResolved) {
                val sourceFirstParts = sourceTokens.dropLast(surnameLength)
                val candidateFirstParts = candidateTokens.dropLast(surnameLength)
                if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                    return true
                }
            }
        }

        // Candidate with surname at start: [surname..., first...]
        if (sourceTokens.size > surnameLength && candidateTokens.size > surnameLength) {
            val sourceSurnameParts = sourceTokens.takeLast(surnameLength)
            val candidateSurnameParts = candidateTokens.take(surnameLength)
            if (candidateSurnameParts == candidateSurnameTokensResolved && sourceSurnameParts == candidateSurnameTokensResolved) {
                val sourceFirstParts = sourceTokens.dropLast(surnameLength)
                val candidateFirstParts = candidateTokens.drop(surnameLength)
                if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                    return true
                }
            }
        }

        val lastSourceToken = sourceTokens.last()
        val lastCandidateToken = candidateTokens.last()
        val firstCandidateToken = candidateTokens.first()
        val explicitSurname = candidateSurnameTokensResolved.joinToString(" ")

        if (lastCandidateToken == explicitSurname && lastSourceToken == lastCandidateToken && candidateTokens.size >= 2) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candidateFirstParts = candidateTokens.dropLast(1)
            if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                return true
            }
        }

        if (firstCandidateToken == explicitSurname && lastSourceToken == firstCandidateToken && candidateTokens.size >= 2) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candidateFirstParts = candidateTokens.drop(1)
            if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if the source tokens and candidate tokens match based on fuzzy logic with a given surname matcher.
     */
    fun checkTokensFuzzyMatch(
        sourceTokens: List<String>,
        candidateTokens: List<String>,
        explicitSurnameTokens: List<String>,
        surnameMatcher: (String, String) -> Boolean
    ): Boolean {
        if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) return false

        val candidateSurnameTokensResolved = if (explicitSurnameTokens.isNotEmpty() && candidateTokens.containsAll(explicitSurnameTokens)) {
            explicitSurnameTokens
        } else {
            listOf(candidateTokens.last())
        }

        val surnameLength = candidateSurnameTokensResolved.size

        if (sourceTokens.size == 1) {
            val single = sourceTokens.first()
            val candidateSurname = candidateSurnameTokensResolved.joinToString(" ")
            return surnameMatcher(single, candidateSurname) ||
                surnameMatcher(single, candidateTokens.first()) ||
                surnameMatcher(single, candidateTokens.last())
        }

        if (candidateTokens.size == 1 || candidateTokens.size == surnameLength) {
            val candidateSurname = candidateSurnameTokensResolved.joinToString(" ")
            return surnameMatcher(sourceTokens.last(), candidateSurname)
        }

        // Candidate with surname at end: [first..., surname...]
        if (sourceTokens.size > surnameLength && candidateTokens.size > surnameLength) {
            val sourceSurnameParts = sourceTokens.takeLast(surnameLength).joinToString(" ")
            val candidateSurnameParts = candidateTokens.takeLast(surnameLength).joinToString(" ")
            val explicitSurname = candidateSurnameTokensResolved.joinToString(" ")
            if (candidateSurnameParts == explicitSurname && surnameMatcher(sourceSurnameParts, explicitSurname)) {
                val sourceFirstParts = sourceTokens.dropLast(surnameLength)
                val candidateFirstParts = candidateTokens.dropLast(surnameLength)
                if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                    return true
                }
            }
        }

        // Candidate with surname at start: [surname..., first...]
        if (sourceTokens.size > surnameLength && candidateTokens.size > surnameLength) {
            val sourceSurnameParts = sourceTokens.takeLast(surnameLength).joinToString(" ")
            val candidateSurnameParts = candidateTokens.take(surnameLength).joinToString(" ")
            val explicitSurname = candidateSurnameTokensResolved.joinToString(" ")
            if (candidateSurnameParts == explicitSurname && surnameMatcher(sourceSurnameParts, explicitSurname)) {
                val sourceFirstParts = sourceTokens.dropLast(surnameLength)
                val candidateFirstParts = candidateTokens.drop(surnameLength)
                if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                    return true
                }
            }
        }

        val lastSourceToken = sourceTokens.last()
        val lastCandidateToken = candidateTokens.last()
        val firstCandidateToken = candidateTokens.first()
        val explicitSurname = candidateSurnameTokensResolved.joinToString(" ")

        if (lastCandidateToken == explicitSurname && lastSourceToken == lastCandidateToken && candidateTokens.size >= 2) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candidateFirstParts = candidateTokens.dropLast(1)
            if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                return true
            }
        }

        if (firstCandidateToken == explicitSurname && lastSourceToken == firstCandidateToken && candidateTokens.size >= 2) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candidateFirstParts = candidateTokens.drop(1)
            if (matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)) {
                return true
            }
        }

        return false
    }

    /**
     * Determines whether candidate tokens match source tokens under relaxed duck-typing rules.
     */
    fun checkTokensCloseEnoughMatch(
        sourceTokens: List<String>,
        candidateTokens: List<String>,
        explicitSurnameTokens: List<String>
    ): Boolean {
        if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) return false

        val candidateSurnameTokensResolved = if (explicitSurnameTokens.isNotEmpty() && candidateTokens.containsAll(explicitSurnameTokens)) {
            explicitSurnameTokens
        } else {
            listOf(candidateTokens.last())
        }

        val candidateSurname = candidateSurnameTokensResolved.joinToString(" ")
        val sourceSurname = sourceTokens.last()

        // Guard against short surnames (<= 3 chars) mismatching (e.g. Roy vs Ray)
        if (sourceSurname.length <= 3 && candidateSurname.length <= 3 && sourceSurname != candidateSurname) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candFirstParts = candidateTokens.dropLast(candidateSurnameTokensResolved.size)
            if (sourceFirstParts.none { it.length >= 4 && candFirstParts.contains(it) }) {
                return false
            }
        }

        // Single token in source name
        if (sourceTokens.size == 1) {
            val single = sourceTokens.first()
            if (single.length >= 4 && (candidateTokens.contains(single) || isSurnameRelaxedFuzzyMatch(single, candidateSurname))) {
                return true
            }
        }

        // Surname at end: [first..., surname...]
        if (sourceTokens.size > 1 && candidateTokens.size >= candidateSurnameTokensResolved.size) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candFirstParts = candidateTokens.dropLast(candidateSurnameTokensResolved.size)
            if (candFirstParts.isNotEmpty() && matchFirstNamesOrInitials(sourceFirstParts, candFirstParts)) {
                if (isSurnameRelaxedFuzzyMatch(sourceSurname, candidateSurname) ||
                    isSurnamePhoneticMatch(sourceSurname, candidateSurname)
                ) {
                    return true
                }
            }
        }

        // Surname at start: [surname..., first...]
        if (sourceTokens.size > 1 && candidateTokens.size >= candidateSurnameTokensResolved.size) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candFirstParts = candidateTokens.drop(candidateSurnameTokensResolved.size)
            if (candFirstParts.isNotEmpty() && matchFirstNamesOrInitials(sourceFirstParts, candFirstParts)) {
                if (isSurnameRelaxedFuzzyMatch(sourceSurname, candidateSurname) ||
                    isSurnamePhoneticMatch(sourceSurname, candidateSurname)
                ) {
                    return true
                }
            }
        }

        // Candidate tokens without explicit surname structure, trying single token drop
        if (sourceTokens.size > 1 && candidateTokens.size >= 2) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candFirstPartsEnd = candidateTokens.dropLast(1)
            val candFirstPartsStart = candidateTokens.drop(1)

            if (matchFirstNamesOrInitials(sourceFirstParts, candFirstPartsEnd) &&
                (isSurnameRelaxedFuzzyMatch(sourceSurname, candidateTokens.last()) || isSurnamePhoneticMatch(sourceSurname, candidateTokens.last()))
            ) {
                return true
            }

            if (matchFirstNamesOrInitials(sourceFirstParts, candFirstPartsStart) &&
                (isSurnameRelaxedFuzzyMatch(sourceSurname, candidateTokens.first()) || isSurnamePhoneticMatch(sourceSurname, candidateTokens.first()))
            ) {
                return true
            }
        }

        // Shared Given Name / First Name token of significant length
        if (sourceTokens.size > 1 && candidateTokens.size > 1) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candFirstParts = candidateTokens.dropLast(1)
            if (sourceFirstParts.isNotEmpty() && candFirstParts.isNotEmpty()) {
                val sourceFirstInitial = sourceFirstParts.first().firstOrNull()
                val candFirstInitial = candFirstParts.first().firstOrNull()
                if (sourceFirstInitial != null && (sourceFirstInitial == candFirstInitial || isInitialsCompatible(sourceFirstInitial.toString(), candFirstInitial.toString()))) {
                    if (sourceFirstParts == candFirstParts) {
                        return true
                    }
                    if (sourceFirstParts.any { part ->
                            part.length >= 3 && candFirstParts.any { cand ->
                                cand == part ||
                                    cand.startsWith(part) ||
                                    part.startsWith(cand) ||
                                    isSurnameRelaxedFuzzyMatch(part, cand) ||
                                    isSurnamePhoneticMatch(part, cand) ||
                                    (levenshteinDistance(part, cand) <= 1 && minOf(part.length, cand.length) >= 4) ||
                                    (normalizeTransliteration(part) == normalizeTransliteration(cand) && normalizeTransliteration(part).length >= 3)
                            }
                        }
                    ) {
                        return true
                    }
                }
            }
        }

        // Transliterated given name and fuzzy surname
        if (sourceTokens.size > 1 && candidateTokens.size > 1) {
            val srcFirst = sourceTokens.first()
            val candFirst = candidateTokens.first()
            if ((srcFirst == candFirst || isSurnameRelaxedFuzzyMatch(srcFirst, candFirst) || isSurnamePhoneticMatch(srcFirst, candFirst)) &&
                (isSurnameRelaxedFuzzyMatch(sourceSurname, candidateSurname) || isSurnamePhoneticMatch(sourceSurname, candidateSurname))
            ) {
                return true
            }
        }

        // Exact unique surname of length >= 4
        if (sourceSurname.length >= 4 && candidateSurname.length >= 4 && (sourceSurname == candidateSurname || isSurnameRelaxedFuzzyMatch(sourceSurname, candidateSurname))) {
            if (sourceTokens.size > 1 && candidateTokens.size > 1) {
                val sourceFirst = sourceTokens.dropLast(1).joinToString("")
                val candFirst = candidateTokens.dropLast(1).joinToString("")
                if (sourceFirst.length == 1 && candFirst.length == 1 && !isInitialsCompatible(sourceFirst, candFirst)) {
                    return false
                }
            }
            return true
        }

        // Any distinctive shared token of significant length (>= 4)
        if (sourceTokens.any { st -> st.length >= 4 && candidateTokens.any { ct -> ct == st || (st != sourceSurname && (isSurnameRelaxedFuzzyMatch(st, ct) || isSurnamePhoneticMatch(st, ct))) } }) {
            if (sourceTokens.size > 1 && candidateTokens.size > 1) {
                val sourceFirst = sourceTokens.dropLast(1).joinToString("")
                val candFirst = candidateTokens.dropLast(1).joinToString("")
                if (sourceFirst.length == 1 && candFirst.length == 1 && !isInitialsCompatible(sourceFirst, candFirst)) {
                    return false
                }
            }
            return true
        }

        // Significant token overlap or similarity with compatible first initial
        val srcInitial = sourceTokens.first().firstOrNull()
        val candInitial = candidateTokens.first().firstOrNull()
        if (srcInitial != null && srcInitial == candInitial) {
            if (sourceTokens.any { token ->
                    token.length >= 3 && candidateTokens.any { candTok ->
                        candTok == token ||
                            candTok.startsWith(token) ||
                            token.startsWith(candTok) ||
                            isSurnameRelaxedFuzzyMatch(token, candTok) ||
                            isSurnamePhoneticMatch(token, candTok)
                    }
                }
            ) {
                return true
            }

            val srcJoined = sourceTokens.joinToString(" ")
            val candJoined = candidateTokens.joinToString(" ")
            if (similarityRatio(srcJoined, candJoined) >= 0.50) {
                return true
            }
        }

        return false
    }

    /**
     * Determines if the source tokens match the initials and the surname of a candidate.
     */
    fun matchesInitialsAndSurname(
        sourceTokens: List<String>,
        candidate: StatsRosterPlayer
    ): Boolean {
        if (sourceTokens.isEmpty()) return false

        val candidateFullNameTokens = tokenize(normalize(candidate.fullName))
        val candidateKnownAsTokens = candidate.knownAs?.let { tokenize(normalize(it)) }.orEmpty()
        val candidateSurnameTokens = candidate.surname?.let { tokenize(normalize(it)) }.orEmpty()
        val candidateExpandedTokens = candidate.expandedFullName?.let { tokenize(normalize(it)) }.orEmpty()

        if (checkTokensMatch(sourceTokens, candidateFullNameTokens, candidateSurnameTokens)) {
            return true
        }

        if (candidateKnownAsTokens.isNotEmpty() &&
            checkTokensMatch(sourceTokens, candidateKnownAsTokens, candidateSurnameTokens)
        ) {
            return true
        }

        if (candidateExpandedTokens.isNotEmpty() &&
            checkTokensMatch(sourceTokens, candidateExpandedTokens, emptyList())
        ) {
            return true
        }

        if (candidateExpandedTokens.isNotEmpty() &&
            matchesInitialsAndAnyExpandedNameToken(sourceTokens, candidateExpandedTokens)
        ) {
            return true
        }

        if (candidateExpandedTokens.isNotEmpty() &&
            matchesReorderedContractedExpandedName(sourceTokens, candidateExpandedTokens)
        ) {
            return true
        }

        return false
    }

    /**
     * Checks if the initials and surname within a source token list match a candidate player's name
     * based on a given fuzzy surname matching function.
     */
    fun matchesInitialsAndFuzzySurname(
        sourceTokens: List<String>,
        candidate: StatsRosterPlayer,
        surnameMatcher: (String, String) -> Boolean
    ): Boolean {
        if (sourceTokens.isEmpty()) return false

        val candidateFullNameTokens = tokenize(normalize(candidate.fullName))
        val candidateKnownAsTokens = candidate.knownAs?.let { tokenize(normalize(it)) }.orEmpty()
        val candidateSurnameTokens = candidate.surname?.let { tokenize(normalize(it)) }.orEmpty()
        val candidateExpandedTokens = candidate.expandedFullName?.let { tokenize(normalize(it)) }.orEmpty()

        if (checkTokensFuzzyMatch(sourceTokens, candidateFullNameTokens, candidateSurnameTokens, surnameMatcher)) {
            return true
        }

        if (candidateKnownAsTokens.isNotEmpty() &&
            checkTokensFuzzyMatch(sourceTokens, candidateKnownAsTokens, candidateSurnameTokens, surnameMatcher)
        ) {
            return true
        }

        if (candidateExpandedTokens.isNotEmpty() &&
            checkTokensFuzzyMatch(sourceTokens, candidateExpandedTokens, emptyList(), surnameMatcher)
        ) {
            return true
        }

        return false
    }
}

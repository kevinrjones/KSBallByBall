package com.knowledgespike.ballbyball.parsecricsheet

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import java.text.Normalizer
import java.util.Locale

/**
 * Encapsulates errors related to invalid [StatsPlayerId] values.
 */
sealed class StatsPlayerIdError(val message: String) {
    class Invalid(value: Int?) : StatsPlayerIdError("Stats player ID must be a positive integer: '$value'")
}

/**
 * Strongly typed identifier for a stats database player.
 */
@JvmInline
value class StatsPlayerId private constructor(val value: Int) {
    companion object {
        context(raise: Raise<StatsPlayerIdError.Invalid>)
        operator fun invoke(value: Int?): StatsPlayerId {
            if (value == null || value <= 0) raise.raise(StatsPlayerIdError.Invalid(value))
            return StatsPlayerId(value)
        }

        fun of(value: Int): Either<StatsPlayerIdError.Invalid, StatsPlayerId> = either { invoke(value) }

        fun from(value: Int): StatsPlayerId = requireNotNull(of(value).getOrNull()) {
            "Stats player ID must be a positive integer"
        }
    }
}

/**
 * Represents a player in a CricketArchive match roster.
 */
data class StatsRosterPlayer(
    val playerId: StatsPlayerId,
    val fullName: String,
    val knownAs: String? = null,
    val surname: String? = null
)

/**
 * Result of linking a source Cricsheet player name/id to a stats player ID.
 */
data class PlayerMatchResult(
    val sourcePersonId: String,
    val sourceName: String,
    val statsPlayerId: StatsPlayerId?
)

/**
 * Pure functional matching engine for resolving Cricsheet player names against
 * CricketArchive match roster players.
 */
object PlayerNameMatcher {

    /**
     * Attempts to find a unique matching [StatsRosterPlayer] in the given roster for [sourceName].
     * Returns null if no match or multiple ambiguous matches are found.
     */
    fun matchPlayer(
        sourceName: String,
        roster: List<StatsRosterPlayer>
    ): StatsRosterPlayer? {
        if (sourceName.isBlank() || roster.isEmpty()) return null

        val normalizedSource = normalize(sourceName)
        if (normalizedSource.isEmpty()) return null

        // Stage 1: Exact normalized match on fullName or knownAs
        val exactMatches = roster.filter { player ->
            normalize(player.fullName) == normalizedSource ||
                (player.knownAs != null && normalize(player.knownAs) == normalizedSource)
        }
        if (exactMatches.size == 1) {
            return exactMatches.first()
        }
        if (exactMatches.size > 1) {
            return null
        }

        // Stage 2: Tokenized Initials and Surname match
        val sourceTokens = tokenize(normalizedSource)
        if (sourceTokens.isEmpty()) return null

        val initialsSurnameMatches = roster.filter { player ->
            matchesInitialsAndSurname(sourceTokens, player)
        }

        return if (initialsSurnameMatches.size == 1) {
            initialsSurnameMatches.first()
        } else {
            null
        }
    }

    internal fun normalize(name: String): String {
        val decomposed = Normalizer.normalize(name, Normalizer.Form.NFD)
        val withoutDiacritics = decomposed.replace(Regex("\\p{M}+"), "")
        return withoutDiacritics
            .replace(Regex("[.,'-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)
    }

    private fun tokenize(normalized: String): List<String> =
        normalized.split(' ').filter(String::isNotBlank)

    private fun matchesInitialsAndSurname(
        sourceTokens: List<String>,
        candidate: StatsRosterPlayer
    ): Boolean {
        if (sourceTokens.isEmpty()) return false

        val candidateFullNameTokens = tokenize(normalize(candidate.fullName))
        val candidateKnownAsTokens = candidate.knownAs?.let { tokenize(normalize(it)) }.orEmpty()
        val candidateSurnameTokens = candidate.surname?.let { tokenize(normalize(it)) }.orEmpty()

        if (checkTokensMatch(sourceTokens, candidateFullNameTokens, candidateSurnameTokens)) {
            return true
        }

        if (candidateKnownAsTokens.isNotEmpty() &&
            checkTokensMatch(sourceTokens, candidateKnownAsTokens, candidateSurnameTokens)
        ) {
            return true
        }

        return false
    }

    private fun checkTokensMatch(
        sourceTokens: List<String>,
        candidateTokens: List<String>,
        explicitSurnameTokens: List<String>
    ): Boolean {
        if (sourceTokens.isEmpty() || candidateTokens.isEmpty()) return false

        if (sourceTokens.size == 1) {
            val single = sourceTokens.first()
            val candidateSurname = if (explicitSurnameTokens.isNotEmpty()) {
                explicitSurnameTokens.joinToString(" ")
            } else {
                candidateTokens.last()
            }
            return single == candidateSurname || single == candidateTokens.first()
        }

        val candidateSurnameTokensResolved = if (explicitSurnameTokens.isNotEmpty()) {
            explicitSurnameTokens
        } else {
            listOf(candidateTokens.last())
        }

        val surnameLength = candidateSurnameTokensResolved.size
        if (sourceTokens.size > surnameLength) {
            val sourceSurnameParts = sourceTokens.takeLast(surnameLength)
            if (sourceSurnameParts == candidateSurnameTokensResolved) {
                val sourceFirstParts = sourceTokens.dropLast(surnameLength)
                val candidateFirstParts = candidateTokens.dropLast(surnameLength)
                return matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)
            }
        }

        val lastSourceToken = sourceTokens.last()
        val lastCandidateToken = candidateTokens.last()
        if (lastSourceToken == lastCandidateToken) {
            val sourceFirstParts = sourceTokens.dropLast(1)
            val candidateFirstParts = candidateTokens.dropLast(1)
            return matchFirstNamesOrInitials(sourceFirstParts, candidateFirstParts)
        }

        return false
    }

    private fun matchFirstNamesOrInitials(
        sourceFirstParts: List<String>,
        candidateFirstParts: List<String>
    ): Boolean {
        if (sourceFirstParts.isEmpty() || candidateFirstParts.isEmpty()) return false

        if (sourceFirstParts.size == 1 && candidateFirstParts.first() == sourceFirstParts.first()) {
            return true
        }

        val sourceInitials = sourceFirstParts.joinToString("") { it }
        val candidateInitials = candidateFirstParts.mapNotNull { it.firstOrNull() }.joinToString("")

        if (sourceInitials == candidateInitials) return true
        if (sourceInitials.isNotEmpty() && candidateInitials.startsWith(sourceInitials)) return true

        return false
    }
}

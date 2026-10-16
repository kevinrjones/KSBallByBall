package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.clishared.schema.Info
import com.knowledgespike.ballbyball.clishared.schema.TeamNamePolicy
import com.knowledgespike.ballbyball.identity.SourceCaId
import org.slf4j.LoggerFactory
import java.nio.file.Path
import java.sql.Connection
import java.time.LocalDate

fun interface MatchLinker {
    fun link(match: Info, input: Path): StatsLinkResult
}

sealed interface StatsLinkResult {
    data class Linked(val sourceCaId: SourceCaId) : StatsLinkResult

    data class NotFound(val criteria: MatchLookupCriteria) : StatsLinkResult

    data class Ambiguous(val criteria: MatchLookupCriteria, val caIds: List<SourceCaId>) : StatsLinkResult

    data class InvalidAlias(val message: String) : StatsLinkResult

    data object Skipped : StatsLinkResult
}

class StatsMatchLinker(
    private val statsRepository: StatsMatchRepository,
    private val aliasResolver: StatsTeamAliasResolver? = null,
    private val lookupPolicy: StatsMatchLookupPolicy = StatsMatchLookupPolicy()
) : MatchLinker {
    private val log = LoggerFactory.getLogger(StatsMatchLinker::class.java)
    private val lookupFailureLog = LoggerFactory.getLogger(LOOKUP_FAILURE_LOGGER)

    override fun link(match: Info, input: Path): StatsLinkResult {
        if (!StatsMatchLinkPolicy.shouldLink(match)) return StatsLinkResult.Skipped
        val criteria = MatchLookupCriteria.from(match)
        log.debug("Looking up stats match for {}", criteria)
        return try {
            val result = when (val repository = statsRepository) {
                is BulkStatsMatchRepository -> lookupInBatch(criteria, repository)
                else -> {
                    val exactDateResult = lookupWithMarginFallback(criteria)
                    if (exactDateResult is StatsLinkResult.NotFound) {
                        lookupWithDateHeuristics(criteria, exactDateResult)
                    } else {
                        exactDateResult
                    }
                }
            }
            logResult(criteria, result, input)
            result
        } catch (exception: Exception) {
            lookupFailureLog.error("Stats match lookup failed for {}", criteria, exception)
            throw exception
        }
    }

    private fun lookupInBatch(
        criteria: MatchLookupCriteria,
        repository: BulkStatsMatchRepository
    ): StatsLinkResult {
        val dates = listOf(criteria.matchDate) + StatsMatchDateHeuristic.candidateDates(criteria.matchDate)
        val exactCandidate = lookupPolicy.exactCandidate(criteria.statsFirstTeam, criteria.statsSecondTeam)
        val exactMatches = repository.candidateMatches(dates, listOf(exactCandidate))
        val exactPrimaryResult = batchLookup(criteria, exactMatches, 0, criteria.matchDate, criteria.margin)
        if (exactPrimaryResult !is StatsLinkResult.NotFound) return exactPrimaryResult

        val firstAliases = aliasResolver?.aliasesFor(criteria.sourceFirstTeam).orEmpty().distinct()
        invalidAlias(criteria.sourceFirstTeam, firstAliases)?.let { return it }
        val secondAliases = aliasResolver?.aliasesFor(criteria.sourceSecondTeam).orEmpty().distinct()
        invalidAlias(criteria.sourceSecondTeam, secondAliases)?.let { return it }
        val aliasCandidate = lookupPolicy.aliasCandidate(
            criteria.statsFirstTeam,
            criteria.statsSecondTeam,
            firstAliases,
            secondAliases
        )
        val aliasMatches = aliasCandidate?.let { repository.candidateMatches(dates, listOf(it)) }.orEmpty()

        dates.forEachIndexed { dateIndex, date ->
            val dateCriteria = criteria.copy(matchDate = date)
            if (criteria.margin != null) {
                returnIfFound(
                    criteria,
                    date,
                    batchLookup(dateCriteria, exactMatches, 0, date, criteria.margin)
                )?.let { return it }

                val aliasMarginResult = aliasCandidate?.let {
                    batchLookup(dateCriteria, aliasMatches, 0, date, criteria.margin)
                } ?: StatsLinkResult.NotFound(dateCriteria)
                returnIfFound(criteria, date, aliasMarginResult)?.let { return it }

                lookupFailureLog.warn(
                    "Stats match lookup with margin failed for {}; retrying without margin",
                    dateCriteria
                )
                returnIfFound(criteria, date, batchLookup(dateCriteria, exactMatches, 0, date, null))
                    ?.let { return it }

                val aliasPlainResult = aliasCandidate?.let {
                    batchLookup(dateCriteria, aliasMatches, 0, date, null)
                } ?: StatsLinkResult.NotFound(dateCriteria)
                returnIfFound(criteria, date, aliasPlainResult)?.let { return it }
            } else {
                returnIfFound(criteria, date, batchLookup(dateCriteria, exactMatches, 0, date, null))
                    ?.let { return it }

                val aliasResult = aliasCandidate?.let {
                    batchLookup(dateCriteria, aliasMatches, 0, date, null)
                } ?: StatsLinkResult.NotFound(dateCriteria)
                returnIfFound(criteria, date, aliasResult)?.let { return it }
            }

            if (dateIndex == 0 && dates.size > 1) {
                lookupFailureLog.warn("Exact-date stats match lookup failed for {}; trying adjacent dates", criteria)
            }
        }
        return StatsLinkResult.NotFound(criteria)
    }

    private fun returnIfFound(
        criteria: MatchLookupCriteria,
        date: LocalDate,
        result: StatsLinkResult
    ): StatsLinkResult? = when (result) {
        is StatsLinkResult.NotFound -> null
        is StatsLinkResult.Linked -> {
            if (date != criteria.matchDate) {
                log.info("Stats match linked using date heuristic for {} on {}", criteria, date)
            }
            result
        }
        is StatsLinkResult.Ambiguous -> result
        is StatsLinkResult.InvalidAlias -> result
        StatsLinkResult.Skipped -> result
    }

    private fun batchLookup(
        criteria: MatchLookupCriteria,
        matches: List<StatsMatchCandidateMatch>,
        candidateIndex: Int,
        date: LocalDate,
        margin: Int?
    ): StatsLinkResult {
        val caIds = matches.asSequence()
            .filter { it.candidateIndex == candidateIndex && it.matchDate == date }
            .filter { margin == null || it.howMuch == margin }
            .map(StatsMatchCandidateMatch::caId)
            .distinct()
            .toList()
        return when (caIds.size) {
            0 -> StatsLinkResult.NotFound(criteria)
            1 -> StatsLinkResult.Linked(caIds.single())
            else -> StatsLinkResult.Ambiguous(criteria, caIds)
        }
    }

    private fun lookupWithMarginFallback(criteria: MatchLookupCriteria): StatsLinkResult {
        val firstAttempt = lookup(criteria)
        return if (firstAttempt is StatsLinkResult.NotFound && criteria.margin != null) {
            lookupFailureLog.warn("Stats match lookup with margin failed for {}; retrying without margin", criteria)
            lookup(criteria.copy(margin = null))
        } else {
            firstAttempt
        }
    }

    private fun lookupWithDateHeuristics(
        criteria: MatchLookupCriteria,
        primaryNotFound: StatsLinkResult.NotFound
    ): StatsLinkResult {
        lookupFailureLog.warn("Exact-date stats match lookup failed for {}; trying adjacent dates", criteria)
        for (date in StatsMatchDateHeuristic.candidateDates(criteria.matchDate)) {
            val attempt = lookupWithMarginFallback(criteria.copy(matchDate = date))
            if (attempt !is StatsLinkResult.NotFound) {
                if (attempt is StatsLinkResult.Linked) {
                    log.info("Stats match linked using date heuristic for {} on {}", criteria, date)
                }
                return attempt
            }
        }
        return primaryNotFound
    }

    private fun lookup(criteria: MatchLookupCriteria): StatsLinkResult {
        val exactCandidate = lookupPolicy.exactCandidate(criteria.statsFirstTeam, criteria.statsSecondTeam)
        val exactResult = lookupCandidate(criteria, exactCandidate)
        if (exactResult !is StatsLinkResult.NotFound) return exactResult

        val firstAliases = aliasResolver?.aliasesFor(criteria.sourceFirstTeam).orEmpty().distinct()
        invalidAlias(criteria.sourceFirstTeam, firstAliases)?.let { return it }
        val secondAliases = aliasResolver?.aliasesFor(criteria.sourceSecondTeam).orEmpty().distinct()
        invalidAlias(criteria.sourceSecondTeam, secondAliases)?.let { return it }
        val aliasCandidate = lookupPolicy.aliasCandidate(
            criteria.statsFirstTeam,
            criteria.statsSecondTeam,
            firstAliases,
            secondAliases
        )
        return aliasCandidate?.let { lookupCandidate(criteria, it) } ?: exactResult
    }

    private fun invalidAlias(
        sourceTeamName: String,
        aliases: List<StatsTeamLookup.Id>
    ): StatsLinkResult.InvalidAlias? = aliases.firstNotNullOfOrNull { alias ->
        when (val matchingTeamCount = statsRepository.statsTeamCount(alias.value)) {
            0 -> StatsLinkResult.InvalidAlias(
                "Alias for $sourceTeamName targets stats team ${alias.value.value}, but no unique stats team was found"
            )

            1 -> null
            else -> StatsLinkResult.InvalidAlias(
                "Alias for $sourceTeamName targets stats team ${alias.value.value}, but $matchingTeamCount stats teams were found"
            )
        }
    }

    private fun lookupCandidate(criteria: MatchLookupCriteria, candidate: StatsMatchCandidate): StatsLinkResult {
        val caIds = statsRepository.caIds(criteria, candidate.firstTeam, candidate.secondTeam).distinct()
        return when (caIds.size) {
            0 -> StatsLinkResult.NotFound(criteria)
            1 -> StatsLinkResult.Linked(caIds.single())
            else -> StatsLinkResult.Ambiguous(criteria, caIds)
        }
    }

    private fun logResult(criteria: MatchLookupCriteria, result: StatsLinkResult, input: Path? = null) {
        when (result) {
            is StatsLinkResult.Linked, StatsLinkResult.Skipped -> Unit
            is StatsLinkResult.NotFound -> lookupFailureLog.error("No stats match found for {} at {}", criteria, input)
            is StatsLinkResult.Ambiguous ->
                lookupFailureLog.info("Stats match lookup was ambiguous for {}: {}", criteria, result.caIds)

            is StatsLinkResult.InvalidAlias ->
                lookupFailureLog.error("Stats team alias was invalid for {}: {}", criteria, result.message)
        }
    }


    companion object {
        const val LOOKUP_FAILURE_LOGGER = "com.knowledgespike.ballbyball.parsecricsheet.lookup-failures"

        fun fromConnections(statsConnection: Connection, aliasConnection: Connection): StatsMatchLinker {
            val statsRepository = JdbcStatsMatchRepository(statsConnection)
            val aliasRepository = JdbcTeamNameAliasRepository(aliasConnection)
            return StatsMatchLinker(statsRepository, aliasRepository)
        }
    }
}

data class MatchLookupCriteria(
    val sourceFirstTeam: String,
    val sourceSecondTeam: String,
    val statsFirstTeam: String,
    val statsSecondTeam: String,
    val matchDate: LocalDate,
    val margin: Int?
) {
    override fun toString(): String =
        "${teamDescription(sourceFirstTeam, statsFirstTeam)} vs " +
            "${teamDescription(sourceSecondTeam, statsSecondTeam)} on $matchDate ${marginDescription()}"

    private fun teamDescription(sourceTeam: String, statsTeam: String): String =
        if (sourceTeam == statsTeam) {
            "source \"$sourceTeam\""
        } else {
            "source \"$sourceTeam\" (stats \"$statsTeam\")"
        }

    private fun marginDescription(): String = margin?.let { "with margin $it" } ?: "without margin"

    companion object {
        fun from(match: Info): MatchLookupCriteria {
            require(match.teams.size == 2) { "A match must contain exactly two teams" }
            return MatchLookupCriteria(
                sourceFirstTeam = match.teams[0],
                sourceSecondTeam = match.teams[1],
                statsFirstTeam = teamNameForStats(match.teams[0], match),
                statsSecondTeam = teamNameForStats(match.teams[1], match),
                matchDate = LocalDate.parse(match.dates.first()),
                margin = match.outcome.by?.runs
                    ?: match.outcome.by?.wickets
                    ?: match.outcome.by?.innings
            )
        }

        private fun teamNameForStats(teamName: String, match: Info): String =
            TeamNamePolicy.nameForMatch(teamName, match)
    }
}

package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.identity.SourcePersonId
import org.slf4j.LoggerFactory
import java.sql.Connection

/**
 * Interface for linking match players to their corresponding stats player records.
 */
fun interface PlayerLinker {
    /**
     * Links players in [matchPlayers] (source player name -> sourcePersonId) to their stats player IDs
     * using the roster of match [sourceCaId].
     */
    fun linkPlayers(
        sourceCaId: SourceCaId,
        matchPlayers: Map<String, String>
    ): List<PlayerMatchResult>

    fun linkPlayers(
        sourceCaId: SourceCaId,
        matchPlayers: Map<String, String>,
        sourceFileName: String
    ): List<PlayerMatchResult> = linkPlayers(sourceCaId, matchPlayers)
}

/**
 * Orchestrates player linking using an in-memory cache, stats match roster lookups,
 * pure name matching, and write-through persistence to the BBB database.
 */
class StatsPlayerLinker(
    private val statsPlayerRepository: StatsPlayerRepository,
    private val playerIdentityRepository: PlayerIdentityRepository,
    private val playerCache: PlayerCache
) : PlayerLinker {

    private val log = LoggerFactory.getLogger(StatsPlayerLinker::class.java)
    private val lookupFailureLog = LoggerFactory.getLogger(LOOKUP_FAILURE_LOGGER)

    override fun linkPlayers(
        sourceCaId: SourceCaId,
        matchPlayers: Map<String, String>
    ): List<PlayerMatchResult> = linkPlayers(sourceCaId, matchPlayers, "")

    override fun linkPlayers(
        sourceCaId: SourceCaId,
        matchPlayers: Map<String, String>,
        sourceFileName: String
    ): List<PlayerMatchResult> {
        if (matchPlayers.isEmpty()) return emptyList()

        val parsedPlayers: List<Pair<String, SourcePersonId>> = matchPlayers.mapNotNull { (name, rawId) ->
            SourcePersonId.of(rawId).getOrNull()?.let { personId -> name to personId }
        }

        val (cached, uncached) = parsedPlayers.partition { (_, personId) -> playerCache[personId] != null }

        val cachedResults = cached.map { (name, personId) ->
            val cachedId = playerCache[personId]!!
            log.debug("Player '{}' ({}) resolved from cache -> ca_id: {}", name, personId.value, cachedId.value)
            PlayerMatchResult(personId, name, cachedId)
        }

        if (uncached.isEmpty()) {
            log.debug("All {} players in match {} were resolved from cache", matchPlayers.size, sourceCaId.value)
            return cachedResults
        }

        log.info(
            "Querying roster for match {} to resolve {} uncached players ({} already cached)",
            sourceCaId.value,
            uncached.size,
            cachedResults.size
        )

        val roster = try {
            statsPlayerRepository.playersForMatch(sourceCaId)
        } catch (e: Exception) {
            log.error("Failed to query match roster for match {}: {}", sourceCaId.value, e.message, e)
            emptyList()
        }

        val filePrefix = if (sourceFileName.isNotBlank()) "In file $sourceFileName: " else ""

        if (roster.isEmpty()) {
            log.warn("{}No roster players found in stats database for match {}", filePrefix, sourceCaId.value)
        }

        val cachedPlayerIds = cachedResults.mapNotNull { it.statsPlayerId }.toSet()
        val availableRoster = roster.filter { it.playerId !in cachedPlayerIds }.toMutableList()

        val matchedResults = mutableListOf<PlayerMatchResult>()
        val unresolved = mutableListOf<Pair<String, SourcePersonId>>()

        // Pass 1: High-confidence exact & fuzzy matching
        for ((name, personId) in uncached) {
            val matched = PlayerNameMatcher.matchPlayer(name, availableRoster)
            if (matched != null) {
                log.info(
                    "Matched player '{}' ({}) to stats player '{}' (ca_id: {})",
                    name,
                    personId.value,
                    matched.fullName,
                    matched.playerId.value
                )
                persistAndCache(personId, name, matched.playerId)
                availableRoster.removeIf { it.playerId == matched.playerId }
                matchedResults.add(PlayerMatchResult(personId, name, matched.playerId))
            } else {
                unresolved.add(name to personId)
            }
        }

        // Pass 2: Elimination / duck-typing fallback matching for remaining unresolved players & roster candidates
        var madeProgress = true
        while (madeProgress && unresolved.isNotEmpty() && availableRoster.isNotEmpty()) {
            madeProgress = false
            val iterator = unresolved.iterator()
            while (iterator.hasNext()) {
                val (name, personId) = iterator.next()
                val matched = PlayerNameMatcher.matchCloseEnough(name, availableRoster)
                if (matched != null) {
                    log.warn(
                        "{}Matched person '{}' ({}) to stats player '{}' (ca_id: {}) via elimination / fuzzy fallback in match {}",
                        filePrefix,
                        name,
                        personId.value,
                        matched.fullName,
                        matched.playerId.value,
                        sourceCaId.value
                    )
                    lookupFailureLog.warn(
                        "{}Matched person '{}' ({}) to stats player '{}' (ca_id: {}) via elimination / fuzzy fallback in match {}",
                        filePrefix,
                        name,
                        personId.value,
                        matched.fullName,
                        matched.playerId.value,
                        sourceCaId.value
                    )
                    persistAndCache(personId, name, matched.playerId)
                    availableRoster.removeIf { it.playerId == matched.playerId }
                    matchedResults.add(PlayerMatchResult(personId, name, matched.playerId))
                    iterator.remove()
                    madeProgress = true
                    break
                }
            }
        }

        // Pass 3: Match only a globally unique exact full name
        val exactMatchFailures = mutableMapOf<SourcePersonId, String>()
        val exactMatchIterator = unresolved.iterator()
        while (exactMatchIterator.hasNext()) {
            val (name, personId) = exactMatchIterator.next()
            val exactMatches = try {
                statsPlayerRepository.exactMatchesForFullName(name).distinctBy { it.playerId }
            } catch (e: Exception) {
                log.error("Failed to query exact stats name '{}' for match {}: {}", name, sourceCaId.value, e.message, e)
                emptyList()
            }
            val matched = exactMatches.singleOrNull()

            if (matched != null) {
                log.info(
                    "Matched person '{}' ({}) to exact stats name '{}' (ca_id: {})",
                    name,
                    personId.value,
                    matched.fullName,
                    matched.playerId.value
                )
                persistAndCache(personId, name, matched.playerId)
                matchedResults.add(PlayerMatchResult(personId, name, matched.playerId))
                exactMatchIterator.remove()
            } else if (exactMatches.size > 1) {
                exactMatchFailures[personId] =
                    "ambiguous exact-name candidates: ${exactMatches.joinToString(", ") { it.fullName }}"
            }
        }

        // Pass 4: Unmatched persons logged as errors
        val failedResults = unresolved.map { (name, personId) ->
            val reason = exactMatchFailures[personId]
                ?: if (roster.isEmpty()) "no roster found in stats database"
                else unmatchedReason(name, availableRoster)
            log.warn(
                "{}Unmatched player '{}' ({}) in match {} - {}",
                filePrefix,
                name,
                personId.value,
                sourceCaId.value,
                reason
            )
            lookupFailureLog.error(
                "{}Unmatched person '{}' ({}) in match {} - {}",
                filePrefix,
                name,
                personId.value,
                sourceCaId.value,
                reason
            )
            PlayerMatchResult(personId, name, null)
        }

        return cachedResults + matchedResults + failedResults
    }

    private fun persistAndCache(personId: SourcePersonId, name: String, statsPlayerId: StatsPlayerId) {
        try {
            playerIdentityRepository.savePlayerMapping(personId, name, statsPlayerId)
            playerCache[personId] = statsPlayerId
        } catch (e: Exception) {
            log.error(
                "Failed to persist player mapping for '{}' ({}) -> ca_id {}: {}",
                name,
                personId.value,
                statsPlayerId.value,
                e.message,
                e
            )
        }
    }

    private fun unmatchedReason(sourceName: String, availableRoster: List<StatsRosterPlayer>): String {
        val compatibleCandidates = PlayerNameMatcher.compatibleCandidates(sourceName, availableRoster)
        if (compatibleCandidates.size > 1) {
            return "ambiguous compatible candidates: ${compatibleCandidates.joinToString(", ") { it.fullName }}"
        }

        val candidateNames = availableRoster.take(MAX_UNMATCHED_CANDIDATES).joinToString(", ") { it.fullName }
        return "no unique compatible candidate among ${availableRoster.size} remaining roster record(s)" +
            candidateNames.takeIf(String::isNotBlank)?.let { "; candidates: $it" }.orEmpty()
    }

    companion object {
        private const val LOOKUP_FAILURE_LOGGER = "com.knowledgespike.ballbyball.parsecricsheet.lookup-failures"
        private const val MAX_UNMATCHED_CANDIDATES = 5

        fun fromConnections(
            statsConnection: Connection,
            bbbConnection: Connection
        ): StatsPlayerLinker {
            val statsRepository = JdbcStatsPlayerRepository(statsConnection)
            val bbbRepository = JdbcPlayerIdentityRepository(bbbConnection)
            val initialMappings = bbbRepository.loadAllPlayerMappings()
            val cache = ThreadSafePlayerCache(initialMappings)
            return StatsPlayerLinker(statsRepository, bbbRepository, cache)
        }
    }
}

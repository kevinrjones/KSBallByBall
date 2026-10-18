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

    override fun linkPlayers(
        sourceCaId: SourceCaId,
        matchPlayers: Map<String, String>
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

        if (roster.isEmpty()) {
            log.warn("No roster players found in stats database for match {}", sourceCaId.value)
            return cachedResults + uncached.map { (name, personId) -> PlayerMatchResult(personId, name, null) }
        }

        val uncachedResults = uncached.map { (name, personId) ->
            val matched = PlayerNameMatcher.matchPlayer(name, roster)
            if (matched != null) {
                log.info(
                    "Matched player '{}' ({}) to stats player '{}' (ca_id: {})",
                    name,
                    personId.value,
                    matched.fullName,
                    matched.playerId.value
                )
                try {
                    playerIdentityRepository.savePlayerMapping(personId, name, matched.playerId)
                    playerCache[personId] = matched.playerId
                } catch (e: Exception) {
                    log.error(
                        "Failed to persist player mapping for '{}' ({}) -> ca_id {}: {}",
                        name,
                        personId.value,
                        matched.playerId.value,
                        e.message,
                        e
                    )
                }
                PlayerMatchResult(personId, name, matched.playerId)
            } else {
                log.warn(
                    "Unmatched player '{}' ({}) in match {}",
                    name,
                    personId.value,
                    sourceCaId.value
                )
                PlayerMatchResult(personId, name, null)
            }
        }

        return cachedResults + uncachedResults
    }

    companion object {
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

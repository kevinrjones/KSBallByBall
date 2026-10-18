package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
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

        val results = mutableListOf<PlayerMatchResult>()
        val uncachedPlayers = mutableMapOf<String, String>()

        // 1. Check in-memory cache
        for ((name, sourcePersonId) in matchPlayers) {
            val cachedId = playerCache[sourcePersonId]
            if (cachedId != null) {
                log.debug("Player '{}' ({}) resolved from cache -> ca_id: {}", name, sourcePersonId, cachedId.value)
                results.add(PlayerMatchResult(sourcePersonId, name, cachedId))
            } else {
                uncachedPlayers[name] = sourcePersonId
            }
        }

        // 2. If all players are already cached, bypass stats database query completely
        if (uncachedPlayers.isEmpty()) {
            log.debug("All {} players in match {} were resolved from cache", matchPlayers.size, sourceCaId.value)
            return results
        }

        log.info(
            "Querying roster for match {} to resolve {} uncached players ({} already cached)",
            sourceCaId.value,
            uncachedPlayers.size,
            results.size
        )

        // 3. Query match roster from stats repository
        val roster = try {
            statsPlayerRepository.playersForMatch(sourceCaId)
        } catch (e: Exception) {
            log.error("Failed to query match roster for match {}: {}", sourceCaId.value, e.message, e)
            emptyList()
        }

        if (roster.isEmpty()) {
            log.warn("No roster players found in stats database for match {}", sourceCaId.value)
            for ((name, sourcePersonId) in uncachedPlayers) {
                results.add(PlayerMatchResult(sourcePersonId, name, null))
            }
            return results
        }

        // 4. Match uncached players against roster
        for ((name, sourcePersonId) in uncachedPlayers) {
            val matched = PlayerNameMatcher.matchPlayer(name, roster)
            if (matched != null) {
                log.info(
                    "Matched player '{}' ({}) to stats player '{}' (ca_id: {})",
                    name,
                    sourcePersonId,
                    matched.fullName,
                    matched.playerId.value
                )
                // Write-through to cache and database
                playerCache[sourcePersonId] = matched.playerId
                try {
                    playerIdentityRepository.savePlayerMapping(sourcePersonId, name, matched.playerId)
                } catch (e: Exception) {
                    log.error(
                        "Failed to persist player mapping for '{}' ({}) -> ca_id {}: {}",
                        name,
                        sourcePersonId,
                        matched.playerId.value,
                        e.message,
                        e
                    )
                }
                results.add(PlayerMatchResult(sourcePersonId, name, matched.playerId))
            } else {
                log.warn(
                    "Unmatched player '{}' ({}) in match {}",
                    name,
                    sourcePersonId,
                    sourceCaId.value
                )
                results.add(PlayerMatchResult(sourcePersonId, name, null))
            }
        }

        return results
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

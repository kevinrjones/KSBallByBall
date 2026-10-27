package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException

/**
 * Repository interface for retrieving player and official roster information from the stats database.
 */
interface StatsPlayerRepository {
    /**
     * Retrieves the list of players and officials associated with the specified match [matchCaId].
     */
    fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer>

    /**
     * Retrieves all players and officials whose full name exactly matches [fullName].
     */
    fun exactMatchesForFullName(fullName: String): List<StatsRosterPlayer> = emptyList()
}

/**
 * JDBC implementation of [StatsPlayerRepository] querying match player and official details.
 */
class JdbcStatsPlayerRepository(
    private val connection: Connection,
    private val querySql: String = DEFAULT_QUERY_SQL
) : StatsPlayerRepository {

    private val log = LoggerFactory.getLogger(JdbcStatsPlayerRepository::class.java)

    override fun exactMatchesForFullName(fullName: String): List<StatsRosterPlayer> {
        val matches = mutableListOf<StatsRosterPlayer>()

        for (sql in DEFAULT_EXACT_NAME_QUERIES) {
            try {
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, fullName)
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            val player = rs.toStatsRosterPlayer()
                            if (player != null && player.fullName == fullName) {
                                matches.add(player)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                log.debug("Skipping exact-name query due to SQL exception (possibly absent table): {}", e.message)
            }
        }

        return matches.distinctBy { it.playerId }
    }

    override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> {
        val players = mutableListOf<StatsRosterPlayer>()
        val queries = if (querySql == DEFAULT_QUERY_SQL) DEFAULT_ROSTER_QUERIES else listOf(querySql)

        for (sql in queries) {
            try {
                connection.prepareStatement(sql).use { statement ->
                    val paramCount = runCatching { statement.parameterMetaData?.parameterCount }.getOrNull()?.takeIf { it > 0 }
                        ?: sql.count { it == '?' }
                    for (i in 1..paramCount) {
                        statement.setString(i, matchCaId.value)
                    }
                    statement.executeQuery().use { rs ->
                        while (rs.next()) {
                            val player = rs.toStatsRosterPlayer(expandedIndex = 5)
                            if (player != null) {
                                players.add(player)
                            }
                        }
                    }
                }
            } catch (e: SQLException) {
                log.debug("Skipping roster query due to SQL exception (possibly absent table): {}", e.message)
            }
        }
        return players.distinctBy { it.playerId to it.fullName }
    }

    private fun ResultSet.toStatsRosterPlayer(expandedIndex: Int? = null): StatsRosterPlayer? {
        val id = getInt(1)
        val fullName = getString(2) ?: ""
        val sortNamePart = getString(3)
        val otherNamePart = getString(4)
        val expandedFullName = expandedIndex?.let { runCatching { getString(it) }.getOrNull() }?.takeIf(String::isNotBlank)

        if (id <= 0 || fullName.isBlank()) return null

        val knownAs = if (!otherNamePart.isNullOrBlank() && !sortNamePart.isNullOrBlank()) {
            "$otherNamePart $sortNamePart".trim()
        } else null
        val surname = sortNamePart?.takeIf(String::isNotBlank)

        return StatsRosterPlayer(
            playerId = StatsPlayerId.from(id),
            fullName = fullName,
            knownAs = knownAs,
            surname = surname,
            expandedFullName = expandedFullName
        )
    }

    companion object {
        val DEFAULT_EXACT_NAME_QUERIES = listOf(
            "SELECT PlayerId, FullName, SortNamePart, OtherNamePart FROM Players WHERE FullName = ?",
            "SELECT UmpiresId, FullName, SortNamePart, OtherNamePart FROM Umpires WHERE FullName = ?",
            "SELECT TvUmpiresId, FullName, SortNamePart, OtherNamePart FROM TvUmpires WHERE FullName = ?",
            "SELECT ReserveUmpiresId, FullName, SortNamePart, OtherNamePart FROM ReserveUmpires WHERE FullName = ?",
            "SELECT MatchRefereesId, FullName, SortNamePart, OtherNamePart FROM MatchReferees WHERE FullName = ?",
            "SELECT ScorersId, FullName, SortNamePart, OtherNamePart FROM Scorers WHERE FullName = ?",
            "SELECT OfficialId, FullName, SortNamePart, OtherNamePart FROM Officials WHERE FullName = ?"
        )

        val DEFAULT_ROSTER_QUERIES = listOf(
            """
            SELECT p.PlayerId AS id, pm.FullName, pm.SortNamePart, pm.OtherNamePart, p.ExpandedFullName
            FROM PlayersMatches pm
            JOIN Players p ON p.Id = pm.PlayerId
            JOIN Matches m ON m.Id = pm.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT p.PlayerId AS id, p.FullName, p.SortNamePart, p.OtherNamePart, p.ExpandedFullName
            FROM Fielding f
            JOIN Players p ON p.Id = f.PlayerId
            JOIN Matches m ON m.Id = f.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.UmpiresId AS id, u.FullName, u.SortNamePart, u.OtherNamePart, o.ExpandedFullName
            FROM UmpiresMatches um
            JOIN Umpires u ON u.Id = um.PersonId
            LEFT JOIN Officials o ON o.OfficialId = u.UmpiresId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.TvUmpiresId AS id, u.FullName, u.SortNamePart, u.OtherNamePart, o.ExpandedFullName
            FROM TvUmpiresMatches um
            JOIN TvUmpires u ON u.Id = um.PersonId
            LEFT JOIN Officials o ON o.OfficialId = u.TvUmpiresId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.ReserveUmpiresId AS id, u.FullName, u.SortNamePart, u.OtherNamePart, o.ExpandedFullName
            FROM ReserveUmpiresMatches um
            JOIN ReserveUmpires u ON u.Id = um.PersonId
            LEFT JOIN Officials o ON o.OfficialId = u.ReserveUmpiresId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.MatchRefereesId AS id, u.FullName, u.SortNamePart, u.OtherNamePart, o.ExpandedFullName
            FROM MatchRefereesMatches um
            JOIN MatchReferees u ON u.Id = um.PersonId
            LEFT JOIN Officials o ON o.OfficialId = u.MatchRefereesId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT o.OfficialId AS id, o.FullName, o.SortNamePart, o.OtherNamePart, o.ExpandedFullName
            FROM OfficialsMatches om
            JOIN Officials o ON o.Id = om.PersonId
            JOIN Matches m ON m.Id = om.MatchId
            WHERE m.CaId = ?
            """.trimIndent()
        )

        const val DEFAULT_QUERY_SQL = "DEFAULT_ROSTER_QUERY"
    }
}

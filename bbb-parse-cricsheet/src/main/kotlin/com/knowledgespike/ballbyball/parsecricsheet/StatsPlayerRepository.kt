package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLException

/**
 * Repository interface for retrieving player and official roster information from the stats database.
 */
interface StatsPlayerRepository {
    /**
     * Retrieves the list of players and officials associated with the specified match [matchCaId].
     */
    fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer>
}

/**
 * JDBC implementation of [StatsPlayerRepository] querying match player and official details.
 */
class JdbcStatsPlayerRepository(
    private val connection: Connection,
    private val querySql: String = DEFAULT_QUERY_SQL
) : StatsPlayerRepository {

    private val log = LoggerFactory.getLogger(JdbcStatsPlayerRepository::class.java)

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
                            val id = rs.getInt(1)
                            val fullName = rs.getString(2) ?: ""
                            val sortNamePart = rs.getString(3)
                            val otherNamePart = rs.getString(4)
                            val knownAs = if (!otherNamePart.isNullOrBlank() && !sortNamePart.isNullOrBlank()) {
                                "$otherNamePart $sortNamePart".trim()
                            } else null
                            if (id > 0 && fullName.isNotBlank()) {
                                players.add(
                                    StatsRosterPlayer(
                                        playerId = StatsPlayerId.from(id),
                                        fullName = fullName,
                                        knownAs = knownAs,
                                        surname = sortNamePart?.takeIf(String::isNotBlank)
                                    )
                                )
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

    companion object {
        val DEFAULT_ROSTER_QUERIES = listOf(
            """
            SELECT p.PlayerId AS id, pm.FullName, pm.SortNamePart, pm.OtherNamePart
            FROM PlayersMatches pm
            JOIN Players p ON p.Id = pm.PlayerId
            JOIN Matches m ON m.Id = pm.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.UmpiresId AS id, u.FullName, u.SortNamePart, u.OtherNamePart
            FROM UmpiresMatches um
            JOIN Umpires u ON u.Id = um.PersonId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.TvUmpiresId AS id, u.FullName, u.SortNamePart, u.OtherNamePart
            FROM TvUmpiresMatches um
            JOIN TvUmpires u ON u.Id = um.PersonId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.ReserveUmpiresId AS id, u.FullName, u.SortNamePart, u.OtherNamePart
            FROM ReserveUmpiresMatches um
            JOIN ReserveUmpires u ON u.Id = um.PersonId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT u.MatchRefereesId AS id, u.FullName, u.SortNamePart, u.OtherNamePart
            FROM MatchRefereesMatches um
            JOIN MatchReferees u ON u.Id = um.PersonId
            JOIN Matches m ON m.Id = um.MatchId
            WHERE m.CaId = ?
            """.trimIndent(),
            """
            SELECT o.OfficialId AS id, o.FullName, o.SortNamePart, o.OtherNamePart
            FROM OfficialsMatches om
            JOIN Officials o ON o.Id = om.PersonId
            JOIN Matches m ON m.Id = om.MatchId
            WHERE m.CaId = ?
            """.trimIndent()
        )

        const val DEFAULT_QUERY_SQL = "DEFAULT_ROSTER_QUERY"
    }
}

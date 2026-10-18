package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
import java.sql.Connection

/**
 * Repository interface for retrieving player roster information from the stats database.
 */
interface StatsPlayerRepository {
    /**
     * Retrieves the list of players associated with the specified match [matchCaId].
     */
    fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer>
}

/**
 * JDBC implementation of [StatsPlayerRepository] querying match player details.
 */
class JdbcStatsPlayerRepository(
    private val connection: Connection,
    private val querySql: String = DEFAULT_QUERY_SQL
) : StatsPlayerRepository {

    override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> {
        val players = mutableListOf<StatsRosterPlayer>()
        connection.prepareStatement(querySql).use { statement ->
            statement.setString(1, matchCaId.value)
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
        return players
    }

    companion object {
        const val DEFAULT_QUERY_SQL = """
            SELECT p.PlayerId, pm.FullName, pm.SortNamePart, pm.OtherNamePart
            FROM PlayersMatches pm
            JOIN Players p ON p.Id = pm.PlayerId
            JOIN Matches m ON m.Id = pm.MatchId
            WHERE m.CaId = ?
        """
    }
}

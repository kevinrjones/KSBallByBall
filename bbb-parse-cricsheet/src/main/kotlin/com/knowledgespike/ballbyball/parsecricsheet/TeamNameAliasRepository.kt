package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.AliasSourceSystem
import java.sql.Connection

fun interface TeamNameAliasRepository {
    fun statsTeamIds(sourceTeamName: String): List<StatsTeamId>
}

fun interface StatsTeamAliasResolver {
    fun aliasesFor(sourceTeamName: String): List<StatsTeamLookup.Id>
}

class JdbcTeamNameAliasRepository(
    private val connection: Connection,
    private val sourceSystem: String = AliasSourceSystem.CRICSHEET.value
) : TeamNameAliasRepository, StatsTeamAliasResolver {
    override fun statsTeamIds(sourceTeamName: String): List<StatsTeamId> = connection.prepareStatement(ALIAS_SQL).use { statement ->
        statement.setString(1, sourceSystem)
        statement.setString(2, sourceTeamName)
        statement.executeQuery().use { results ->
            buildList {
                while (results.next()) add(StatsTeamId.from(results.getInt("stats_team_id")))
            }
        }
    }

    override fun aliasesFor(sourceTeamName: String): List<StatsTeamLookup.Id> =
        statsTeamIds(sourceTeamName).map(StatsTeamLookup::Id)

    private companion object {
        const val ALIAS_SQL = """
            SELECT stats_team_id
            FROM team_name_aliases
            WHERE source_system = ? AND source_team_name = ?
            ORDER BY stats_team_id
        """
    }
}

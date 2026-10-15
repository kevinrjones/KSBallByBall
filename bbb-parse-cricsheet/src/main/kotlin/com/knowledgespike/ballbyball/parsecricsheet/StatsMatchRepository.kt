package com.knowledgespike.ballbyball.parsecricsheet

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.identity.SourceCaId
import java.sql.Connection
import java.sql.PreparedStatement

sealed class StatsTeamIdError(val message: String) {
    class Invalid(value: Int?) : StatsTeamIdError("Stats team ID must be a positive integer: '$value'")
}

@JvmInline
value class StatsTeamId private constructor(val value: Int) {
    companion object {
        context(raise: Raise<StatsTeamIdError.Invalid>)
        operator fun invoke(value: Int?): StatsTeamId {
            if (value == null || value <= 0) raise.raise(StatsTeamIdError.Invalid(value))
            return StatsTeamId(value)
        }

        fun of(value: Int): Either<StatsTeamIdError.Invalid, StatsTeamId> = either { invoke(value) }

        fun from(value: Int): StatsTeamId = requireNotNull(of(value).getOrNull()) {
            "Stats team ID must be a positive integer"
        }
    }
}

interface StatsMatchRepository {
    fun statsTeamCount(statsTeamId: StatsTeamId): Int

    fun caIds(
        criteria: MatchLookupCriteria,
        firstTeam: StatsTeamLookup,
        secondTeam: StatsTeamLookup
    ): List<SourceCaId>
}

class JdbcStatsMatchRepository(private val connection: Connection) : StatsMatchRepository {
    override fun statsTeamCount(statsTeamId: StatsTeamId): Int = connection.prepareStatement(STATS_TEAM_COUNT_SQL).use { statement ->
        statement.setInt(1, statsTeamId.value)
        statement.executeQuery().use { results ->
            results.next()
            results.getInt(1)
        }
    }

    override fun caIds(
        criteria: MatchLookupCriteria,
        firstTeam: StatsTeamLookup,
        secondTeam: StatsTeamLookup
    ): List<SourceCaId> {
        val sql = lookupSql(firstTeam, secondTeam, criteria.margin != null)
        return connection.prepareStatement(sql).use { statement ->
            statement.setString(1, criteria.matchDate.toString())
            var parameterIndex = 2
            criteria.margin?.let { margin ->
                statement.setInt(parameterIndex++, margin)
            }
            listOf(firstTeam, secondTeam, secondTeam, firstTeam).forEach { team ->
                team.bind(statement, parameterIndex)
                parameterIndex += team.parameterCount
            }
            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) {
                        val caId = requireNotNull(results.getString("CaId")) { "Stats match CA ID must not be null" }
                        add(SourceCaId.from(caId))
                    }
                }
            }
        }
    }

    private fun lookupSql(firstTeam: StatsTeamLookup, secondTeam: StatsTeamLookup, includeMargin: Boolean): String {
        val marginPredicate = if (includeMargin) "AND m.HowMuch = ?" else ""
        return """
        SELECT m.CaId
        FROM Matches m
        JOIN Teams home_team ON home_team.Id = m.HomeTeamId
        JOIN Teams away_team ON away_team.Id = m.AwayTeamId
        WHERE m.MatchStartDate = ?
          $marginPredicate
          AND ((${firstTeam.predicate("home_team")} AND ${secondTeam.predicate("away_team")})
            OR (${secondTeam.predicate("home_team")} AND ${firstTeam.predicate("away_team")}))
            AND MatchType not in ('t', 'wt', 'o', 'wo', 'itt', 'witt')
        ORDER BY m.CaId
        """.trimIndent()
    }

    private companion object {
        const val STATS_TEAM_COUNT_SQL = "SELECT COUNT(*) FROM Teams WHERE TeamId = ?"
    }
}

sealed interface StatsTeamLookup {
    val parameterCount: Int

    fun predicate(tableAlias: String): String

    fun bind(statement: PreparedStatement, index: Int)

    data class Name(val value: String) : StatsTeamLookup {
        override val parameterCount: Int = 1
        override fun predicate(tableAlias: String): String = "$tableAlias.Name = ?"

        override fun bind(statement: PreparedStatement, index: Int) {
            statement.setString(index, value)
        }
    }

    data class Id(val value: StatsTeamId) : StatsTeamLookup {
        override val parameterCount: Int = 1
        override fun predicate(tableAlias: String): String = "$tableAlias.TeamId = ?"

        override fun bind(statement: PreparedStatement, index: Int) {
            statement.setInt(index, value.value)
        }
    }

    data class IdSet(val values: List<StatsTeamId>) : StatsTeamLookup {
        override val parameterCount: Int = values.size
        init {
            require(values.isNotEmpty()) { "Stats team ID candidates must not be empty" }
        }

        override fun predicate(tableAlias: String): String =
            "$tableAlias.TeamId IN (${values.joinToString(", ") { "?" }})"

        override fun bind(statement: PreparedStatement, index: Int) {
            values.forEachIndexed { offset, value -> statement.setInt(index + offset, value.value) }
        }
    }

    data class AnyOf(val values: List<StatsTeamLookup>) : StatsTeamLookup {
        init {
            require(values.size >= 2) { "Combined stats team candidates must contain at least two values" }
        }

        override val parameterCount: Int = values.sumOf(StatsTeamLookup::parameterCount)

        override fun predicate(tableAlias: String): String =
            values.joinToString(prefix = "(", postfix = ")", separator = " OR ") { it.predicate(tableAlias) }

        override fun bind(statement: PreparedStatement, index: Int) {
            var nextIndex = index
            values.forEach { value ->
                value.bind(statement, nextIndex)
                nextIndex += value.parameterCount
            }
        }
    }
}
package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalDate

class JdbcStatsMatchRepositoryQueryTest {
    @Test
    fun `given match margin and aliased teams when querying then sql and binder positions agree`() {
        var sql = ""
        val bindings = sortedMapOf<Int, Any>()
        val results = Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "next" -> false
                "close" -> null
                else -> null
            }
        } as ResultSet
        val statement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "setString", "setInt" -> {
                    bindings[arguments!![0] as Int] = arguments[1] as Any
                    null
                }
                "executeQuery" -> results
                "close" -> null
                else -> null
            }
        } as PreparedStatement
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, arguments ->
            if (method.name == "prepareStatement") {
                sql = arguments!![0] as String
                statement
            } else {
                null
            }
        } as Connection
        val criteria = MatchLookupCriteria(
            sourceFirstTeam = "Home",
            sourceSecondTeam = "Away",
            statsFirstTeam = "Home",
            statsSecondTeam = "Away",
            matchDate = LocalDate.parse("2024-01-01"),
            margin = 105
        )
        val firstTeam = StatsTeamLookup.AnyOf(
            listOf(
                StatsTeamLookup.Name("Home"),
                StatsTeamLookup.IdSet(listOf(StatsTeamId.from(10), StatsTeamId.from(11)))
            )
        )

        val matches = JdbcStatsMatchRepository(connection).caIds(
            criteria,
            firstTeam,
            StatsTeamLookup.Name("Away")
        )

        expectThat(matches).isEqualTo(emptyList())
        expectThat(sql).contains("m.HowMuch = ?")
        expectThat(sql).contains("home_team.TeamId IN (?, ?)")
        expectThat(sql).contains("ORDER BY m.CaId")
        expectThat(bindings.toMap()).isEqualTo(
            mapOf(
                1 to "2024-01-01",
                2 to 105,
                3 to "Home",
                4 to 10,
                5 to 11,
                6 to "Away",
                7 to "Away",
                8 to "Home",
                9 to 10,
                10 to 11
            )
        )
    }
}
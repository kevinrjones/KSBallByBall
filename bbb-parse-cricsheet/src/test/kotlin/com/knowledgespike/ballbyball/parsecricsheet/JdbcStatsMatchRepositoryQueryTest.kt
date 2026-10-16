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
    fun `given case-insensitive team names and a date window when querying then bindings agree`() {
        var sql = ""
        val preparedSql = mutableListOf<String>()
        val bindings = sortedMapOf<Int, Any>()
        val teams = listOf(
            mapOf("Id" to 1, "TeamId" to 10, "Name" to "Home"),
            mapOf("Id" to 2, "TeamId" to 20, "Name" to "Away"),
            mapOf("Id" to 3, "TeamId" to 11, "Name" to "Alternate Home")
        )
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, arguments ->
            if (method.name == "prepareStatement") {
                val statementSql = arguments!![0] as String
                sql = statementSql
                preparedSql += statementSql
                var rowIndex = -1
                val results = Proxy.newProxyInstance(
                    ResultSet::class.java.classLoader,
                    arrayOf(ResultSet::class.java)
                ) { _, resultMethod, resultArguments ->
                    when (resultMethod.name) {
                        "next" -> if (statementSql.contains("FROM Teams")) {
                            rowIndex++
                            rowIndex < teams.size
                        } else {
                            false
                        }
                        "getInt", "getString" -> teams[rowIndex][resultArguments!![0] as String]
                        "close" -> null
                        else -> null
                    }
                } as ResultSet
                Proxy.newProxyInstance(
                    PreparedStatement::class.java.classLoader,
                    arrayOf(PreparedStatement::class.java)
                ) { _, statementMethod, statementArguments ->
                    when (statementMethod.name) {
                        "setString", "setInt" -> {
                            bindings[statementArguments!![0] as Int] = statementArguments[1] as Any
                            null
                        }
                        "executeQuery" -> results
                        "close" -> null
                        else -> null
                    }
                } as PreparedStatement
            } else {
                null
            }
        } as Connection
        val dates = (1..5).map { LocalDate.parse("2024-01-0$it") }
        val firstTeam = StatsTeamLookup.AnyOf(
            listOf(
                StatsTeamLookup.Name("hOME"),
                StatsTeamLookup.IdSet(listOf(StatsTeamId.from(10), StatsTeamId.from(11)))
            )
        )

        val matches = JdbcStatsMatchRepository(connection).candidateMatches(
            dates,
            listOf(StatsMatchCandidate(firstTeam, StatsTeamLookup.Name("aWAY")))
        )

        expectThat(matches).isEqualTo(emptyList())
        expectThat(sql).contains("m.MatchStartDate IN (?, ?, ?, ?, ?)")
        expectThat(sql).contains("m.HomeTeamId IN (?, ?)")
        expectThat(sql).contains("ORDER BY m.CaId, m.MatchStartDate")
        expectThat(sql.contains("JOIN Teams")).isEqualTo(false)
        expectThat(sql.contains("m.HowMuch = ?")).isEqualTo(false)
        expectThat(preparedSql.count { it.contains("FROM Teams") }).isEqualTo(1)
        expectThat(preparedSql.count { it.contains("FROM Matches m") }).isEqualTo(1)
        expectThat(bindings.toMap()).isEqualTo(
            mapOf(
                1 to "2024-01-01",
                2 to "2024-01-02",
                3 to "2024-01-03",
                4 to "2024-01-04",
                5 to "2024-01-05",
                6 to 1,
                7 to 3,
                8 to 2,
                9 to 2,
                10 to 1,
                11 to 3
            )
        )
    }
}
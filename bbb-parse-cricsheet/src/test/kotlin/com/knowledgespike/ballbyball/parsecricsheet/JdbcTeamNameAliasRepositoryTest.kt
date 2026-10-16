package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class JdbcTeamNameAliasRepositoryTest {
    @Test
    fun `given an alias exists when resolving the same source name twice then it is queried once`() {
        var queryCount = 0
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, _ ->
            if (method.name == "prepareStatement") {
                var hasReturnedRow = false
                val results = Proxy.newProxyInstance(
                    ResultSet::class.java.classLoader,
                    arrayOf(ResultSet::class.java)
                ) { _, resultMethod, _ ->
                    when (resultMethod.name) {
                        "next" -> if (hasReturnedRow) {
                            false
                        } else {
                            hasReturnedRow = true
                            true
                        }
                        "getInt" -> 42
                        "close" -> null
                        else -> null
                    }
                } as ResultSet
                Proxy.newProxyInstance(
                    PreparedStatement::class.java.classLoader,
                    arrayOf(PreparedStatement::class.java)
                ) { _, statementMethod, _ ->
                    when (statementMethod.name) {
                        "executeQuery" -> {
                            queryCount++
                            results
                        }
                        "close" -> null
                        else -> null
                    }
                } as PreparedStatement
            } else {
                null
            }
        } as Connection
        val repository = JdbcTeamNameAliasRepository(connection)

        expectThat(repository.statsTeamIds("Team A")).containsExactly(StatsTeamId.from(42))
        expectThat(repository.statsTeamIds("Team A")).containsExactly(StatsTeamId.from(42))
        expectThat(queryCount).isEqualTo(1)
    }
}
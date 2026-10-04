package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.config.DatabaseResources
import com.knowledgespike.ballbyball.api.config.DatabaseSettings
import com.knowledgespike.ballbyball.types.values.Limit
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotBlank
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import java.sql.DriverManager

class JooqMatchRepositoryIntegrationTest {

    private fun databaseSettings() = DatabaseSettings(
        jdbcUrl = System.getProperty("test.db.url") ?: System.getenv("TEST_DB_JDBC_URL")
            ?: "jdbc:mariadb://localhost:3306/acs_ball_by_ball",
        user = System.getProperty("test.db.user") ?: System.getenv("TEST_DB_USER") ?: "ballbyball",
        password = System.getProperty("test.db.password") ?: System.getenv("TEST_DB_PASSWORD") ?: "p4ssw0rd",
        maximumPoolSize = 2
    )

    @Test
    fun `recentMatches fetches matches from live database when available`(): Unit = runBlocking {
        val settings = databaseSettings()

        DatabaseResources(settings).use { resources ->
            assumeTrue(resources.databaseHealth.isHealthy(), "Database is not reachable on localhost:3306")
            assumeTrue(relationalSchemaAvailable(settings), "Configured database does not contain the relational schema")

            val repository = resources.matchRepository
            val matches = repository.recentMatches(Limit.from(10))

            expectThat(matches).isNotEmpty()
            val first = matches.first()
            expectThat(first.competition).isNotNull().get { requireNotNull(this) }.isNotBlank()
            expectThat(first.team1).isNotNull().get { requireNotNull(this) }.isNotBlank()
            expectThat(first.team2).isNotNull().get { requireNotNull(this) }.isNotBlank()
            expectThat(first.score1).isNotNull().get { requireNotNull(this) }.isNotBlank()
            expectThat(first.score2).isNotNull().get { requireNotNull(this) }.isNotBlank()
        }
    }

    @Test
    fun `scoresheet maps selected match context innings and all ordered deliveries when available`(): Unit = runBlocking {
        val settings = databaseSettings()

        DatabaseResources(settings).use { resources ->
            assumeTrue(resources.databaseHealth.isHealthy(), "Database is not reachable on localhost:3306")
            assumeTrue(relationalSchemaAvailable(settings), "Configured database does not contain the relational schema")

            val repository = resources.matchRepository
            val match = repository.recentMatches(Limit.from(10)).firstOrNull()
            assumeTrue(match != null, "No match is available in the relational database")
            val scoresheet = repository.scoresheet(requireNotNull(match).publicMatchId)

            expectThat(scoresheet).isNotNull()
            val mapped = requireNotNull(scoresheet)
            expectThat(mapped.context.publicMatchId).isEqualTo(match.publicMatchId)
            expectThat(mapped.innings.map { it.inningsNumber }).isEqualTo(
                mapped.innings.map { it.inningsNumber }.sorted()
            )
            val deliveries = mapped.innings.flatMap { it.deliveries }
            if (deliveries.isNotEmpty()) {
                expectThat(deliveries.map { it.deliveryKey }).isEqualTo(
                    deliveries.map { it.deliveryKey }.sorted()
                )
            }
        }
    }

    private fun relationalSchemaAvailable(settings: DatabaseSettings): Boolean = runCatching {
        DriverManager.getConnection(settings.jdbcUrl, settings.user, settings.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select id from matches where 1 = 0").use {
                    connection.createStatement().use { inningsStatement ->
                        inningsStatement.executeQuery("select declared from innings where 1 = 0").use { true }
                    }
                }
            }
        }
    }.getOrDefault(false)
}

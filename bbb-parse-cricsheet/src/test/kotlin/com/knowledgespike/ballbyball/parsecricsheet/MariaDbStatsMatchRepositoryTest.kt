package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MariaDBContainer
import strikt.api.expectThat
import strikt.assertions.containsExactly
import java.sql.DriverManager

class MariaDbStatsMatchRepositoryTest {
    @Test
    fun `given a MariaDB stats schema when a match has a margin then the repository binds the margin`() {
        val container = MariaDBContainer<Nothing>("mariadb:11.4").apply {
            withDatabaseName("stats")
        }
        try {
            container.start()
        } catch (exception: IllegalStateException) {
            assumeTrue(false, "MariaDB integration test requires Docker: ${exception.message}")
            return
        }
        try {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                createSchema(connection)
                connection.createStatement().use { statement ->
                    statement.executeUpdate("insert into Teams (Id, TeamId, Name) values (1, 10, 'Team A'), (2, 20, 'Team B')")
                    statement.executeUpdate(
                        "insert into Matches (CaId, HomeTeamId, AwayTeamId, MatchStartDate, HowMuch) " +
                            "values ('ca-123', 1, 2, '2024-01-01', 100), ('ca-other', 1, 2, '2024-01-01', 105)"
                    )
                }

                val matches = JdbcStatsMatchRepository(connection).caIds(
                    MatchLookupCriteria(
                        sourceFirstTeam = "Team A",
                        sourceSecondTeam = "Team B",
                        statsFirstTeam = "Team A",
                        statsSecondTeam = "Team B",
                        matchDate = java.time.LocalDate.parse("2024-01-01"),
                        margin = 105
                    ),
                    StatsTeamLookup.Name("Team A"),
                    StatsTeamLookup.Name("Team B")
                )

                expectThat(matches).containsExactly(SourceCaId.from("ca-other"))
            }
        } finally {
            container.stop()
        }
    }

    private fun createSchema(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table Teams (
                    Id int not null primary key,
                    TeamId int not null,
                    Name varchar(100) not null
                )
                """.trimIndent()
            )
            statement.execute(
                """
                create table Matches (
                    CaId varchar(100) not null,
                    HomeTeamId int not null,
                    AwayTeamId int not null,
                    MatchStartDate date,
                    HowMuch int not null
                )
                """.trimIndent()
            )
        }
    }
}
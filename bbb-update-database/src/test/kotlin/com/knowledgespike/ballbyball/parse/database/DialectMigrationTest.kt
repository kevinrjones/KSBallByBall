package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.identity.AliasSourceSystem
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

class DialectMigrationTest {
    @Test
    fun `mysql initial migration allows distinct aliases and rejects duplicate triples`() {
        val container = MySQLContainer<Nothing>("mysql:8.4").apply {
            withDatabaseName("acs_ball_by_ball")
        }
        container.start()
        try {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                applyMigration(connection, "mysql", "1__initial_tables.sql")
                assertDistinctAliasTargetsAreAccepted(connection)
                assertAliasCount(connection, expected = 2)
                assertDuplicateAliasTriplesAreRejected(connection)
                assertAliasUniqueIndexExists(connection, expected = true)
                org.junit.jupiter.api.assertThrows<SQLException> {
                    insertDuplicateSourceCaIds(connection)
                }
                assertUniqueSourceIdIndexExists(connection)
            }
        } finally {
            container.stop()
        }
    }

    @Test
    fun `postgres initial migration allows distinct aliases and rejects duplicate triples`() {
        val container = PostgreSQLContainer<Nothing>("postgres:17").apply {
            withDatabaseName("acs_ball_by_ball")
        }
        container.start()
        try {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                applyMigration(connection, "postgres", "1__initial_tables.sql")
                assertDistinctAliasTargetsAreAccepted(connection)
                assertDuplicateAliasTriplesAreRejected(connection)
                assertAliasUniqueIndexExists(connection, expected = true)
                org.junit.jupiter.api.assertThrows<SQLException> {
                    insertDuplicateSourceCaIds(connection)
                }
                assertUniqueSourceIdIndexExists(connection)
            }
        } finally {
            container.stop()
        }
    }


    private fun applyMigration(connection: Connection, dialect: String, fileName: String) {
        migrationStatements(dialect, fileName).forEach { sql ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    private fun assertDistinctAliasTargetsAreAccepted(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate(aliasInsert(1))
            expectThat(statement.executeUpdate(aliasInsert(2))).isEqualTo(1)
        }
    }

    private fun insertDuplicateSourceCaIds(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("insert into teams (id, source_team_id, team_name) values (1, 1, 'Home'), (2, 2, 'Away')")
            statement.executeUpdate("insert into grounds (id, source_ground_id, ground_name) values (1, 1, 'Ground')")
            statement.executeUpdate(
                "insert into matches " +
                    "(canonical_match_id, source_ca_id, file_name, match_in_series, match_type, event_name, " +
                    "match_date_text, season, match_start_year, balls_per_over, added_timestamp, team1_id, " +
                    "team2_id, ground_id, toss_team_id, victory_type, duration_days, margin) values " +
                    "('1890a7a8-f76d-5f36-89f7-39b0319044b0', 'ca-dup', 'one.json', 1, 'tt', 'Series', " +
                    "'2024-01-01', '2024', '2024', 6, CURRENT_TIMESTAMP, 1, 2, 1, 1, 'runs', 1, 1), " +
                    "('1890a7a8-f76d-5f36-89f7-39b0319044b1', 'ca-dup', 'two.json', 1, 'tt', 'Series', " +
                    "'2024-01-02', '2024', '2024', 6, CURRENT_TIMESTAMP, 1, 2, 1, 1, 'runs', 1, 1)"
            )
        }
    }

    private fun assertAliasCount(connection: Connection, expected: Int) {
        connection.createStatement().use { statement ->
            statement.executeQuery("select count(*) from team_name_aliases").use { result ->
                result.next()
                expectThat(result.getInt(1)).isEqualTo(expected)
            }
        }
    }

    private fun assertDuplicateAliasTriplesAreRejected(connection: Connection) {
        org.junit.jupiter.api.assertThrows<SQLException> {
            connection.createStatement().use { it.executeUpdate(aliasInsert(1)) }
        }
    }


    private fun assertAliasUniqueIndexExists(connection: Connection, expected: Boolean) {
        connection.metaData.getIndexInfo(null, null, "team_name_aliases", true, false).use { result ->
            val indexes = buildSet {
                while (result.next()) result.getString("INDEX_NAME")?.let(::add)
            }
            expectThat(indexes.contains("uq_team_name_aliases_source_name_team")).isEqualTo(expected)
        }
    }


    private fun aliasInsert(statsTeamId: Int) =
        "INSERT INTO team_name_aliases (source_system, source_team_name, stats_team_id) " +
            "VALUES ('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', $statsTeamId)"

    private fun assertUniqueSourceIdIndexExists(connection: Connection) {
        connection.metaData.getIndexInfo(null, null, "matches", true, false).use { result ->
            val indexes = buildSet {
                while (result.next()) result.getString("INDEX_NAME")?.let(::add)
            }
            expectThat(indexes.contains("uq_matches_source_ca_id")).isEqualTo(true)
        }
    }

    private fun migrationStatements(dialect: String, fileName: String): List<String> =
        splitSql(
            Files.readString(Path.of("migrations/$dialect/$fileName"))
                .lineSequence()
                .filterNot { it.trimStart().startsWith("--") }
                .joinToString("\n")
        )

    private fun splitSql(sql: String): List<String> {
        val statements = mutableListOf<String>()
        val current = StringBuilder()
        var inDollarQuote = false
        sql.lines().forEach { line ->
            if (line.contains("$$")) inDollarQuote = !inDollarQuote
            line.forEach { character ->
                if (character == ';' && !inDollarQuote) {
                    statements.add(current.toString().trim())
                    current.clear()
                } else {
                    current.append(character)
                }
            }
            current.append('\n')
        }
        current.toString().trim().takeIf(String::isNotBlank)?.let(statements::add)
        return statements.filter(String::isNotBlank)
    }

}
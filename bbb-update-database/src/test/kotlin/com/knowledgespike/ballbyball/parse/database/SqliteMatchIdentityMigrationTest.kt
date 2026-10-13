package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.identity.AliasSourceSystem
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException

class SqliteMatchIdentityMigrationTest {
    @Test
    fun `warehouse cutover creates normalized schema and removes warehouse tables`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("CREATE TABLE dim_match (id INTEGER PRIMARY KEY, file_name VARCHAR(120) NOT NULL)")
                statement.execute(
                    "CREATE TABLE dim_innings (innings_key INTEGER PRIMARY KEY, match_key INTEGER NOT NULL, " +
                        "FOREIGN KEY (match_key) REFERENCES dim_match (id))"
                )
                statement.execute(
                    "CREATE TABLE team_name_aliases (source_system VARCHAR(50) NOT NULL, " +
                        "source_team_name VARCHAR(100) NOT NULL, stats_team_id INTEGER NOT NULL)"
                )
                migrationStatements("1__initial_tables.sql")
                    .forEach(statement::execute)

                statement.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name LIKE 'dim_%'"
                ).use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(0)
                }
                statement.executeQuery(
                    "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name IN " +
                        "('dates', 'teams', 'people', 'grounds', 'matches', 'innings', 'deliveries', " +
                        "'wickets', 'match_people', 'delivery_wickets', 'delivery_fielders', 'match_source_reference')"
                ).use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(12)
                }
                statement.executeQuery("PRAGMA foreign_key_check").use { result ->
                    expectThat(result.next()).isEqualTo(false)
                }
                statement.executeQuery("PRAGMA table_info(matches)").use { result ->
                    val columns = buildList {
                        while (result.next()) add(result.getString("name"))
                    }
                    expectThat(columns).isEqualTo(
                        listOf(
                            "id", "canonical_match_id", "public_match_id", "source_ca_id", "file_name",
                            "match_in_series", "match_type", "event_name", "match_date_text", "season",
                            "match_start_year", "match_start_date_id", "balls_per_over", "added_timestamp",
                            "team1_id", "team2_id", "ground_id", "toss_team_id", "toss_decision", "victory_type",
                            "winner_team_id", "loser_team_id", "duration_days", "margin", "match_count"
                        )
                    )
                }
            }
        }
    }

    @Test
    fun `given initial schema then distinct targets for one alias are retained`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                migrationStatements("1__initial_tables.sql")
                    .forEach(statement::execute)

                statement.executeUpdate(
                    "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                        "values ('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', 1), " +
                        "('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', 2)"
                )
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from team_name_aliases where source_team_name = 'Warriors'")
                    .use { result ->
                        result.next()
                        expectThat(result.getInt(1)).isEqualTo(2)
                    }
                statement.executeQuery("pragma index_list('matches')").use { result ->
                    val indexes = buildList {
                        while (result.next()) add(result.getString("name"))
                    }
                    expectThat(indexes.contains("uq_matches_source_ca_id")).isEqualTo(true)
                }
            }

        }
    }

    @Test
    fun `given initial migration then alias names can map to distinct teams`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                migrationStatements("1__initial_tables.sql")
                    .forEach(statement::execute)
            }

            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                        "values ('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', 1)"
                )
                expectThat(statement.executeUpdate(
                    "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                        "values ('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', 2)"
                )).isEqualTo(1)
            }
        }
    }

    @Test
    fun `given initial migration then duplicate alias triples are rejected`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                migrationStatements("1__initial_tables.sql")
                    .forEach(statement::execute)
                statement.executeUpdate(
                    "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                        "values ('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', 1)"
                )
                org.junit.jupiter.api.assertThrows<SQLException> {
                    statement.executeUpdate(
                        "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                            "values ('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', 1)"
                    )
                }
                expectThat(
                    statement.executeUpdate(
                        "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                            "values ('${AliasSourceSystem.CRICSHEET.value}', 'Warriors', 2)"
                    )
                ).isEqualTo(1)
            }
        }
    }

    @Test
    fun `given duplicate source ca ids when identity migration runs then migration is rejected`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                migrationStatements("1__initial_tables.sql")
                    .forEach(statement::execute)
                statement.executeUpdate("insert into teams (id, source_team_id, team_name) values (1, 1, 'Home'), (2, 2, 'Away')")
                statement.executeUpdate("insert into grounds (id, source_ground_id, ground_name) values (1, 1, 'Ground')")
                statement.executeUpdate(
                    "insert into matches " +
                        "(canonical_match_id, source_ca_id, file_name, match_in_series, match_type, event_name, " +
                        "match_date_text, season, match_start_year, balls_per_over, added_timestamp, team1_id, " +
                        "team2_id, ground_id, toss_team_id, victory_type, duration_days, margin) values " +
                        "('1890a7a8-f76d-5f36-89f7-39b0319044b0', 'ca-dup', 'one.json', 1, 'tt', 'Series', " +
                        "'2024-01-01', '2024', '2024', 6, CURRENT_TIMESTAMP, 1, 2, 1, 1, 'runs', 1, 1)"
                )

                org.junit.jupiter.api.assertThrows<SQLException> {
                    statement.executeUpdate(
                        "insert into matches " +
                            "(canonical_match_id, source_ca_id, file_name, match_in_series, match_type, event_name, " +
                            "match_date_text, season, match_start_year, balls_per_over, added_timestamp, team1_id, " +
                            "team2_id, ground_id, toss_team_id, victory_type, duration_days, margin) values " +
                            "('1890a7a8-f76d-5f36-89f7-39b0319044b1', 'ca-dup', 'two.json', 1, 'tt', 'Series', " +
                            "'2024-01-02', '2024', '2024', 6, CURRENT_TIMESTAMP, 1, 2, 1, 1, 'runs', 1, 1)"
                    )
                }
            }
        }
    }

    private fun migrationStatements(vararg fileNames: String): List<String> = fileNames.flatMap(::readMigration)

    private fun readMigration(fileName: String): List<String> = Files.readString(
        Path.of("migrations/sqlite/$fileName")
    ).lines()
        .filterNot { it.trimStart().startsWith("--") }
        .joinToString("\n")
        .split(';')
        .map(String::trim)
        .filter(String::isNotBlank)
}
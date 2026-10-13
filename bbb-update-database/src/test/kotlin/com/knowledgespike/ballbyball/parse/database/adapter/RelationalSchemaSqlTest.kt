package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.AliasSourceSystem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.io.BufferedWriter
import java.io.StringWriter
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.nio.file.Files
import java.nio.file.Path

class RelationalSchemaSqlTest {
    @Test
    fun `given each SQL dialect when rendering schema then include alias triple uniqueness`() {
        SqlDialect.entries.forEach { dialect ->
            val schema = generatedSchema(dialect)

            expectThat(schema).contains("UNIQUE (source_system, source_team_name, stats_team_id)")
            expectThat(schema).contains("CREATE TABLE IF NOT EXISTS team_name_aliases")
            expectThat(schema).contains("CREATE INDEX IF NOT EXISTS idx_team_name_aliases_stats_team_id")
            expectThat(schema).not().contains("DROP TABLE IF EXISTS team_name_aliases")
        }
    }

    @Test
    fun `given each migration dialect then aliases are preserved and unique in the initial schema`() {
        listOf("mysql", "postgres", "sqlite").forEach { dialect ->
            val initialMigration = Files.readString(Path.of("migrations/$dialect/1__initial_tables.sql"))
            val drops = sqlStatements(initialMigration).filter { it.startsWith("DROP TABLE IF EXISTS", ignoreCase = true) }

            expectThat(drops.none { it.contains("team_name_aliases", ignoreCase = true) }).isEqualTo(true)
            expectThat(initialMigration).contains("CREATE TABLE IF NOT EXISTS team_name_aliases")
            expectThat(initialMigration).contains("uq_team_name_aliases_source_name_team")
            if (dialect == "mysql") {
                expectThat(initialMigration).contains("information_schema.statistics")
                expectThat(initialMigration).contains("PREPARE team_name_aliases_index_statement")
                expectThat(initialMigration).contains("PREPARE team_name_aliases_unique_index_statement")
            } else {
                expectThat(initialMigration).contains("CREATE INDEX IF NOT EXISTS idx_team_name_aliases_stats_team_id")
                expectThat(initialMigration).contains("CREATE UNIQUE INDEX IF NOT EXISTS uq_team_name_aliases_source_name_team")
            }
        }
    }

    @Test
    fun `given a custom MariaDB database name when rendering then use the validated identifier`() {
        val schema = StringWriter()
        BufferedWriter(schema).use { writer ->
            RelationalSchemaSql.writeTo(writer, SqlDialect.MARIADB, mariadbDatabaseName = "test_schema_2")
        }

        expectThat(schema.toString()).contains("USE test_schema_2;")
    }

    @Test
    fun `given an unsafe MariaDB database name when rendering then reject it`() {
        assertThrows<IllegalArgumentException> {
            BufferedWriter(StringWriter()).use { writer ->
                RelationalSchemaSql.writeTo(
                    writer,
                    SqlDialect.MARIADB,
                    mariadbDatabaseName = "safe; DROP DATABASE prod"
                )
            }
        }
    }

    @Test
    fun `given each dialect when generating schema then table and index statements stay aligned`() {
        val expectedTables = setOf(
            "team_name_aliases", "dates", "teams", "people", "grounds", "matches", "match_source_reference",
            "innings", "wickets", "deliveries", "match_people", "delivery_wickets", "delivery_fielders"
        )
        val expectedIndexes = indexNames(generatedSchema(SqlDialect.SQLITE))

        SqlDialect.entries.forEach { dialect ->
            val schema = generatedSchema(dialect)

            expectThat(tableNames(schema)).isEqualTo(expectedTables)
            expectThat(indexNames(schema)).isEqualTo(expectedIndexes)
            when (dialect) {
                SqlDialect.MARIADB -> {
                    expectThat(schema).contains("USE acs_ball_by_ball;")
                    expectThat(Regex("\\) ENGINE = InnoDB;").findAll(schema).count()).isEqualTo(expectedTables.size)
                }
                SqlDialect.POSTGRES -> {
                    expectThat(schema).contains("CREATE SCHEMA IF NOT EXISTS acs_ball_by_ball;")
                    expectThat(schema).contains("SET search_path TO acs_ball_by_ball;")
                }
                SqlDialect.SQLITE -> expectThat(schema).contains("PRAGMA foreign_keys = ON;")
            }
        }
    }

    @Test
    fun `given a saved alias when generated SQLite schema is applied again then alias survives`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            applySchema(connection)
            connection.prepareStatement(
                "insert into team_name_aliases (source_system, source_team_name, stats_team_id) values (?, ?, ?)"
            ).use { statement ->
                statement.setString(1, AliasSourceSystem.CRICSHEET.value)
                statement.setString(2, "North Stars")
                statement.setInt(3, 42)
                statement.executeUpdate()
            }

            applySchema(connection)

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from team_name_aliases").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
            assertThrows<SQLException> {
                connection.prepareStatement(
                    "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                        "values ('${AliasSourceSystem.CRICSHEET.value}', 'North Stars', 42)"
                ).use { it.executeUpdate() }
            }
        }
    }

    @Test
    fun `given generated MariaDB schema when applied then tables indexes and aliases are usable`() {
        val container = MariaDBContainer<Nothing>("mariadb:11.4").apply {
            withDatabaseName(RelationalSchemaSql.DEFAULT_MARIADB_DATABASE_NAME)
        }
        container.start()
        try {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                assertGeneratedSchemaCanBeAppliedAndReapplied(connection, SqlDialect.MARIADB)
            }
        } finally {
            container.stop()
        }
    }

    @Test
    fun `given generated Postgres schema when applied then tables indexes and aliases are usable`() {
        val container = PostgreSQLContainer<Nothing>("postgres:17").apply {
            withDatabaseName("acs_ball_by_ball")
        }
        container.start()
        try {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                assertGeneratedSchemaCanBeAppliedAndReapplied(connection, SqlDialect.POSTGRES)
            }
        } finally {
            container.stop()
        }
    }

    @Test
    fun `given generated SQLite schema and migrated schema then table contracts agree`() {
        val generated = DriverManager.getConnection("jdbc:sqlite::memory:")
        val migrated = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            applySchema(generated)
            applyMigrations(migrated)
            insertAlias(migrated)
            repeat(2) { applyMigrationAliasStatements(migrated) }

            expectThat(tableColumns(generated) - tableColumns(migrated)).isEqualTo(emptySet())
            expectThat(tableColumns(migrated) - tableColumns(generated)).isEqualTo(emptySet())
            expectThat(indexes(generated) - indexes(migrated)).isEqualTo(emptySet())
            expectThat(indexes(migrated) - indexes(generated)).isEqualTo(emptySet())
            expectThat(foreignKeys(generated) - foreignKeys(migrated)).isEqualTo(emptySet())
            expectThat(foreignKeys(migrated) - foreignKeys(generated)).isEqualTo(emptySet())
            assertAliasCount(migrated, 1)
        } finally {
            generated.close()
            migrated.close()
        }
    }

    private fun applySchema(connection: Connection) {
        generatedSchema(SqlDialect.SQLITE).split(';').map(String::trim).filter(String::isNotEmpty).forEach { sql ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    private fun assertGeneratedSchemaCanBeAppliedAndReapplied(connection: Connection, dialect: SqlDialect) {
        applyGeneratedSchema(connection, dialect)
        listOf("matches", "match_source_reference", "deliveries").forEach { table ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from $table").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(0)
                }
            }
        }
        assertAliasIndexExists(connection)

        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                    "values ('${AliasSourceSystem.CRICSHEET.value}', 'North Stars', 42)"
            )
        }
        assertAliasQueryReturnsOneRow(connection)
        assertThrows<SQLException> {
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "insert into team_name_aliases (source_system, source_team_name, stats_team_id) " +
                        "values ('${AliasSourceSystem.CRICSHEET.value}', 'North Stars', 42)"
                )
            }
        }

        applyGeneratedSchema(connection, dialect)

        assertAliasQueryReturnsOneRow(connection)
        assertAliasIndexExists(connection)
    }

    private fun applyGeneratedSchema(connection: Connection, dialect: SqlDialect) {
        generatedSchema(dialect).lineSequence()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { sql -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun assertAliasQueryReturnsOneRow(connection: Connection) {
        connection.prepareStatement(
            "select stats_team_id from team_name_aliases where stats_team_id = ?"
        ).use { statement ->
            statement.setInt(1, 42)
            statement.executeQuery().use { result ->
                expectThat(result.next()).isEqualTo(true)
                expectThat(result.getInt(1)).isEqualTo(42)
                expectThat(result.next()).isEqualTo(false)
            }
        }
    }

    private fun assertAliasIndexExists(connection: Connection) {
        connection.metaData.getIndexInfo(null, null, "team_name_aliases", false, false).use { result ->
            val indexes = buildSet {
                while (result.next()) result.getString("INDEX_NAME")?.let(::add)
            }
            expectThat(indexes.any { it.equals("idx_team_name_aliases_stats_team_id", ignoreCase = true) })
                .isEqualTo(true)
        }
    }

    private fun generatedSchema(dialect: SqlDialect): String {
        val stringWriter = StringWriter()
        BufferedWriter(stringWriter).use { writer -> RelationalSchemaSql.writeTo(writer, dialect) }
        return stringWriter.toString()
    }

    private fun tableNames(schema: String): Set<String> = Regex(
        "(?im)^\\s*CREATE TABLE(?: IF NOT EXISTS)?\\s+([A-Za-z_][A-Za-z0-9_]*)"
    ).findAll(schema).map { it.groupValues[1] }.toSet()

    private fun indexNames(schema: String): Set<String> = Regex(
        "(?im)^\\s*CREATE INDEX(?: IF NOT EXISTS)?\\s+([A-Za-z_][A-Za-z0-9_]*)"
    ).findAll(schema).map { it.groupValues[1] }.toSet()

    private fun applyMigrations(connection: Connection) {
        sqlStatements(Files.readString(Path.of("migrations/sqlite/1__initial_tables.sql")))
            .forEach { sql -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun applyMigrationAliasStatements(connection: Connection) {
        migrationStatements("sqlite", "1__initial_tables.sql")
            .filter { it.contains("team_name_aliases", ignoreCase = true) }
            .forEach { sql -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun migrationStatements(dialect: String, fileName: String): List<String> =
        sqlStatements(Files.readString(Path.of("migrations/$dialect/$fileName")))

    private fun insertAlias(connection: Connection) {
        connection.prepareStatement(
            "insert into team_name_aliases (source_system, source_team_name, stats_team_id) values (?, ?, ?)"
        ).use { statement ->
            statement.setString(1, AliasSourceSystem.CRICSHEET.value)
            statement.setString(2, "North Stars")
            statement.setInt(3, 42)
            statement.executeUpdate()
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

    private fun sqlStatements(script: String): List<String> = script.lineSequence()
        .filterNot { it.trimStart().startsWith("--") }
        .joinToString("\n")
        .split(';')
        .map(String::trim)
        .filter(String::isNotEmpty)

    private fun tableNames(connection: Connection): List<String> = connection.createStatement().use { statement ->
        statement.executeQuery("select name from sqlite_master where type = 'table' and name not like 'sqlite_%'")
            .use { result -> buildList { while (result.next()) add(result.getString("name")) }.sorted() }
    }

    private fun tableColumns(connection: Connection): Set<TableColumn> = tableNames(connection).flatMap { table ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(\"$table\")").use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            TableColumn(
                                table = table,
                                name = result.getString("name"),
                                type = result.getString("type"),
                                notNull = result.getInt("notnull") == 1,
                                defaultValue = result.getString("dflt_value"),
                                primaryKeyOrder = result.getInt("pk")
                            )
                        )
                    }
                }
            }
        }
    }.toSet()

    private fun indexes(connection: Connection): Set<IndexContract> = tableNames(connection).flatMap { table ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA index_list(\"$table\")").use { result ->
                buildList {
                    while (result.next()) {
                        val indexName = result.getString("name")
                        val columns = connection.createStatement().use { indexStatement ->
                            indexStatement.executeQuery("PRAGMA index_info(\"$indexName\")").use { indexResult ->
                                buildList {
                                    while (indexResult.next()) add(indexResult.getString("name"))
                                }
                            }
                        }
                        add(IndexContract(table, result.getInt("unique") == 1, columns))
                    }
                }
            }
        }
    }.toSet()

    private fun foreignKeys(connection: Connection): Set<ForeignKeyContract> = tableNames(connection).flatMap { table ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA foreign_key_list(\"$table\")").use { result ->
                buildList {
                    while (result.next()) {
                        add(
                            ForeignKeyContract(
                                table = table,
                                referencedTable = result.getString("table"),
                                fromColumn = result.getString("from"),
                                toColumn = result.getString("to"),
                                onUpdate = result.getString("on_update"),
                                onDelete = result.getString("on_delete")
                            )
                        )
                    }
                }
            }
        }
    }.toSet()

    private data class TableColumn(
        val table: String,
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKeyOrder: Int
    )

    private data class IndexContract(val table: String, val unique: Boolean, val columns: List<String>)

    private data class ForeignKeyContract(
        val table: String,
        val referencedTable: String,
        val fromColumn: String,
        val toColumn: String,
        val onUpdate: String,
        val onDelete: String
    )
}
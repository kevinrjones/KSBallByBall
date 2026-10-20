package com.knowledgespike.ballbyball.bulkupdate

import com.knowledgespike.ballbyball.bulkupdate.discovery.CsvDiscovery
import com.knowledgespike.ballbyball.bulkupdate.loaders.BulkLoader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class BulkUpdateServiceTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should discover and load CSV files into SQLite database`() {
        val dbPath = tempDir.resolve("test.db")
        val jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute(
                    """
                    CREATE TABLE people (
                        id INTEGER PRIMARY KEY,
                        name TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        val csvDir = tempDir.resolve("csv")
        Files.createDirectories(csvDir)
        Files.writeString(
            csvDir.resolve("people.csv"),
            """
            id,name
            1,Alice
            2,Bob
            """.trimIndent()
        )

        val config = AppConfig(
            connectionString = jdbcUrl,
            baseDirectory = tempDir.toAbsolutePath().toString(),
            csvDir = "csv",
            dbType = DbType.SQLite
        )

        val service = BulkUpdateService(config)
        val result = service.execute()

        expectThat(result.isSuccess).isTrue()

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM people")
                expectThat(rs.next()).isTrue()
                expectThat(rs.getInt(1)).isEqualTo(2)
            }
        }
    }

    @Test
    fun `should rollback transaction on loader failure`() {
        val dbPath = tempDir.resolve("test_rollback.db")
        val jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute("CREATE TABLE people (id INTEGER PRIMARY KEY, name TEXT)")
            }
        }

        val csvDir = tempDir.resolve("csv")
        Files.createDirectories(csvDir)
        Files.writeString(
            csvDir.resolve("people.csv"),
            """
            id,name
            1,Alice
            """.trimIndent()
        )

        val config = AppConfig(
            connectionString = jdbcUrl,
            baseDirectory = tempDir.toAbsolutePath().toString(),
            csvDir = "csv",
            dbType = DbType.SQLite
        )

        val failingLoader = object : BulkLoader {
            override fun load(connection: Connection, file: Path, tableName: String): Result<Unit> {
                connection.createStatement().use { it.execute("INSERT INTO people VALUES (1, 'Alice')") }
                return Result.failure(RuntimeException("Simulated bulk loader failure"))
            }
        }

        val service = BulkUpdateService(
            config = config,
            discovery = CsvDiscovery(),
            loaderFactory = { failingLoader }
        )

        val result = service.execute()

        expectThat(result.isFailure).isTrue()

        DriverManager.getConnection(jdbcUrl).use { connection ->
            connection.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT count(*) FROM people")
                expectThat(rs.next()).isTrue()
                expectThat(rs.getInt(1)).isEqualTo(0)
            }
        }
    }

    @Test
    fun `should succeed without error if no CSV files found`() {
        val dbPath = tempDir.resolve("test_empty.db")
        val jdbcUrl = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

        val config = AppConfig(
            connectionString = jdbcUrl,
            baseDirectory = tempDir.toAbsolutePath().toString(),
            csvDir = "nonexistent",
            dbType = DbType.SQLite
        )

        val service = BulkUpdateService(config)
        val result = service.execute()

        expectThat(result.isSuccess).isTrue()
    }
}

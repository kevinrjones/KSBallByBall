package com.knowledgespike.ballbyball.bulkupdate.loaders

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class SqliteBulkLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var connection: Connection

    @BeforeEach
    fun setUp() {
        val dbPath = tempDir.resolve("test.db")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbPath.toAbsolutePath()}")
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `should load valid CSV data into SQLite table`() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE people (
                    id INTEGER PRIMARY KEY,
                    source_person_id TEXT,
                    full_name TEXT,
                    ca_id INTEGER
                )
                """.trimIndent()
            )
        }

        val csvFile = tempDir.resolve("people.csv")
        Files.writeString(
            csvFile,
            """
            id,source_person_id,full_name,ca_id
            1,p1,"O'Brien, Jr.",100
            2,p2,Player Two,\N
            3,p3,Player Three,
            """.trimIndent()
        )

        val loader = SqliteBulkLoader(batchSize = 2)
        val result = loader.load(connection, csvFile, "people")

        expectThat(result.isSuccess).isTrue()

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT count(*) FROM people")
            expectThat(rs.next()).isTrue()
            expectThat(rs.getInt(1)).isEqualTo(3)
        }

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT full_name, ca_id FROM people WHERE id = 1")
            expectThat(rs.next()).isTrue()
            expectThat(rs.getString("full_name")).isEqualTo("O'Brien, Jr.")
            expectThat(rs.getInt("ca_id")).isEqualTo(100)
        }

        connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT ca_id FROM people WHERE id = 2")
            expectThat(rs.next()).isTrue()
            expectThat(rs.getObject("ca_id")).isEqualTo(null)
        }
    }

    @Test
    fun `should parse complex quoted CSV rows correctly`() {
        val loader = SqliteBulkLoader()
        val row = loader.parseCsvRow("""1,"Smith, John",active,"says ""hello"", world",\N""")

        expectThat(row).isEqualTo(
            listOf("1", "Smith, John", "active", "says \"hello\", world", "\\N")
        )
    }

    @Test
    fun `should handle empty CSV file gracefully`() {
        connection.createStatement().use { stmt ->
            stmt.execute("CREATE TABLE empty_table (id INTEGER)")
        }

        val csvFile = tempDir.resolve("empty_table.csv")
        Files.writeString(csvFile, "")

        val loader = SqliteBulkLoader()
        val result = loader.load(connection, csvFile, "empty_table")

        expectThat(result.isSuccess).isTrue()
    }
}

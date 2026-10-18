package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.sql.Connection
import java.sql.DriverManager

class JdbcStatsPlayerRepositoryTest {

    @Test
    fun `given match with players when querying roster then returns correct players`() {
        withStatsDatabase { connection ->
            val repository = JdbcStatsPlayerRepository(connection)
            val roster = repository.playersForMatch(SourceCaId.from("a12345"))

            expectThat(roster).hasSize(2)
            val virat = roster.find { it.fullName == "Virat Kohli" }
            expectThat(virat).isNotNull()
            expectThat(virat?.playerId?.value).isEqualTo(101)
            expectThat(virat?.knownAs).isEqualTo("Virat Kohli")
            expectThat(virat?.surname).isEqualTo("Kohli")

            val rohit = roster.find { it.fullName == "Rohit Gurunath Sharma" }
            expectThat(rohit).isNotNull()
            expectThat(rohit?.playerId?.value).isEqualTo(102)
            expectThat(rohit?.knownAs).isEqualTo("Rohit Sharma")
            expectThat(rohit?.surname).isEqualTo("Sharma")
        }
    }

    @Test
    fun `given unknown match caId when querying roster then returns empty list`() {
        withStatsDatabase { connection ->
            val repository = JdbcStatsPlayerRepository(connection)
            val roster = repository.playersForMatch(SourceCaId.from("unknown_ca_id"))

            expectThat(roster).hasSize(0)
        }
    }

    @Test
    fun `given existing people in bbb database when preloading then returns existing mappings`() {
        withBbbDatabase { connection ->
            val repository = JdbcPlayerIdentityRepository(connection)
            val mappings = repository.loadAllPlayerMappings()

            expectThat(mappings).hasSize(2)
            expectThat(mappings["person_1"]?.value).isEqualTo(101)
            expectThat(mappings["person_2"]?.value).isEqualTo(102)
        }
    }

    @Test
    fun `given uncached player when saving mapping then updates or inserts in bbb database`() {
        withBbbDatabase { connection ->
            val repository = JdbcPlayerIdentityRepository(connection)

            // Update existing person with ca_id = 0
            repository.savePlayerMapping("person_unmapped", "Steve Smith", StatsPlayerId.from(103))
            val updatedMappings = repository.loadAllPlayerMappings()
            expectThat(updatedMappings["person_unmapped"]?.value).isEqualTo(103)

            // Insert new person not yet in table
            repository.savePlayerMapping("person_new", "Jasprit Jasbirsingh Bumrah", StatsPlayerId.from(104))
            val allMappings = repository.loadAllPlayerMappings()
            expectThat(allMappings["person_new"]?.value).isEqualTo(104)
        }
    }

    @Test
    fun `given thread safe player cache when initialized and updated then behaves correctly`() {
        val initial = mapOf("p1" to StatsPlayerId.from(100))
        val cache = ThreadSafePlayerCache(initial)

        expectThat(cache.size).isEqualTo(1)
        expectThat(cache["p1"]?.value).isEqualTo(100)
        expectThat(cache["p2"]).isNull()

        cache["p2"] = StatsPlayerId.from(200)
        expectThat(cache.size).isEqualTo(2)
        expectThat(cache["p2"]?.value).isEqualTo(200)
    }

    private fun withStatsDatabase(block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE Matches (
                        Id INTEGER PRIMARY KEY,
                        CaId TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE Players (
                        Id INTEGER PRIMARY KEY,
                        PlayerId INTEGER NOT NULL,
                        FullName TEXT NOT NULL,
                        SortNamePart TEXT NOT NULL,
                        OtherNamePart TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    CREATE TABLE PlayersMatches (
                        Id INTEGER PRIMARY KEY,
                        PlayerId INTEGER NOT NULL,
                        MatchId INTEGER NOT NULL,
                        FullName TEXT NOT NULL,
                        SortNamePart TEXT NOT NULL,
                        OtherNamePart TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                statement.execute("INSERT INTO Matches (Id, CaId) VALUES (1, 'a12345')")
                statement.execute("INSERT INTO Players (Id, PlayerId, FullName, SortNamePart, OtherNamePart) VALUES (1, 101, 'Virat Kohli', 'Kohli', 'Virat')")
                statement.execute("INSERT INTO Players (Id, PlayerId, FullName, SortNamePart, OtherNamePart) VALUES (2, 102, 'Rohit Gurunath Sharma', 'Sharma', 'Rohit')")
                statement.execute("INSERT INTO PlayersMatches (Id, PlayerId, MatchId, FullName, SortNamePart, OtherNamePart) VALUES (1, 1, 1, 'Virat Kohli', 'Kohli', 'Virat')")
                statement.execute("INSERT INTO PlayersMatches (Id, PlayerId, MatchId, FullName, SortNamePart, OtherNamePart) VALUES (2, 2, 1, 'Rohit Gurunath Sharma', 'Sharma', 'Rohit')")
            }
            block(connection)
        }
    }

    private fun withBbbDatabase(block: (Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE people (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        source_person_id TEXT NOT NULL UNIQUE,
                        full_name TEXT NOT NULL,
                        sort_name_part TEXT NOT NULL,
                        other_name_part TEXT NOT NULL,
                        ca_id INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                statement.execute("INSERT INTO people (source_person_id, full_name, sort_name_part, other_name_part, ca_id) VALUES ('person_1', 'Virat Kohli', 'Kohli', 'Virat', 101)")
                statement.execute("INSERT INTO people (source_person_id, full_name, sort_name_part, other_name_part, ca_id) VALUES ('person_2', 'Rohit Sharma', 'Sharma', 'Rohit', 102)")
                statement.execute("INSERT INTO people (source_person_id, full_name, sort_name_part, other_name_part, ca_id) VALUES ('person_unmapped', 'Steve Smith', 'Smith', 'Steve', 0)")
            }
            block(connection)
        }
    }
}

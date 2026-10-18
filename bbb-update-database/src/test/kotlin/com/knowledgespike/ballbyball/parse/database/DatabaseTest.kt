package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.MergeEvidence
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference

import com.knowledgespike.ballbyball.clishared.identity.getNameParts
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.csv.CsvOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.DeliveryRecord
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.ballbyball.parse.database.adapter.OutputAdapter
import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.clishared.schema.By
import com.knowledgespike.ballbyball.clishared.schema.Delivery
import com.knowledgespike.ballbyball.clishared.schema.Event
import com.knowledgespike.ballbyball.clishared.schema.Info
import com.knowledgespike.ballbyball.clishared.schema.Innings
import com.knowledgespike.ballbyball.clishared.schema.Outcome
import com.knowledgespike.ballbyball.clishared.schema.Over
import com.knowledgespike.ballbyball.clishared.schema.Player
import com.knowledgespike.ballbyball.clishared.schema.PlayersRegistry
import com.knowledgespike.ballbyball.clishared.schema.PowerPlays
import com.knowledgespike.ballbyball.clishared.schema.Runs
import com.knowledgespike.ballbyball.clishared.schema.Toss
import com.knowledgespike.ballbyball.clishared.schema.Wickets
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.knowledgespike.cricketarchive.InvalidStateException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.sql.DriverManager
import java.util.Locale
import java.util.UUID
import java.util.stream.Stream

class DatabaseTest {
    @Test
    fun `given a declared innings when written then the innings insert stores declared`() {
        val sql = writeMatchAndReadSql(
            cricSheetWithFielder().copy(
                innings = cricSheetWithFielder().innings.map { it.copy(declared = true) }
            )
        )

        expectThat(sql).contains(
            "INSERT INTO innings (id, match_id, innings_number, batting_team_id, bowling_team_id, declared)"
        )
        expectThat(sql).contains("VALUES (1, 1, 1, 1, 2, 1)")
    }

    @Test
    fun `given a match with peopleCaId in registry when written then people are inserted with caId`() {
        val base = cricSheetWithFielder()
        val matchWithCaId = base.copy(
            match = base.match.copy(
                registry = base.match.registry.copy(
                    peopleCaId = mapOf("batter-id" to 101, "bowler-id" to 102)
                )
            )
        )
        val sql = writeMatchAndReadSql(matchWithCaId)

        expectThat(sql).contains("INSERT INTO people (id, source_person_id, full_name, sort_name_part, other_name_part, ca_id)")
        expectThat(sql).contains("'batter-id', 'Batter', 'Batter', '', 101")
        expectThat(sql).contains("'bowler-id', 'Bowler', 'Bowler', '', 102")
    }

    @Test
    fun `given a failed match write when writing then the adapter rolls back`() {
        val adapter = FailingMatchOutputAdapter()

        assertThrows<IllegalStateException> {
            Database(adapter).writeMatch("/new/root/12345.json", cricSheetWithFielder())
        }

        expectThat(adapter.matchStarted).isEqualTo(true)
        expectThat(adapter.rollbackRequested).isEqualTo(true)
        expectThat(adapter.commitRequested).isEqualTo(false)
    }

    @Test
    fun `given an invalid wire source ca id when writing then tiny type rejects it at the boundary`() {
        val adapter = FailingMatchOutputAdapter()
        val sourceMatch = cricSheetWithFielder().let { match ->
            match.copy(match = match.match.copy(sourceCaId = "ca invalid"))
        }

        assertThrows<IllegalArgumentException> {
            Database(adapter).writeMatch("/new/root/12345.json", sourceMatch)
        }

        expectThat(adapter.matchStarted).isEqualTo(true)
        expectThat(adapter.rollbackRequested).isEqualTo(true)
        expectThat(adapter.commitRequested).isEqualTo(false)
    }

    @Test
    fun `given a failed people bootstrap when writing then the adapter rolls back`() {
        val adapter = FailingMatchOutputAdapter(failPeopleWrites = true)

        assertThrows<IllegalStateException> {
            Database(adapter).writeAllPeople(Stream.of(PersonEntity("person-1", "Player", 12)))
        }

        expectThat(adapter.matchStarted).isEqualTo(true)
        expectThat(adapter.rollbackRequested).isEqualTo(true)
        expectThat(adapter.commitRequested).isEqualTo(false)
    }

    @Test
    fun `given a csv match write that fails after staging rows then no failed rows are published`() {
        val output = Files.createTempDirectory("warehouse-csv-transaction")
        val failedMatch = cricSheetWithFielder().copy(
            innings = cricSheetWithFielder().innings.map { it.copy(team = "Unknown") }
        )

        assertThrows<InvalidStateException> {
            CsvOutputAdapter(output).use { adapter ->
                Database(adapter).writeMatch("/new/root/12345.json", failedMatch)
            }
        }

        expectThat(Files.exists(output.resolve("matches.csv"))).isEqualTo(false)
        expectThat(Files.exists(output.resolve("teams.csv"))).isEqualTo(false)
    }

    @Test
    fun `given a sql script match write that fails after staging rows then no failed rows are published`() {
        val output = Files.createTempFile("warehouse-sql-transaction", ".sql")
        val failedMatch = cricSheetWithFielder().copy(
            innings = cricSheetWithFielder().innings.map { it.copy(team = "Unknown") }
        )

        assertThrows<InvalidStateException> {
            SqlScriptOutputAdapter(output).use { adapter ->
                Database(adapter).writeMatch("/new/root/12345.json", failedMatch)
            }
        }

        expectThat(Files.readString(output)).not().contains("INSERT INTO matches")
    }

    private fun Database.writeMatch(fileName: String, cricSheet: BbbMatchData) {
        writeMatch(fileName, sampleEnvelope(cricSheet = cricSheet))
    }

    private fun sampleEnvelope(
        canonicalMatchId: CanonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
        sourceCaId: String? = null,
        cricSheet: BbbMatchData = cricSheetWithFielder()
    ): CanonicalMatchEnvelope {
        val sourceRecordId = SourceRecordId.from(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877"))
        return CanonicalMatchEnvelope(
            canonicalMatchId = canonicalMatchId,
            sources = listOf(
                SourceReference(
                    provider = ProviderId.from("cricsheet"),
                    providerRecordKey = "12345",
                    sourceRecordId = sourceRecordId,
                    rawContentDigest = Sha256Digest.from("0".repeat(64))
                )
            ),
            mergeEvidence = MergeEvidence.SingleSource(sourceRecordId),
            match = if (sourceCaId != null) cricSheet.copy(match = cricSheet.match.copy(sourceCaId = sourceCaId)) else cricSheet
        )
    }

    @Test
    fun `given a match with duplicate sourceCaId already assigned when writing then match is skipped and not inserted`() {
        val output = Files.createTempFile("warehouse-duplicate-source-ca", ".sql")
        val firstEnvelope = sampleEnvelope(
            canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
            sourceCaId = "a434804"
        )
        val secondEnvelope = sampleEnvelope(
            canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b1")),
            sourceCaId = "a434804"
        )

        SqlScriptOutputAdapter(output).use { adapter ->
            val database = Database(adapter)
            database.writeMatch("first.json", firstEnvelope)
            database.writeMatch("second.json", secondEnvelope)
            adapter.complete()
        }

        val sql = Files.readString(output)
        expectThat(sql).contains("'1890a7a8-f76d-5f36-89f7-39b0319044b0'")
        expectThat(sql).not().contains("'1890a7a8-f76d-5f36-89f7-39b0319044b1'")
    }

    @Test
    fun `given duplicate sourceCaId in database output when writing then duplicate match is not inserted`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            val script = java.io.StringWriter()
            java.io.BufferedWriter(script).use { writer ->
                com.knowledgespike.ballbyball.parse.database.adapter.RelationalSchemaSql.writeTo(
                    writer,
                    com.knowledgespike.ballbyball.parse.database.adapter.SqlDialect.SQLITE
                )
            }
            script.toString().split(';').map(String::trim).filter(String::isNotEmpty).forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }

            val firstEnvelope = sampleEnvelope(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
                sourceCaId = "a434804"
            )
            val secondEnvelope = sampleEnvelope(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b1")),
                sourceCaId = "a434804"
            )

            SqlOutputAdapter(connection).use { adapter ->
                val database = Database(adapter)
                database.writeMatch("first.json", firstEnvelope)
                database.writeMatch("second.json", secondEnvelope)
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from matches").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
                statement.executeQuery("select canonical_match_id from matches").use { result ->
                    result.next()
                    expectThat(result.getString(1)).isEqualTo("1890a7a8-f76d-5f36-89f7-39b0319044b0")
                }
            }
        }
    }
    @Test
    fun `given an existing database match when written again then no dependent rows are inserted`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table matches (
                        id integer primary key,
                        canonical_match_id varchar(36) not null unique,
                        public_match_id bigint unique
                    )
                    """.trimIndent()
                )
                statement.execute(
                    """
                    create table match_source_reference (
                        match_id integer not null,
                        provider varchar(100) not null,
                        provider_record_key varchar(255) not null,
                        source_record_id char(36) not null,
                        raw_content_digest char(64) not null
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id) " +
                        "values (7, '1890a7a8-f76d-5f36-89f7-39b0319044b0')"
                )
                statement.executeUpdate(
                    "insert into match_source_reference " +
                        "(match_id, provider, provider_record_key, source_record_id, raw_content_digest) " +
                        "values (7, 'cricsheet', '12345', 'c61f62bd-7b02-5c3f-ae44-572d533b5877', '${"0".repeat(64)}')"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                Database(adapter).writeMatch("/new/root/12345.json", cricSheetWithFielder())
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from matches").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
                statement.executeQuery("select public_match_id from matches where id = 7").use { result ->
                    result.next()
                    expectThat(result.getLong(1)).isEqualTo(7_922_450_146L)
                }
            }
        }
    }

    @Test
    fun `given female t20 match when written then warehouse match type is wtt`() {
        val base = cricSheetWithFielder()
        val cricSheet = base.copy(
            match = base.match.copy(
                event = Event("Women's T20 Challenge"),
                gender = "female",
                matchType = "wtt"
            )
        )
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "12345.json",
                cricSheet = cricSheet
            )
            adapter.complete()
        }

        expectThat(Files.readString(output)).contains("'wtt'")
    }

    @Test
    fun `given female match outside excluded competitions when match is written then Women is appended to team names`() {
        val base = cricSheetWithFielder()
        val sql = writeMatchAndReadSql(
            base.copy(
                match = base.match.copy(gender = "female", event = Event("County Championship"))
            )
        )

        expectThat(sql).contains("'Home Women'")
        expectThat(sql).contains("'Away Women'")
    }

    @Test
    fun `given female match in Women's Cricket Super League when match is written then team names remain unchanged`() {
        val base = cricSheetWithFielder()
        val sql = writeMatchAndReadSql(
            base.copy(match = base.match.copy(gender = "female", event = Event("Women's Cricket Super League"))),
            competitionName = "Women's Cricket Super League"
        )

        expectThat(sql).contains("'Home'")
        expectThat(sql).contains("'Away'")
        expectThat(sql.contains("'Home Women'")).isEqualTo(false)
    }

    @Test
    fun `given female match in Women's T20 Challenge when match is written then team names remain unchanged`() {
        val base = cricSheetWithFielder()
        val sql = writeMatchAndReadSql(
            base.copy(match = base.match.copy(gender = "female", event = Event("Women's T20 Challenge"))),
            competitionName = "Women's T20 Challenge"
        )

        expectThat(sql).contains("'Home'")
        expectThat(sql).contains("'Away'")
        expectThat(sql.contains("'Home Women'")).isEqualTo(false)
    }
    @Test
    fun `given wicket fielder when match is written then fielder association is emitted`() {
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "12345.json",
                cricSheet = cricSheetWithFielder(Player(name = "Fielder"))
            )
            adapter.complete()
        }

        val sql = Files.readString(output)
        expectThat(sql).contains(
            "INSERT INTO delivery_fielders (delivery_id, wicket_id, person_id) VALUES (1, 1, 4);"
        )
    }

    @Test
    fun `given duplicate wicket fielder entries when match is written then one fielder bridge row is emitted`() {
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "12345.json",
                cricSheet = cricSheetWithFielders(listOf(Player(name = "Fielder"), Player(name = "Fielder")))
            )
            adapter.complete()
        }

        val sql = Files.readString(output)
        expectThat(sql.lines().count { it.startsWith("INSERT INTO delivery_fielders") }).isEqualTo(1)
    }

    @Test
    fun `given substitute wicket fielder without name when match is written then unknown substitute person and bridge row are emitted`() {
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "12345.json",
                cricSheet = cricSheetWithFielder(Player(name = null, substitute = true))
            )
            adapter.complete()
        }

        val sql = Files.readString(output)
        expectThat(sql).contains(
            "INSERT INTO people (id, source_person_id, full_name, sort_name_part, other_name_part, ca_id) VALUES (5, 'unknown', '[substitute]', '[substitute]', '', 0);"
        )
        expectThat(sql).contains(
            "INSERT INTO delivery_fielders (delivery_id, wicket_id, person_id) VALUES (1, 1, 5);"
        )
    }

    @Test
    fun `given wicket fielder with no name and not substitute when match is written then InvalidStateException is thrown`() {
        val output = Files.createTempFile("warehouse", ".sql")

        assertThrows<InvalidStateException> {
            SqlScriptOutputAdapter(output).use { adapter ->
                Database(adapter).writeMatch(
                    fileName = "12345.json",
                    cricSheet = cricSheetWithFielder(Player(name = null, substitute = null))
                )
            }
        }
    }

    @Test
    fun `given player with substitute boolean in json when deserialized then returns correct player`() {
        val json = """{"name": null, "substitute": true}"""
        val player = Json.decodeFromString<Player>(json)
        expectThat(player.substitute).isEqualTo(true)
        expectThat(player.name).isEqualTo(null)
    }

    @Test
    fun `given parenthesized player name when getNameParts is called then name is not erased`() {
        val (sortNamePart, otherNamePart) = getNameParts("Bob Willis (sub)")
        expectThat(sortNamePart).isEqualTo("BobWillis")
        expectThat(otherNamePart).isEqualTo("")

        val (initialSort, initialOther) = getNameParts("GS Sobers (c)")
        expectThat(initialOther).isEqualTo("GS")
        expectThat(initialSort).isEqualTo("Sobers")
    }

    @Test
    fun `given a Turkish default locale when parsing uppercase initials then casing remains locale independent`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            expectThat(getNameParts("INDIA Smith")).isEqualTo("Smith" to "INDIA")
        } finally {
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `given match with powerplays when match is written then powerplay is stored in deliveries`() {
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "12345.json",
                cricSheet = cricSheetWithPowerplays()
            )
            adapter.complete()
        }

        val sql = Files.readString(output)
        // delivery.powerplay should be 1
        expectThat(sql).contains("INSERT INTO deliveries")
        val deliveryLine = sql.lines().first { it.startsWith("INSERT INTO deliveries") }
        // The powerplay column value before wicket_count (which is 1 from the caught dismissal) should be 1
        expectThat(deliveryLine).contains(", 1, 1);")
    }

    @Test
    fun `given missing person in registry when Translate getPlayers is called then InvalidStateException is thrown`() {
        val cricSheet = cricSheetWithFielder()
        val playersJson = JsonObject(
            mapOf("Home" to JsonArray(listOf(JsonPrimitive("UnknownPlayer"))))
        )

        assertThrows<InvalidStateException> {
            com.knowledgespike.ballbyball.parse.parser.structure.Translate.getPlayers(playersJson, cricSheet)
        }
    }

    @Test
    fun `given valid officials and players when Translate is called then registry lookups map correctly`() {
        val cricSheet = cricSheetWithFielder()
        val players = com.knowledgespike.ballbyball.parse.parser.structure.Translate.getPlayers(
            cricSheet.match.players,
            cricSheet
        )
        expectThat(players["Home"]?.firstOrNull()?.id).isEqualTo("batter-id")
        expectThat(players["Home"]?.firstOrNull()?.name).isEqualTo("Batter")

        val officials = com.knowledgespike.ballbyball.parse.parser.structure.Translate.getOfficials(
            listOf("NonStriker"),
            cricSheet
        )
        expectThat(officials.firstOrNull()?.id).isEqualTo("non-striker-id")
    }

    private class FailingMatchOutputAdapter(private val failPeopleWrites: Boolean = false) : OutputAdapter {
        var matchStarted = false
        var rollbackRequested = false
        var commitRequested = false
        private val teams = mutableMapOf<String, Team>()

        override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = null
        override fun ensurePublicMatchId(
            canonicalMatchId: CanonicalMatchId,
            publicMatchId: com.knowledgespike.ballbyball.types.values.PublicMatchId
        ) = Unit
        override fun reconcileSourceCaId(canonicalMatchId: CanonicalMatchId, sourceCaId: SourceCaId?) = Unit
        override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long = 1
        override fun upsertTeam(name: String): Team = teams.getOrPut(name) { Team(teams.size + 1L, name) }
        override fun upsertGround(name: String): Ground = throw IllegalStateException("Simulated match write failure")
        override fun upsertDate(date: java.time.LocalDate): Int = 20240101
        override fun insertMatch(match: MatchRecord): MatchEntity = MatchEntity(1, match.publicMatchId)
        override fun insertSourceReferences(matchId: Long, sources: List<SourceReference>) = Unit
        override fun upsertInnings(
            matchId: Long,
            inningsNumber: Int,
            battingTeamId: Long,
            bowlingTeamId: Long,
            declared: Boolean
        ): InningsEntity = InningsEntity(1)
        override fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long? = null
        override fun insertDelivery(delivery: DeliveryRecord): Long = 1
        override fun insertWicket(kind: String): Long = 1
        override fun insertDeliveryWicket(deliveryId: Long, wicketId: Long) = Unit
        override fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long) = Unit
        override fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String) = Unit
        override fun writeAllPeople(people: Sequence<PersonEntity>) {
            if (failPeopleWrites) {
                people.first()
                throw IllegalStateException("Simulated people write failure")
            }
        }
        override fun beginMatch() {
            matchStarted = true
        }
        override fun commit() {
            commitRequested = true
        }
        override fun rollback() {
            rollbackRequested = true
        }
        override fun close() = Unit
    }

    private fun writeMatchAndReadSql(cricSheet: BbbMatchData, competitionName: String = "match"): String {
        val output = Files.createTempFile("warehouse", ".sql")
        val matchData = cricSheet.copy(match = cricSheet.match.copy(event = Event(competitionName)))
        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "12345.json",
                cricSheet = matchData
            )
            adapter.complete()
        }
        return Files.readString(output)
    }

    private fun cricSheetWithPowerplays(): BbbMatchData {
        val base = cricSheetWithFielder()
        val inningsWithPowerplay = base.innings.map { innings ->
            innings.copy(
                powerplays = listOf(PowerPlays(from = "0.1", to = "5.6", type = "mandatory"))
            )
        }
        return base.copy(innings = inningsWithPowerplay)
    }

    private fun cricSheetWithFielder(fielder: Player = Player(name = "Fielder")): BbbMatchData =
        cricSheetWithFielders(listOf(fielder))

    private fun cricSheetWithFielders(fielders: List<Player>): BbbMatchData = BbbMatchData(
        match = Info(
            ballsPerOver = 6,
            dates = listOf("2024-01-01"),
            gender = "male",
            matchType = "T20",
            outcome = Outcome(winner = "Home", by = By(runs = 1)),
            players = JsonObject(
                mapOf(
                    "Home" to JsonArray(listOf(JsonPrimitive("Batter"))),
                    "Away" to JsonArray(listOf(JsonPrimitive("Bowler")))
                )
            ),
            registry = PlayersRegistry(
                mapOf(
                    "Batter" to "batter-id",
                    "Bowler" to "bowler-id",
                    "NonStriker" to "non-striker-id",
                    "Fielder" to "fielder-id"
                )
            ),
            season = "2024",
            teamType = "international",
            teams = listOf("Home", "Away"),
            toss = Toss(decision = "bat", winner = "Home")
        ),
        innings = listOf(
            Innings(
                team = "Home",
                overs = listOf(
                    Over(
                        over = 0,
                        deliveries = listOf(
                            Delivery(
                                batter = "Batter",
                                bowler = "Bowler",
                                nonStriker = "NonStriker",
                                runs = Runs(batter = 0, extras = 0, total = 0),
                                wickets = listOf(
                                    Wickets(
                                        fielders = fielders,
                                        kind = "caught",
                                        playerOut = "Batter"
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )
    )
}
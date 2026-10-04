package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.MergeEvidence
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference

import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter
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
import java.util.UUID

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
    fun `given a failed match write when writing then the adapter rolls back`() {
        val adapter = FailingMatchOutputAdapter()

        assertThrows<IllegalStateException> {
            Database(adapter).writeMatch("/new/root/12345.json", cricSheetWithFielder())
        }

        expectThat(adapter.matchStarted).isEqualTo(true)
        expectThat(adapter.rollbackRequested).isEqualTo(true)
        expectThat(adapter.commitRequested).isEqualTo(false)
    }

    private fun Database.writeMatch(fileName: String, cricSheet: BbbMatchData) {
        val sourceRecordId = SourceRecordId.from(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877"))
        writeMatch(
            fileName,
            CanonicalMatchEnvelope(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
                sources = listOf(
                    SourceReference(
                        provider = ProviderId.from("cricsheet"),
                        providerRecordKey = "12345",
                        sourceRecordId = sourceRecordId,
                        rawContentDigest = Sha256Digest.from("0".repeat(64))
                    )
                ),
                mergeEvidence = MergeEvidence.SingleSource(sourceRecordId),
                match = cricSheet
            )
        )
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
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id) " +
                        "values (7, '1890a7a8-f76d-5f36-89f7-39b0319044b0')"
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
        }

        val sql = Files.readString(output)
        expectThat(sql).contains(
            "INSERT INTO delivery_fielders (delivery_id, wicket_id, person_id) VALUES (1, 1, 4) " +
                    "ON CONFLICT (delivery_id, wicket_id, person_id) DO NOTHING;"
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
        }

        val sql = Files.readString(output)
        expectThat(sql).contains(
            "INSERT INTO people (id, source_person_id, full_name, sort_name_part, other_name_part, ca_id) VALUES (5, 'unknown', '[substitute]', '[substitute]', '', 0);"
        )
        expectThat(sql).contains(
            "INSERT INTO delivery_fielders (delivery_id, wicket_id, person_id) VALUES (1, 1, 5) " +
                    "ON CONFLICT (delivery_id, wicket_id, person_id) DO NOTHING;"
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
    fun `given match with powerplays when match is written then powerplay is stored in deliveries`() {
        val output = Files.createTempFile("warehouse", ".sql")

        SqlScriptOutputAdapter(output).use { adapter ->
            Database(adapter).writeMatch(
                fileName = "12345.json",
                cricSheet = cricSheetWithPowerplays()
            )
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

    private class FailingMatchOutputAdapter : OutputAdapter {
        var matchStarted = false
        var rollbackRequested = false
        var commitRequested = false
        private val teams = mutableMapOf<String, Team>()

        override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = null
        override fun ensurePublicMatchId(
            canonicalMatchId: CanonicalMatchId,
            publicMatchId: com.knowledgespike.ballbyball.types.values.PublicMatchId
        ) = Unit
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
        override fun writeAllPeople(people: Sequence<PersonEntity>) = Unit
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
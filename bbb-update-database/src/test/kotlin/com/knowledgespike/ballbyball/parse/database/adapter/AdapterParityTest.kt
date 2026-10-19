package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.parse.database.MatchEntity
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalMatchSession
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRow
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRowOperation
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalSink
import com.knowledgespike.ballbyball.parse.database.adapter.csv.CsvOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlScriptOutputAdapter as SqliteSqlScriptOutputAdapter
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.cricketarchive.InvalidStateException
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.BufferedWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.util.UUID

class AdapterParityTest {
    @Test
    fun `given a match inserted twice when reimported then both paths reuse one row`() = withAdapters { session, jdbc ->
        val match = matchRecord(MATCH_ID)

        val sessionKeys = listOf(session.insertMatch(match).id, session.insertMatch(match).id)
        val jdbcKeys = listOf(jdbc.insertMatch(match).id, jdbc.insertMatch(match).id)

        expectThat(sessionKeys).isEqualTo(jdbcKeys)
        expectThat(session.matchFacts(MATCH_ID)).isEqualTo(jdbc.matchFacts(MATCH_ID))
    }

    @Test
    fun `given a match without source ca id when reconciled then session jdbc and csv facts agree`() =
        withAdapters { session, jdbc ->
            val csvOutput = Files.createTempDirectory("adapter-parity-csv")
            CsvOutputAdapter(csvOutput).use { csv ->
                session.beginMatch()
                jdbc.beginMatch()
                csv.beginMatch()
                session.insertMatch(matchRecord(MATCH_ID))
                jdbc.insertMatch(matchRecord(MATCH_ID))
                csv.insertMatch(matchRecord(MATCH_ID))
                session.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)
                jdbc.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)
                csv.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)
                session.commit()
                jdbc.commit()
                csv.commit()
                csv.complete()

                expectThat(csvMatchFacts(csvOutput, MATCH_ID)).isEqualTo(session.matchFacts(MATCH_ID))
            }

            expectThat(session.matchFacts(MATCH_ID)).isEqualTo(jdbc.matchFacts(MATCH_ID))
        }

    @Test
    fun `given a reused and reconciled match when sqlite script is applied then all adapter facts agree`() =
        withAdapters { session, jdbc ->
            val csvOutput = Files.createTempDirectory("adapter-parity-script-csv")
            val scriptOutput = Files.createTempFile("adapter-parity-script", ".sql")
            val match = matchRecord(MATCH_ID)

            CsvOutputAdapter(csvOutput).use { csv ->
                SqliteSqlScriptOutputAdapter(scriptOutput).use { script ->
                    session.beginMatch()
                    jdbc.beginMatch()
                    csv.beginMatch()
                    script.beginMatch()
                    repeat(2) { script.upsertTeam("Team ${it + 1}") }
                    repeat(2) { script.upsertGround("Unused ground ${it + 1}") }
                    script.upsertGround("Ground 3")

                    val sessionKeys = listOf(session.insertMatch(match).id, session.insertMatch(match).id)
                    val jdbcKeys = listOf(jdbc.insertMatch(match).id, jdbc.insertMatch(match).id)
                    val csvKeys = listOf(csv.insertMatch(match).id, csv.insertMatch(match).id)
                    val scriptKeys = listOf(script.insertMatch(match).id, script.insertMatch(match).id)

                    session.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)
                    jdbc.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)
                    csv.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)
                    script.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)

                    session.commit()
                    jdbc.commit()
                    csv.commit()
                    csv.complete()
                    script.commit()
                    script.complete()

                    expectThat(jdbcKeys).isEqualTo(sessionKeys)
                    expectThat(csvKeys).isEqualTo(sessionKeys)
                    expectThat(scriptKeys).isEqualTo(sessionKeys)
                }

                val scriptFacts = applyScriptAndReadMatchFacts(scriptOutput, MATCH_ID)
                expectThat(csvMatchFacts(csvOutput, MATCH_ID)).isEqualTo(session.matchFacts(MATCH_ID))
                expectThat(scriptFacts).isEqualTo(session.matchFacts(MATCH_ID))
            }
            expectThat(session.matchFacts(MATCH_ID)).isEqualTo(jdbc.matchFacts(MATCH_ID))
        }

    @Test
    fun `given a source ca id already owned when reused then both paths reject the conflict`() =
        withAdapters { session, jdbc ->
            val owner = matchRecord(MATCH_ID, sourceCaId = SOURCE_CA_ID)
            session.insertMatch(owner)
            jdbc.seedMatchWithPublicId(owner)

            val sessionRejected = runCatching {
                session.insertMatch(matchRecord(OTHER_MATCH_ID, sourceCaId = SOURCE_CA_ID))
            }.exceptionOrNull() is InvalidStateException
            val jdbcRejected = runCatching {
                jdbc.insertMatch(matchRecord(OTHER_MATCH_ID, sourceCaId = SOURCE_CA_ID))
            }.exceptionOrNull() is InvalidStateException

            expectThat(sessionRejected).isEqualTo(true)
            expectThat(jdbcRejected).isEqualTo(sessionRejected)
        }

    @Test
    fun `given a source ca id pending reconciliation when another match claims it then both paths reject`() =
        withAdapters { session, jdbc ->
            session.beginMatch()
            jdbc.beginMatch()
            session.insertMatch(matchRecord(MATCH_ID))
            jdbc.insertMatch(matchRecord(MATCH_ID))
            session.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)
            jdbc.reconcileSourceCaId(MATCH_ID, SOURCE_CA_ID)

            val sessionRejected = runCatching {
                session.insertMatch(matchRecord(OTHER_MATCH_ID, sourceCaId = SOURCE_CA_ID))
            }.exceptionOrNull() is InvalidStateException
            val jdbcRejected = runCatching {
                jdbc.insertMatch(matchRecord(OTHER_MATCH_ID, sourceCaId = SOURCE_CA_ID))
            }.exceptionOrNull() is InvalidStateException

            expectThat(sessionRejected).isEqualTo(true)
            expectThat(jdbcRejected).isEqualTo(sessionRejected)
            session.rollback()
            jdbc.rollback()
        }

    @Test
    fun `given an existing innings when reused then declared is updated consistently`() =
        withAdapters { session, jdbc ->
            val sessionMatch = session.insertMatch(matchRecord(MATCH_ID))
            val jdbcMatch = jdbc.insertMatch(matchRecord(MATCH_ID))

            session.upsertInnings(sessionMatch.id, 1, 10, 11, declared = false)
            jdbc.upsertInnings(jdbcMatch.id, 1, 10, 11, declared = false)
            session.upsertInnings(sessionMatch.id, 1, 10, 11, declared = true)
            jdbc.upsertInnings(jdbcMatch.id, 1, 10, 11, declared = true)

            expectThat(session.inningsDeclared(sessionMatch.id, 1)).isEqualTo(true)
            expectThat(session.inningsDeclared(sessionMatch.id, 1))
                .isEqualTo(jdbc.inningsDeclared(jdbcMatch.id, 1))
        }

    @Test
    fun `given an existing match without a public id when ensured then public id is backfilled`() =
        withAdapters { session, jdbc ->
            session.seedMatchWithoutPublicId(matchRecord(MATCH_ID))
            jdbc.seedMatchWithoutPublicId(matchRecord(MATCH_ID))
            session.ensurePublicMatchId(MATCH_ID, matchRecord(MATCH_ID).publicMatchId)
            jdbc.ensurePublicMatchId(MATCH_ID, matchRecord(MATCH_ID).publicMatchId)

            expectThat(session.matchFacts(MATCH_ID)).isEqualTo(jdbc.matchFacts(MATCH_ID))
        }

    @Test
    fun `given a public id owned by another match when ensured then both paths reject collision`() =
        withAdapters { session, jdbc ->
            val owner = matchRecord(MATCH_ID)
            val claimant = matchRecord(OTHER_MATCH_ID, publicMatchId = owner.publicMatchId)
            session.insertMatch(owner)
            jdbc.seedMatchWithPublicId(owner)

            val sessionRejected = runCatching {
                session.ensurePublicMatchId(OTHER_MATCH_ID, claimant.publicMatchId)
            }.exceptionOrNull() is InvalidStateException
            val jdbcRejected = runCatching {
                jdbc.ensurePublicMatchId(OTHER_MATCH_ID, claimant.publicMatchId)
            }.exceptionOrNull() is InvalidStateException

            expectThat(sessionRejected).isEqualTo(true)
            expectThat(jdbcRejected).isEqualTo(sessionRejected)
        }

    @Test
    fun `given duplicate fielder and match person rows when inserted then both paths retain one each`() =
        withAdapters { session, jdbc ->
            repeat(2) {
                session.insertDeliveryFielder(1, 2, 3)
                jdbc.insertDeliveryFielder(1, 2, 3)
                session.insertMatchPerson(4, 5, "PLAYER")
                jdbc.insertMatchPerson(4, 5, "PLAYER")
            }

            expectThat(session.deliveryFielderCount()).isEqualTo(1)
            expectThat(session.deliveryFielderCount()).isEqualTo(jdbc.deliveryFielderCount())
            expectThat(session.matchPersonCount()).isEqualTo(1)
            expectThat(session.matchPersonCount()).isEqualTo(jdbc.matchPersonCount())
        }

    @Test
    fun `given duplicate gate entries during a rolled back match when retried then one row is retained`() =
        withAdapters { session, jdbc ->
            session.beginMatch()
            jdbc.beginMatch()
            session.insertMatchPerson(1, 2, "player")
            jdbc.insertMatchPerson(1, 2, "player")
            session.rollback()
            jdbc.rollback()

            session.insertMatchPerson(1, 2, "player")
            jdbc.insertMatchPerson(1, 2, "player")

            expectThat(session.matchPersonCount()).isEqualTo(1)
            expectThat(jdbc.matchPersonCount()).isEqualTo(1)
        }

    @Test
    fun `given a partial match write when rolled back then neither path retains a match`() =
        withAdapters { session, jdbc ->
            session.beginMatch()
            jdbc.beginMatch()
            session.insertMatch(matchRecord(MATCH_ID))
            jdbc.insertMatch(matchRecord(MATCH_ID))
            session.rollback()
            jdbc.rollback()

            expectThat(session.matchFacts(MATCH_ID)).isEqualTo(null)
            expectThat(session.matchFacts(MATCH_ID)).isEqualTo(jdbc.matchFacts(MATCH_ID))
        }

    private fun withAdapters(block: (ParityHarness, ParityHarness) -> Unit) {
        val recording = RecordingHarness()
        val sqlite = SqliteHarness()
        try {
            block(recording, sqlite)
        } finally {
            recording.close()
            sqlite.close()
        }
    }

    private fun csvMatchFacts(output: Path, canonicalMatchId: CanonicalMatchId): MatchFacts? {
        val records = Files.readAllLines(output.resolve("matches.csv"))
        val columns = records.first().split(",")
        val values = records.drop(1).map { row -> columns.zip(row.split(",")).toMap() }
        return values.firstOrNull { it["canonical_match_id"] == canonicalMatchId.value.toString() }
            ?.let { row ->
                MatchFacts(
                    key = row.getValue("id").toLong(),
                    publicMatchId = row.getValue("public_match_id").takeUnless { it == "\\N" }?.toLong(),
                    sourceCaId = row.getValue("source_ca_id").takeUnless { it == "\\N" }
                )
            }
    }

    private fun applyScriptAndReadMatchFacts(scriptPath: Path, canonicalMatchId: CanonicalMatchId): MatchFacts? =
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            Files.readString(scriptPath).split(';').map(String::trim).filter(String::isNotEmpty).forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
            connection.prepareStatement(
                "select id, public_match_id, source_ca_id from matches where canonical_match_id = ?"
            ).use { statement ->
                statement.setString(1, canonicalMatchId.value.toString())
                statement.executeQuery().use { result ->
                    if (!result.next()) return@use null
                    val publicMatchId = result.getLong("public_match_id").let { if (result.wasNull()) null else it }
                    MatchFacts(result.getLong("id"), publicMatchId, result.getString("source_ca_id"))
                }
            }
        }

    private interface ParityHarness : AutoCloseable {
        fun insertMatch(match: MatchRecord): MatchEntity
        fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long?
        fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId)
        fun reconcileSourceCaId(canonicalMatchId: CanonicalMatchId, sourceCaId: SourceCaId?)
        fun upsertInnings(matchId: Long, number: Int, battingTeamId: Long, bowlingTeamId: Long, declared: Boolean): Long
        fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long)
        fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String)
        fun beginMatch()
        fun commit()
        fun rollback()
        fun seedMatchWithoutPublicId(match: MatchRecord)
        fun seedMatchWithPublicId(match: MatchRecord)
        fun matchFacts(canonicalMatchId: CanonicalMatchId): MatchFacts?
        fun inningsDeclared(matchId: Long, inningsNumber: Int): Boolean?
        fun deliveryFielderCount(): Int
        fun matchPersonCount(): Int
    }

    private data class MatchFacts(val key: Long, val publicMatchId: Long?, val sourceCaId: String?)

    private class RecordingHarness : ParityHarness {
        private val sink = RecordingSink()
        private val identityStore = IdentityCore()
        private val session = RelationalMatchSession(sink, identityStore)
        private val sinkTransaction = object : MatchTransactionParticipant {
            override fun begin() = sink.begin()

            override fun commit() = sink.commit()

            override fun rollback() = sink.rollback()
        }
        private val transaction = MatchTransaction(
            listOf(session.transactionParticipant(), sinkTransaction)
        )

        override fun insertMatch(match: MatchRecord) = session.insertMatch(match)
        override fun findMatchKey(canonicalMatchId: CanonicalMatchId) = session.findMatchKey(canonicalMatchId)
        override fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) =
            session.ensurePublicMatchId(canonicalMatchId, publicMatchId)
        override fun reconcileSourceCaId(canonicalMatchId: CanonicalMatchId, sourceCaId: SourceCaId?) =
            session.reconcileSourceCaId(canonicalMatchId, sourceCaId)

        override fun upsertInnings(
            matchId: Long,
            number: Int,
            battingTeamId: Long,
            bowlingTeamId: Long,
            declared: Boolean
        ) = session.upsertInnings(matchId, number, battingTeamId, bowlingTeamId, declared).id

        override fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long) =
            session.insertDeliveryFielder(deliveryId, wicketId, personId)

        override fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String) =
            session.insertMatchPerson(matchId, personId, roleCode)

        override fun beginMatch() = transaction.begin()

        override fun commit() = transaction.commit()

        override fun rollback() = transaction.rollback()

        override fun seedMatchWithoutPublicId(match: MatchRecord) {
            session.insertMatch(match)
            identityStore.setMatchPublicId(match.canonicalMatchId, null)
            sink.clearMatchPublicId(match.canonicalMatchId)
        }

        override fun seedMatchWithPublicId(match: MatchRecord) {
            session.insertMatch(match)
        }

        override fun matchFacts(canonicalMatchId: CanonicalMatchId): MatchFacts? =
            sink.matchFacts(canonicalMatchId)

        override fun inningsDeclared(matchId: Long, inningsNumber: Int): Boolean? =
            sink.inningsDeclared(matchId, inningsNumber)

        override fun deliveryFielderCount() = sink.rows.count { it.tableName == "delivery_fielders" }

        override fun matchPersonCount() = sink.rows
            .count { it.tableName == "match_people" }

        override fun close() = Unit
    }

    private class RecordingSink : RelationalSink {
        val rows = mutableListOf<RelationalRow>()
        private var transactionSnapshot: List<RelationalRow>? = null

        override fun emit(row: RelationalRow): Long? {
            if (row.operation == RelationalRowOperation.UPDATE) {
                applyUpdate(row)
            } else {
                rows += row
            }
            return null
        }

        fun begin() {
            transactionSnapshot = rows.toList()
        }

        fun commit() {
            transactionSnapshot = null
        }

        fun rollback() {
            transactionSnapshot?.let { snapshot ->
                rows.clear()
                rows.addAll(snapshot)
            }
            transactionSnapshot = null
        }

        fun clearMatchPublicId(canonicalMatchId: CanonicalMatchId) {
            val index = rows.indexOfLast {
                it.tableName == "matches" && it.values.getOrNull(1) == canonicalMatchId.value.toString()
            }
            if (index >= 0) {
                val row = rows[index]
                rows[index] = row.copy(values = row.values.toMutableList().also { it[2] = null })
            }
        }

        fun matchFacts(canonicalMatchId: CanonicalMatchId): MatchFacts? =
            rows.lastOrNull {
                it.tableName == "matches" && it.values.getOrNull(1) == canonicalMatchId.value.toString()
            }?.let { row ->
                MatchFacts(
                    key = row.values[0] as Long,
                    publicMatchId = row.values[2] as Long?,
                    sourceCaId = row.values[3] as String?
                )
            }

        fun inningsDeclared(matchId: Long, inningsNumber: Int): Boolean? = rows.lastOrNull {
            it.tableName == "innings" && it.values.getOrNull(1) == matchId && it.values.getOrNull(2) == inningsNumber
        }?.values?.get(5) as Boolean?

        private fun applyUpdate(update: RelationalRow) {
            val rowIndex = rows.indexOfLast { existing ->
                existing.tableName == update.tableName && update.whereColumns.indices.all { index ->
                    existing.values[existing.columns.indexOf(update.whereColumns[index])] == update.whereValues[index]
                }
            }
            check(rowIndex >= 0) { "No recorded ${update.tableName} row matches the requested update" }
            val existing = rows[rowIndex]
            val updatedValues = existing.columns.zip(existing.values).toMap().toMutableMap()
            update.columns.zip(update.values).forEach { (column, value) -> updatedValues[column] = value }
            rows[rowIndex] = existing.copy(values = existing.columns.map(updatedValues::getValue))
        }

    }

    private class SqliteHarness : ParityHarness {
        private val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        private val adapter: SqlOutputAdapter

        init {
            createSchema(connection)
            seedMatchReferences(connection)
            adapter = SqlOutputAdapter(connection)
        }

        override fun insertMatch(match: MatchRecord) = adapter.insertMatch(match)
        override fun findMatchKey(canonicalMatchId: CanonicalMatchId) = adapter.findMatchKey(canonicalMatchId)
        override fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) =
            adapter.ensurePublicMatchId(canonicalMatchId, publicMatchId)
        override fun reconcileSourceCaId(canonicalMatchId: CanonicalMatchId, sourceCaId: SourceCaId?) =
            adapter.reconcileSourceCaId(canonicalMatchId, sourceCaId)

        override fun upsertInnings(
            matchId: Long,
            number: Int,
            battingTeamId: Long,
            bowlingTeamId: Long,
            declared: Boolean
        ) = adapter.upsertInnings(matchId, number, battingTeamId, bowlingTeamId, declared).id

        override fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long) {
            seedBridgeRows()
            adapter.insertDeliveryFielder(deliveryId, wicketId, personId)
        }

        override fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String) {
            seedBridgeRows()
            adapter.insertMatchPerson(matchId, personId, roleCode)
        }

        override fun beginMatch() = adapter.beginMatch()
        override fun commit() = adapter.commit()
        override fun rollback() = adapter.rollback()

        override fun seedMatchWithoutPublicId(match: MatchRecord) = seedMatchRow(match, includePublicMatchId = false)

        override fun seedMatchWithPublicId(match: MatchRecord) = seedMatchRow(match, includePublicMatchId = true)

        private fun seedMatchRow(match: MatchRecord, includePublicMatchId: Boolean) {
            connection.prepareStatement(
                """
                insert into matches (
                    canonical_match_id, public_match_id, source_ca_id, file_name, match_in_series,
                    match_type, event_name, match_date_text, season, match_start_year, match_start_date_id,
                    balls_per_over, added_timestamp, team1_id, team2_id, ground_id, toss_team_id,
                    toss_decision, victory_type, winner_team_id, loser_team_id, duration_days, margin
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, match.canonicalMatchId.value.toString())
                if (includePublicMatchId) {
                    statement.setLong(2, match.publicMatchId.value)
                } else {
                    statement.setNull(2, Types.BIGINT)
                }
                if (match.sourceCaId == null) statement.setNull(3, Types.VARCHAR)
                else statement.setString(3, match.sourceCaId.value)
                statement.setString(4, match.fileName)
                statement.setInt(5, match.matchInSeries)
                statement.setString(6, match.matchType)
                statement.setString(7, match.eventName)
                statement.setString(8, match.matchDateText)
                statement.setString(9, match.season)
                statement.setString(10, match.matchStartYear)
                statement.setInt(11, match.ballsPerOver)
                statement.setLong(12, match.team1Key)
                statement.setLong(13, match.team2Key)
                statement.setLong(14, match.groundKey)
                statement.setLong(15, match.tossTeamKey)
                statement.setString(16, match.tossDecision)
                statement.setString(17, match.victoryType)
                match.winnerTeamKey?.let { statement.setLong(18, it) } ?: statement.setNull(18, Types.BIGINT)
                match.loserTeamKey?.let { statement.setLong(19, it) } ?: statement.setNull(19, Types.BIGINT)
                statement.setInt(20, match.durationDays)
                statement.setInt(21, match.margin)
                statement.executeUpdate()
            }
        }

        override fun matchFacts(canonicalMatchId: CanonicalMatchId): MatchFacts? =
            connection.prepareStatement(
                "select id, public_match_id, source_ca_id from matches where canonical_match_id = ?"
            ).use { statement ->
                statement.setString(1, canonicalMatchId.value.toString())
                statement.executeQuery().use { result ->
                    if (!result.next()) return@use null
                    val publicMatchId = result.getLong("public_match_id").let { if (result.wasNull()) null else it }
                    MatchFacts(result.getLong("id"), publicMatchId, result.getString("source_ca_id"))
                }
            }

        override fun inningsDeclared(matchId: Long, inningsNumber: Int): Boolean? =
            connection.prepareStatement(
                "select declared from innings where match_id = ? and innings_number = ?"
            ).use { statement ->
                statement.setLong(1, matchId)
                statement.setInt(2, inningsNumber)
                statement.executeQuery().use { result ->
                    if (!result.next()) null else result.getBoolean("declared")
                }
            }

        override fun deliveryFielderCount() = rowCount("delivery_fielders")
        override fun matchPersonCount() = rowCount("match_people")

        override fun close() {
            adapter.close()
            connection.close()
        }

        private fun rowCount(table: String): Int = connection.createStatement().use { statement ->
            statement.executeQuery("select count(*) from $table").use { result ->
                result.next()
                result.getInt(1)
            }
        }

        private fun seedBridgeRows() {
            val matchExists = connection.prepareStatement("select 1 from matches where id = 4").use { statement ->
                statement.executeQuery().use { it.next() }
            }
            if (matchExists) return

            listOf(2L, 3L, 5L).forEach { personId ->
                connection.prepareStatement(
                    "insert into people (id, source_person_id, full_name, sort_name_part, other_name_part, ca_id) " +
                        "values (?, ?, ?, ?, '', ?)"
                ).use { statement ->
                    statement.setLong(1, personId)
                    statement.setString(2, "person-$personId")
                    statement.setString(3, "Person $personId")
                    statement.setString(4, "Person$personId")
                    statement.setLong(5, personId)
                    statement.executeUpdate()
                }
            }
            listOf(1L, 4L, 200L).forEach(::insertBridgeMatch)
            connection.prepareStatement(
                "insert into innings (id, match_id, innings_number, batting_team_id, bowling_team_id, declared) " +
                    "values (200, 200, 1, 10, 11, 0)"
            ).use { it.executeUpdate() }
            connection.prepareStatement(
                "insert into wickets (id, source_wicket_id, wicket_kind) values (2, 2, null)"
            ).use { it.executeUpdate() }
            connection.prepareStatement(
                """insert into deliveries (
                    id, source_ball_id, match_id, match_date_id, innings_id, batting_team_id, bowling_team_id,
                    batter_id, non_striker_id, bowler_id, over_number, ball_number, ball_in_over, innings_order,
                    batter_runs, extra_runs, total_runs, no_balls, wides, byes, leg_byes, non_boundary, powerplay,
                    wicket_count
                ) values (1, 1, 200, null, 200, 10, 11, 3, 3, 5, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, null, 0, 0)"""
            ).use { it.executeUpdate() }
        }

        private fun insertBridgeMatch(matchId: Long) {
            connection.prepareStatement(
                """insert into matches (
                    id, canonical_match_id, public_match_id, source_ca_id, file_name, match_in_series, match_type,
                    event_name, match_date_text, season, match_start_year, match_start_date_id, balls_per_over,
                    added_timestamp, team1_id, team2_id, ground_id, toss_team_id, toss_decision, victory_type,
                    winner_team_id, loser_team_id, duration_days, margin
                ) values (?, null, null, null, ?, 1, 'TEST', 'Fixture', '2026-01-01', '2026', '2026', null,
                    6, CURRENT_TIMESTAMP, 1, 2, 3, 1, 'bat', 'No result', null, null, 0, 0)"""
            ).use { statement ->
                statement.setLong(1, matchId)
                statement.setString(2, "fixture-$matchId.json")
                statement.executeUpdate()
            }
        }
    }

    private fun matchRecord(
        canonicalMatchId: CanonicalMatchId,
        sourceCaId: SourceCaId? = null,
        publicMatchId: PublicMatchId = PublicMatchId.from(7_922_450_146L)
    ) = MatchRecord(
        canonicalMatchId = canonicalMatchId,
        publicMatchId = publicMatchId,
        sourceCaId = sourceCaId,
        fileName = "match.json",
        matchInSeries = 1,
        matchType = "TEST",
        eventName = "Test event",
        matchDateText = "2026-10-09",
        season = "2026",
        matchStartYear = "2026",
        matchStartDateKey = null,
        ballsPerOver = 6,
        team1Key = 1,
        team2Key = 2,
        groundKey = 3,
        tossTeamKey = 1,
        tossDecision = "bat",
        victoryType = "No result",
        winnerTeamKey = null,
        loserTeamKey = null
    )

    private companion object {
        fun createSchema(connection: Connection) {
            val script = StringWriter()
            BufferedWriter(script).use { writer -> RelationalSchemaSql.writeTo(writer, SqlDialect.SQLITE) }
            script.toString().split(';').map(String::trim).filter(String::isNotEmpty).forEach { sql ->
                connection.createStatement().use { it.execute(sql) }
            }
        }

        fun seedMatchReferences(connection: Connection) {
            listOf(1L, 2L, 10L, 11L).forEach { teamId ->
                connection.prepareStatement(
                    "insert into teams (id, source_team_id, team_name) values (?, ?, ?)"
                ).use { statement ->
                    statement.setLong(1, teamId)
                    statement.setLong(2, teamId)
                    statement.setString(3, "Team $teamId")
                    statement.executeUpdate()
                }
            }
            connection.prepareStatement(
                "insert into grounds (id, source_ground_id, ground_name) values (3, 3, 'Ground 3')"
            ).use { it.executeUpdate() }
        }

        fun canonicalId(value: String) = CanonicalMatchId.from(UUID.fromString(value))

        val MATCH_ID = canonicalId("1890a7a8-f76d-5f36-89f7-39b0319044b0")
        val OTHER_MATCH_ID = canonicalId("1890a7a8-f76d-5f36-89f7-39b0319044b1")
        val SOURCE_CA_ID = SourceCaId.from("ca-12345")
    }
}
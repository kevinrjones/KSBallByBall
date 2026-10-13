package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalMatchSession
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRow
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRowOperation
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalSink
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.util.UUID

class RelationalMatchSessionTest {
    private val canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
    @Test
    fun `given the same team twice when upserting then emit one row and reuse its key`() {
        val sink = RecordingRelationalSink()
        val session = RelationalMatchSession(sink)

        val first = session.upsertTeam("North Stars")
        val second = session.upsertTeam("North Stars")

        expectThat(second.id).isEqualTo(first.id)
        expectThat(sink.rows).hasSize(1)
        expectThat(sink.rows.single().tableName).isEqualTo("teams")
        expectThat(sink.rows.single().values).isEqualTo(listOf(first.id, 1L, "North Stars"))
    }

    @Test
    fun `given a match without a source ca id when reconciled then commit persists its pending assignment`() {
        val sink = RecordingRelationalSink()
        val identityStore = IdentityCore()
        val session = RelationalMatchSession(sink, identityStore)
        val transaction = MatchTransaction(listOf(session.transactionParticipant()))

        transaction.begin()
        session.insertMatch(sampleMatch())
        session.reconcileSourceCaId(canonicalMatchId, SourceCaId.from("ca-12345"))
        transaction.commit()

        expectThat(identityStore.findMatchSourceCaId(canonicalMatchId)).isEqualTo("ca-12345")
        expectThat(session.pendingSourceCaIdUpdates()).isEqualTo(emptyMap())
        expectThat(session.findMatchKey(canonicalMatchId)).isEqualTo(1L)
    }

    @Test
    fun `given a rolled back source ca reconciliation then ownership and identity allocation are restored`() {
        val identityStore = IdentityCore()
        val session = RelationalMatchSession(RecordingRelationalSink(), identityStore)
        val transaction = MatchTransaction(listOf(session.transactionParticipant()))
        val laterCanonicalMatchId = CanonicalMatchId.from(UUID.fromString("2890a7a8-f76d-5f36-89f7-39b0319044b0"))

        transaction.begin()
        session.insertMatch(sampleMatch())
        session.reconcileSourceCaId(canonicalMatchId, SourceCaId.from("ca-12345"))
        transaction.rollback()

        expectThat(session.findMatchKey(canonicalMatchId)).isEqualTo(null)
        expectThat(identityStore.sourceCaOwnership("ca-12345").canonicalMatchOwners).isEqualTo(emptyList())

        transaction.begin()
        val laterMatch = session.insertMatch(sampleMatch().copy(canonicalMatchId = laterCanonicalMatchId))
        session.reconcileSourceCaId(laterCanonicalMatchId, SourceCaId.from("ca-12345"))
        transaction.commit()

        expectThat(laterMatch.id).isEqualTo(1L)
        expectThat(identityStore.findMatchSourceCaId(laterCanonicalMatchId)).isEqualTo("ca-12345")
    }

    @Test
    fun `given an existing match without a public id when ensured then session emits a backfill update`() {
        val sink = RecordingRelationalSink()
        val identityStore = IdentityCore()
        val session = RelationalMatchSession(sink, identityStore)
        val match = sampleMatch()
        session.insertMatch(match)
        identityStore.setMatchPublicId(canonicalMatchId, null)

        session.ensurePublicMatchId(canonicalMatchId, match.publicMatchId)

        val update = sink.rows.last()
        expectThat(update.operation).isEqualTo(RelationalRowOperation.UPDATE)
        expectThat(update.tableName).isEqualTo("matches")
        expectThat(update.columns).isEqualTo(listOf("public_match_id"))
        expectThat(update.values).isEqualTo(listOf(match.publicMatchId.value))
        expectThat(update.whereColumns).isEqualTo(listOf("canonical_match_id", "public_match_id"))
        expectThat(update.whereValues).isEqualTo(listOf(canonicalMatchId.value.toString(), null))
    }

    @Test
    fun `given an existing innings when declared changes then session emits only the declared update`() {
        val sink = RecordingRelationalSink()
        val session = RelationalMatchSession(sink)

        session.upsertInnings(matchId = 1, inningsNumber = 1, battingTeamId = 2, bowlingTeamId = 3, declared = false)
        session.upsertInnings(matchId = 1, inningsNumber = 1, battingTeamId = 2, bowlingTeamId = 3, declared = true)

        val update = sink.rows.last()
        expectThat(update.operation).isEqualTo(RelationalRowOperation.UPDATE)
        expectThat(update.tableName).isEqualTo("innings")
        expectThat(update.columns).isEqualTo(listOf("declared"))
        expectThat(update.values).isEqualTo(listOf(true))
        expectThat(update.whereColumns).isEqualTo(listOf("id"))
        expectThat(update.whereValues).isEqualTo(listOf(1L))
    }

    @Test
    fun `given a new match with a source ca id when inserted then one match row registers it`() {
        val sink = RecordingRelationalSink()
        val session = RelationalMatchSession(sink)

        session.insertMatch(sampleMatch().copy(sourceCaId = SourceCaId.from("ca-12345")))

        expectThat(sink.rows.count { it.tableName == "matches" }).isEqualTo(1)
        expectThat(sink.rows.single { it.tableName == "matches" }.values[3]).isEqualTo("ca-12345")
        expectThat(session.pendingSourceCaIdUpdates()).isEqualTo(emptyMap())
    }

    @Test
    fun `given a new match person when inserted then identity is allocated through the session`() {
        val sink = RecordingRelationalSink()
        val session = RelationalMatchSession(sink)

        session.insertMatchPerson(matchId = 7, personId = 5, roleCode = "PLAYER")

        val row = sink.rows.single()
        expectThat(row.tableName).isEqualTo("match_people")
        expectThat(row.columns).isEqualTo(listOf("id", "match_id", "person_id", "role_code"))
        expectThat(row.values).isEqualTo(listOf(1L, 7L, 5L, "PLAYER"))
        expectThat(row.generatedColumns).isEqualTo(emptySet())
    }

    private fun sampleMatch() = MatchRecord(
        canonicalMatchId = canonicalMatchId,
        fileName = "match.json",
        matchInSeries = 1,
        matchType = "TEST",
        eventName = "Test event",
        matchDateText = "2026-10-09",
        season = "2026",
        matchStartYear = "2026",
        matchStartDateKey = null,
        ballsPerOver = 6,
        team1Key = 1L,
        team2Key = 2L,
        groundKey = 3L,
        tossTeamKey = 1L,
        tossDecision = "bat",
        victoryType = "No result",
        winnerTeamKey = null,
        loserTeamKey = null
    )

    private class RecordingRelationalSink : RelationalSink {
        val rows = mutableListOf<RelationalRow>()

        override fun emit(row: RelationalRow): Long? {
            rows += row
            return null
        }
    }
}
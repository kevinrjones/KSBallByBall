package com.knowledgespike.ballbyball.clishared.identity

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.DeterministicIdentity
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.ProviderNamespaces
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import arrow.core.Either
import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.clishared.schema.Info
import com.knowledgespike.ballbyball.clishared.schema.Outcome
import com.knowledgespike.ballbyball.clishared.schema.PlayersRegistry
import com.knowledgespike.ballbyball.clishared.schema.Toss
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import strikt.assertions.isTrue
import java.util.UUID

class MatchIdentityTest {
    private val json = Json

    @Test
    fun `Cricsheet namespace and provider key produce the published UUIDv5 golden vector`() {
        val sourceRecordId = DeterministicIdentity.sourceRecordId(
            namespace = ProviderNamespaces.CRICSHEET,
            providerRecordKey = "12345"
        )

        expectThat(sourceRecordId.value).isEqualTo(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877"))
    }

    @Test
    fun `same key in distinct provider namespaces produces distinct identities`() {
        val cricsheetId = DeterministicIdentity.sourceRecordId(ProviderNamespaces.CRICSHEET, "12345")
        val alternativeId = DeterministicIdentity.sourceRecordId(ProviderNamespaces.TEST_PROVIDER, "12345")

        expectThat(cricsheetId).isNotEqualTo(alternativeId)
    }

    @Test
    fun `raw bytes produce the published SHA-256 golden vector`() {
        val digest = DeterministicIdentity.rawContentDigest("hello\n".encodeToByteArray())

        expectThat(digest.value).isEqualTo("5891b5b522d5df086d0ff0b110fbd9d21bb4fc7163af34d08286a2e846f6be03")
    }

    @Test
    fun `canonical identity produces the published ten digit public match ID`() {
        val canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))

        expectThat(DeterministicIdentity.publicMatchId(canonicalMatchId)).isEqualTo(
            PublicMatchId.from(7_922_450_146L)
        )
    }

    @Test
    fun `public match ID uses unsigned high bits and stays in range`() {
        val canonicalMatchId = CanonicalMatchId.from(UUID.fromString("00000000-0000-0000-0000-000000000002"))

        expectThat(DeterministicIdentity.publicMatchId(canonicalMatchId)).isEqualTo(
            PublicMatchId.from(1_323_862_774L)
        )
    }

    @Test
    fun `same canonical identity is repeatable and distinct identities produce distinct public IDs`() {
        val first = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
        val second = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b1"))

        expectThat(DeterministicIdentity.publicMatchId(first)).isEqualTo(
            DeterministicIdentity.publicMatchId(first)
        )
        expectThat(DeterministicIdentity.publicMatchId(first)).isNotEqualTo(
            DeterministicIdentity.publicMatchId(second)
        )
    }

    @Test
    fun `corrected bytes retain source identity and change digest`() {
        val sourceId = DeterministicIdentity.sourceRecordId(ProviderNamespaces.CRICSHEET, "12345")
        val originalDigest = DeterministicIdentity.rawContentDigest("original".encodeToByteArray())
        val correctedDigest = DeterministicIdentity.rawContentDigest("corrected".encodeToByteArray())

        expectThat(sourceId).isEqualTo(
            DeterministicIdentity.sourceRecordId(ProviderNamespaces.CRICSHEET, "12345")
        )
        expectThat(originalDigest).isNotEqualTo(correctedDigest)
    }

    @Test
    fun `source and canonical envelopes serialize as versioned primitive contracts and round trip`() {
        val source = sourceReference()
        val sourceEnvelope = SourceMatchEnvelope(source = source, match = sampleMatchData())
        val canonicalEnvelope = CanonicalMatchEnvelope(
            canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
            sources = listOf(source),
            mergeEvidence = MergeEvidence.SingleSource(source.sourceRecordId),
            match = sampleMatchData()
        )

        val sourceJson = json.encodeToString(sourceEnvelope)
        val canonicalJson = json.encodeToString(canonicalEnvelope)

        expectThat(json.decodeFromString<SourceMatchEnvelope>(sourceJson)).isEqualTo(sourceEnvelope)
        expectThat(json.decodeFromString<CanonicalMatchEnvelope>(canonicalJson)).isEqualTo(canonicalEnvelope)
        expectThat(sourceJson.contains("\"version\":1")).isTrue()
        expectThat(canonicalJson.contains("\"canonicalMatchId\":\"1890a7a8-f76d-5f36-89f7-39b0319044b0\"")).isTrue()
    }

    @Test
    fun `untrusted invalid tiny type values are rejected`() {
        expectThat(ProviderId.of(" ") is Either.Left).isTrue()
        expectThat(SourceRecordId.of("not-a-uuid") is Either.Left).isTrue()
        expectThat(CanonicalMatchId.of("not-a-uuid") is Either.Left).isTrue()
        expectThat(Sha256Digest.of("abc") is Either.Left).isTrue()
    }

    private fun sourceReference(): SourceReference = SourceReference(
        provider = ProviderId.from("cricsheet"),
        providerRecordKey = "12345",
        sourceRecordId = DeterministicIdentity.sourceRecordId(ProviderNamespaces.CRICSHEET, "12345"),
        rawContentDigest = DeterministicIdentity.rawContentDigest("hello\n".encodeToByteArray())
    )

    private fun sampleMatchData() = BbbMatchData(
        match = Info(
            ballsPerOver = 6,
            dates = listOf("2024-01-01"),
            gender = "male",
            matchType = "tt",
            outcome = Outcome(result = "draw"),
            players = JsonObject(emptyMap()),
            registry = PlayersRegistry(emptyMap()),
            season = "2024",
            teamType = "international",
            teams = listOf("Team A", "Team B"),
            toss = Toss(decision = "bat", winner = "Team A")
        ),
        innings = emptyList()
    )
}
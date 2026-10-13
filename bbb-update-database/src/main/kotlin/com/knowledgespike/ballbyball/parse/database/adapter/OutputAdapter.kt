package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.clishared.schema.Delivery
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.DeterministicIdentity
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.MatchEntity
import com.knowledgespike.ballbyball.parse.database.PersonEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.types.values.PublicMatchId

interface OutputAdapter : AutoCloseable {
    fun complete() {}

    fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long?

    fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId)

    fun reconcileSourceCaId(canonicalMatchId: CanonicalMatchId, sourceCaId: SourceCaId?)

    fun sourceReferencesChanged(
        canonicalMatchId: CanonicalMatchId,
        sources: List<SourceReference>
    ): Boolean = false

    fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long

    fun upsertTeam(name: String): Team

    fun upsertGround(name: String): Ground

    fun upsertDate(date: java.time.LocalDate): Int

    fun insertMatch(match: MatchRecord): MatchEntity

    fun insertSourceReferences(matchId: Long, sources: List<SourceReference>)

    fun upsertInnings(
        matchId: Long,
        inningsNumber: Int,
        battingTeamId: Long,
        bowlingTeamId: Long,
        declared: Boolean
    ): InningsEntity

    fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long?

    fun insertDelivery(delivery: DeliveryRecord): Long

    fun insertWicket(kind: String): Long

    fun insertDeliveryWicket(deliveryId: Long, wicketId: Long)

    fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long)

    fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String)

    fun writeAllPeople(people: Sequence<PersonEntity>)

    /** Starts a non-reentrant match transaction; nested calls are rejected. */
    fun beginMatch()

    /** Commits the active match transaction; throws when no match transaction is active. */
    fun commit()

    /** Rolls back the active match transaction; inactive rollback is a no-op. */
    fun rollback()

    override fun close()
}

data class MatchRecord(
    val canonicalMatchId: CanonicalMatchId,
    val publicMatchId: PublicMatchId = DeterministicIdentity.publicMatchId(canonicalMatchId),
    val sourceCaId: SourceCaId? = null,
    val fileName: String,
    val matchInSeries: Int,
    val matchType: String,
    val eventName: String,
    val matchDateText: String,
    val season: String,
    val matchStartYear: String,
    val matchStartDateKey: Int?,
    val ballsPerOver: Int,
    val durationDays: Int = 0,
    val margin: Int = 0,
    val matchCount: Int = 1,
    val team1Key: Long,
    val team2Key: Long,
    val groundKey: Long,
    val tossTeamKey: Long,
    val tossDecision: String?,
    val victoryType: String,
    val winnerTeamKey: Long?,
    val loserTeamKey: Long?
)

data class DeliveryRecord(
    val matchKey: Long,
    val matchDateKey: Int?,
    val inningsKey: Long,
    val battingTeamKey: Long,
    val bowlingTeamKey: Long,
    val batterKey: Long,
    val nonStrikerKey: Long,
    val bowlerKey: Long,
    val overNumber: Int,
    val ballNumber: Int,
    val ballInOver: Int,
    val inningsOrder: Int,
    val delivery: Delivery,
    val powerplay: Int
)

internal fun fingerprint(source: SourceReference): String = listOf(
    source.provider.value,
    source.providerRecordKey,
    source.sourceRecordId.value.toString(),
    source.rawContentDigest.value
).joinToString("|")

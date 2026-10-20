package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import java.time.LocalDate

internal interface MatchIdentityStore {
    fun findPersonKey(sourceId: String): Long?
    fun findPersonCaId(sourceId: String): Int?
    fun rememberPerson(sourceId: String, key: Long, caId: Int = 0)
    fun setPersonCaId(sourceId: String, caId: Int)
    fun findTeam(name: String): Team?
    fun rememberTeam(team: Team)
    fun findGround(name: String): Ground?
    fun rememberGround(ground: Ground)
    fun findDateKey(date: LocalDate): Int?
    fun rememberDate(date: LocalDate, key: Int)
    fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long?
    fun rememberMatchKey(canonicalMatchId: CanonicalMatchId, key: Long)
    fun findMatchPublicId(canonicalMatchId: CanonicalMatchId): PublicMatchId?
    fun findPublicMatchOwner(publicMatchId: PublicMatchId): Long?
    fun findMatchSourceCaId(canonicalMatchId: CanonicalMatchId): String?
    fun sourceCaOwnership(sourceCaId: String): SourceCaIdOwnership
    fun rememberMatch(canonicalMatchId: CanonicalMatchId, key: Long, publicMatchId: PublicMatchId)
    fun setMatchPublicId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId?)
    fun sourceCaIdAllocator(): SourceCaIdAllocator
    fun sourceReferencesChanged(matchKey: Long, sources: List<SourceReference>): Boolean
    fun rememberSourceReferences(matchKey: Long, sources: List<SourceReference>)
    fun findInnings(matchId: Long, inningsNumber: Int): InningsEntity?
    fun findInningsDeclared(matchId: Long, inningsNumber: Int): Boolean?
    fun rememberInnings(matchId: Long, inningsNumber: Int, innings: InningsEntity, declared: Boolean)
    fun setInningsDeclared(matchId: Long, inningsNumber: Int, declared: Boolean)
    fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long?
    fun rememberDelivery(matchId: Long, inningsId: Long, inningsOrder: Int, deliveryKey: Long)
    fun addMatchPerson(matchId: Long, personId: Long, roleCode: String): Boolean
    fun addDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long): Boolean
    fun allocateKey(sequence: IdentitySequence): IdentityKey
    fun snapshot(): IdentityStoreSnapshot
    fun restore(snapshot: IdentityStoreSnapshot)
    fun commit()
}

internal enum class IdentitySequence {
    PERSON,
    TEAM,
    TEAM_SOURCE,
    GROUND,
    GROUND_SOURCE,
    MATCH,
    MATCH_PERSON,
    INNINGS,
    DELIVERY,
    BALL_SOURCE,
    WICKET,
    WICKET_SOURCE
}

internal data class IdentityKey(val value: Long, val databaseGenerated: Boolean = false) {
    fun resolve(generatedKey: Long?): Long =
        if (databaseGenerated) requireNotNull(generatedKey) { "The row sink did not return a generated identity key" }
        else value
}

internal interface IdentityStoreSnapshot
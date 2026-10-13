package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.MatchEntity
import com.knowledgespike.ballbyball.parse.database.PersonEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalMatchSession
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import java.time.LocalDate

abstract class SessionBackedOutputAdapter internal constructor(
    internal val session: RelationalMatchSession
) : OutputAdapter {

    override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = session.findMatchKey(canonicalMatchId)

    override fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) =
        session.ensurePublicMatchId(canonicalMatchId, publicMatchId)

    override fun reconcileSourceCaId(canonicalMatchId: CanonicalMatchId, sourceCaId: SourceCaId?) =
        session.reconcileSourceCaId(canonicalMatchId, sourceCaId)

    override fun sourceReferencesChanged(canonicalMatchId: CanonicalMatchId, sources: List<SourceReference>): Boolean =
        session.sourceReferencesChanged(canonicalMatchId, sources)

    override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long =
        session.upsertPerson(sourceId, fullName, caId)

    override fun upsertTeam(name: String): Team = session.upsertTeam(name)

    override fun upsertGround(name: String): Ground = session.upsertGround(name)

    override fun upsertDate(date: LocalDate): Int = session.upsertDate(date)

    override fun insertMatch(match: MatchRecord): MatchEntity = session.insertMatch(match)

    override fun insertSourceReferences(matchId: Long, sources: List<SourceReference>) =
        session.insertSourceReferences(matchId, sources)

    override fun upsertInnings(
        matchId: Long,
        inningsNumber: Int,
        battingTeamId: Long,
        bowlingTeamId: Long,
        declared: Boolean
    ): InningsEntity = session.upsertInnings(matchId, inningsNumber, battingTeamId, bowlingTeamId, declared)

    override fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long? =
        session.findDeliveryKey(matchId, inningsId, inningsOrder)

    override fun insertDelivery(delivery: DeliveryRecord): Long = session.insertDelivery(delivery)

    override fun insertWicket(kind: String): Long = session.insertWicket(kind)

    override fun insertDeliveryWicket(deliveryId: Long, wicketId: Long) =
        session.insertDeliveryWicket(deliveryId, wicketId)

    override fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long) =
        session.insertDeliveryFielder(deliveryId, wicketId, personId)

    override fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String) =
        session.insertMatchPerson(matchId, personId, roleCode)

    override fun writeAllPeople(people: Sequence<PersonEntity>) = session.writeAllPeople(people)
}
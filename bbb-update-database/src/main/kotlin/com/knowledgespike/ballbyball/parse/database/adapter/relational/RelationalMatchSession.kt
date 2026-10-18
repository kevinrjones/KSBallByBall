package com.knowledgespike.ballbyball.parse.database.adapter.relational

import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.clishared.identity.getNameParts
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.MatchEntity
import com.knowledgespike.ballbyball.parse.database.PersonEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.adapter.DeliveryRecord
import com.knowledgespike.ballbyball.parse.database.adapter.IdentitySequence
import com.knowledgespike.ballbyball.parse.database.adapter.IdentityKey
import com.knowledgespike.ballbyball.parse.database.adapter.IdentityStoreSnapshot
import com.knowledgespike.ballbyball.parse.database.adapter.IdentityCore
import com.knowledgespike.ballbyball.parse.database.adapter.MatchIdentityStore
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.ballbyball.parse.database.adapter.MatchWriteDecision
import com.knowledgespike.ballbyball.parse.database.adapter.MatchTransactionParticipant
import com.knowledgespike.ballbyball.parse.database.adapter.RelationalWriteSupport
import com.knowledgespike.ballbyball.parse.database.adapter.SourceCaIdAssignment
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.cricketarchive.LoggerDelegate
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Owns relational match state and delegates persistence of constructed rows to one sink. */
internal class RelationalMatchSession(
    private val sink: RelationalSink,
    private val identityStore: MatchIdentityStore = IdentityCore()
) {
    private val log by LoggerDelegate()

    fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = identityStore.findMatchKey(canonicalMatchId)

    fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        when (decideMatchWrite(canonicalMatchId, publicMatchId)) {
            is MatchWriteDecision.Reuse -> backfillPublicMatchId(canonicalMatchId, publicMatchId)
            MatchWriteDecision.Insert -> Unit
        }
    }

    fun reconcileSourceCaId(canonicalMatchId: CanonicalMatchId, sourceCaId: SourceCaId?) {
        val sourceCaIdValue = sourceCaId?.value ?: return
        val assignment = identityStore.sourceCaIdAllocator().decideReconciliation(
            canonicalMatchId = canonicalMatchId,
            sourceCaId = sourceCaIdValue,
            existingSourceCaId = identityStore.findMatchSourceCaId(canonicalMatchId),
            ownership = identityStore.sourceCaOwnership(sourceCaIdValue),
            matchExists = identityStore.findMatchKey(canonicalMatchId) != null
        )
        if (assignment is SourceCaIdAssignment.PendingUpdate) {
            val matchKey = requireNotNull(identityStore.findMatchKey(canonicalMatchId)) {
                "Pending source CA assignment must refer to an existing match"
            }
            emitUpdate(
                tableName = "matches",
                columns = listOf("source_ca_id"),
                values = listOf(assignment.sourceCaId),
                whereColumns = listOf("id", "source_ca_id"),
                whereValues = listOf(matchKey, null)
            )
        }
        identityStore.sourceCaIdAllocator().apply(assignment)
    }

    fun sourceReferencesChanged(canonicalMatchId: CanonicalMatchId, sources: List<SourceReference>): Boolean =
        findMatchKey(canonicalMatchId)
            ?.let { matchKey -> identityStore.sourceReferencesChanged(matchKey, sources) }
            ?: false

    fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        identityStore.findPersonKey(sourceId)?.let { return it }
        val identityKey = identityStore.allocateKey(IdentitySequence.PERSON)
        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        val key = identityKey.resolve(
            emit(
                "people",
                RelationalWriteSupport.PERSON_COLUMNS,
                listOf(identityKey.value, sourceId, fullName, sortNamePart, otherNamePart, caId),
                generatedColumns = identityKey.generatedColumns()
            )
        )
        identityStore.rememberPerson(sourceId, key)
        return key
    }

    fun upsertTeam(name: String): Team {
        identityStore.findTeam(name)?.let { return it }
        val identityKey = identityStore.allocateKey(IdentitySequence.TEAM)
        val sourceId = identityStore.allocateKey(IdentitySequence.TEAM_SOURCE)
        val key = identityKey.resolve(
            emit(
                "teams",
                RelationalWriteSupport.TEAM_COLUMNS,
                listOf(identityKey.value, sourceId.value, name),
                generatedColumns = identityKey.generatedColumns()
            )
        )
        val team = Team(key, name)
        identityStore.rememberTeam(team)
        return team
    }

    fun upsertGround(name: String): Ground {
        identityStore.findGround(name)?.let { return it }
        val identityKey = identityStore.allocateKey(IdentitySequence.GROUND)
        val sourceId = identityStore.allocateKey(IdentitySequence.GROUND_SOURCE)
        val key = identityKey.resolve(
            emit(
                "grounds",
                RelationalWriteSupport.GROUND_COLUMNS,
                listOf(identityKey.value, sourceId.value, name),
                generatedColumns = identityKey.generatedColumns()
            )
        )
        val ground = Ground(key, name)
        identityStore.rememberGround(ground)
        return ground
    }

    fun upsertDate(date: LocalDate): Int {
        identityStore.findDateKey(date)?.let { return it }
        val dimensions = RelationalWriteSupport.dateDimensions(date)
        emit("dates", RelationalWriteSupport.DATE_COLUMNS, dimensions.values())
        identityStore.rememberDate(date, dimensions.dateKey)
        return dimensions.dateKey
    }

    fun insertMatch(match: MatchRecord): MatchEntity {
        val key = when (val decision = decideMatchWrite(match.canonicalMatchId, match.publicMatchId)) {
            is MatchWriteDecision.Reuse -> {
                backfillPublicMatchId(match.canonicalMatchId, match.publicMatchId)
                reconcileSourceCaId(match.canonicalMatchId, match.sourceCaId)
                return MatchEntity(decision.matchKey, match.publicMatchId)
            }
            MatchWriteDecision.Insert -> identityStore.allocateKey(IdentitySequence.MATCH)
        }
        val sourceCaAssignment = match.sourceCaId?.let { sourceCaId ->
            identityStore.sourceCaIdAllocator().decideRegistration(
                canonicalMatchId = match.canonicalMatchId,
                sourceCaId = sourceCaId.value,
                existingSourceCaId = identityStore.findMatchSourceCaId(match.canonicalMatchId),
                ownership = identityStore.sourceCaOwnership(sourceCaId.value)
            )
        } ?: SourceCaIdAssignment.NoOp
        rejectSourceCaIdAssignment(sourceCaAssignment)
        val generatedKey = key.resolve(
            emit(
                "matches",
                RelationalWriteSupport.MATCH_COLUMNS,
                RelationalWriteSupport.matchValues(key.value, match, LocalDateTime.now(ZoneOffset.UTC)),
                generatedColumns = key.generatedColumns()
            )
        )
        identityStore.rememberMatch(match.canonicalMatchId, generatedKey, match.publicMatchId)
        identityStore.sourceCaIdAllocator().apply(sourceCaAssignment)
        return MatchEntity(generatedKey, match.publicMatchId)
    }

    fun insertSourceReferences(matchId: Long, sources: List<SourceReference>) {
        identityStore.rememberSourceReferences(matchId, sources)
        sources.forEach { source ->
            emit(
                "match_source_reference",
                RelationalWriteSupport.SOURCE_REFERENCE_COLUMNS,
                RelationalWriteSupport.sourceReferenceValues(matchId, source)
            )
        }
    }

    fun upsertInnings(
        matchId: Long,
        inningsNumber: Int,
        battingTeamId: Long,
        bowlingTeamId: Long,
        declared: Boolean
    ): InningsEntity {
        identityStore.findInnings(matchId, inningsNumber)?.let { innings ->
            if (identityStore.findInningsDeclared(matchId, inningsNumber) != declared) {
                emitUpdate(
                    tableName = "innings",
                    columns = listOf("declared"),
                    values = listOf(declared),
                    whereColumns = listOf("id"),
                    whereValues = listOf(innings.id)
                )
                identityStore.setInningsDeclared(matchId, inningsNumber, declared)
            }
            return innings
        }
        val identityKey = identityStore.allocateKey(IdentitySequence.INNINGS)
        val inningsKey = identityKey.resolve(
            emit(
                "innings",
                RelationalWriteSupport.INNINGS_COLUMNS,
                RelationalWriteSupport.inningsValues(
                    inningsKey = identityKey.value,
                    matchId = matchId,
                    inningsNumber = inningsNumber,
                    battingTeamId = battingTeamId,
                    bowlingTeamId = bowlingTeamId,
                    declared = declared
                ),
                generatedColumns = identityKey.generatedColumns()
            )
        )
        val innings = InningsEntity(inningsKey)
        identityStore.rememberInnings(matchId, inningsNumber, innings, declared)
        return innings
    }

    fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long? =
        identityStore.findDeliveryKey(matchId, inningsId, inningsOrder)

    fun insertDelivery(delivery: DeliveryRecord): Long {
        val identityKey = identityStore.allocateKey(IdentitySequence.DELIVERY)
        val sourceBallId = identityStore.allocateKey(IdentitySequence.BALL_SOURCE)
        val key = identityKey.resolve(
            emit(
                "deliveries",
                RelationalWriteSupport.DELIVERY_COLUMNS,
                RelationalWriteSupport.deliveryValues(identityKey.value, sourceBallId.value, delivery),
                generatedColumns = identityKey.generatedColumns()
            )
        )
        identityStore.rememberDelivery(delivery.matchKey, delivery.inningsKey, delivery.inningsOrder, key)
        return key
    }

    fun insertWicket(kind: String): Long {
        val identityKey = identityStore.allocateKey(IdentitySequence.WICKET)
        val sourceWicketId = identityStore.allocateKey(IdentitySequence.WICKET_SOURCE)
        val key = identityKey.resolve(
            emit(
                "wickets",
                RelationalWriteSupport.WICKET_COLUMNS,
                listOf(identityKey.value, sourceWicketId.value, kind),
                generatedColumns = identityKey.generatedColumns()
            )
        )
        return key
    }

    fun insertDeliveryWicket(deliveryId: Long, wicketId: Long) {
        emit("delivery_wickets", RelationalWriteSupport.DELIVERY_WICKET_COLUMNS, listOf(deliveryId, wicketId))
    }

    fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long) {
        if (!identityStore.addDeliveryFielder(deliveryId, wicketId, personId)) {
            log.warn(
                "Duplicate delivery_fielder suppressed from relational output: deliveryId={}, wicketId={}, personId={}",
                deliveryId,
                wicketId,
                personId
            )
            return
        }
        emit("delivery_fielders", RelationalWriteSupport.DELIVERY_FIELDER_COLUMNS, listOf(deliveryId, wicketId, personId))
    }

    fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String) {
        if (!identityStore.addMatchPerson(matchId, personId, roleCode)) {
            log.warn(
                "Duplicate match_person suppressed: matchId={}, personId={}, roleCode={}",
                matchId,
                personId,
                roleCode
            )
            return
        }
        val identityKey = identityStore.allocateKey(IdentitySequence.MATCH_PERSON)
        identityKey.resolve(
            emit(
                tableName = "match_people",
                columns = RelationalWriteSupport.MATCH_PERSON_COLUMNS,
                values = listOf(identityKey.value, matchId, personId, roleCode),
                generatedColumns = identityKey.generatedColumns()
            )
        )
    }

    fun writeAllPeople(people: Sequence<PersonEntity>) {
        people.forEach { person -> upsertPerson(person.id, person.name, person.caId) }
    }

    fun transactionParticipant(): MatchTransactionParticipant = object : MatchTransactionParticipant {
        private var snapshot: IdentityStoreSnapshot? = null

        override fun begin() {
            check(snapshot == null) { "The relational session transaction is already active" }
            snapshot = identityStore.snapshot()
        }

        override fun commit() {
            identityStore.commit()
            snapshot = null
        }

        override fun rollback() {
            snapshot?.let(identityStore::restore)
            snapshot = null
        }
    }

    fun pendingSourceCaIdUpdates(): Map<CanonicalMatchId, String> =
        identityStore.sourceCaIdAllocator().pendingAssignments()

    private fun decideMatchWrite(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) =
        identityStore.findMatchKey(canonicalMatchId).let { existingMatchKey ->
            RelationalWriteSupport.matchWriteDecision(
                canonicalMatchId = canonicalMatchId,
                publicMatchId = publicMatchId,
                existingMatchKey = existingMatchKey,
                existingPublicMatchId = identityStore.findMatchPublicId(canonicalMatchId)?.value,
                publicIdOwnedByAnotherMatch = identityStore.findPublicMatchOwner(publicMatchId)
                    ?.let { it != existingMatchKey } ?: false
            )
        }

    private fun backfillPublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        if (identityStore.findMatchPublicId(canonicalMatchId) != null) return
        emitUpdate(
            tableName = "matches",
            columns = listOf("public_match_id"),
            values = listOf(publicMatchId.value),
            whereColumns = listOf("canonical_match_id", "public_match_id"),
            whereValues = listOf(canonicalMatchId.value.toString(), null)
        )
        identityStore.setMatchPublicId(canonicalMatchId, publicMatchId)
    }

    private fun rejectSourceCaIdAssignment(assignment: SourceCaIdAssignment) {
        if (assignment is SourceCaIdAssignment.Reject) throw assignment.failure
    }

    private fun emit(
        tableName: String,
        columns: List<String>,
        values: List<Any?>,
        generatedColumns: Set<String> = emptySet()
    ): Long? =
        sink.emit(
            RelationalRow(
                tableName = tableName,
                columns = columns.toList(),
                values = values.toList(),
                generatedColumns = generatedColumns.toSet()
            )
        )

    private fun IdentityKey.generatedColumns(): Set<String> = if (databaseGenerated) setOf("id") else emptySet()

    private fun emitUpdate(
        tableName: String,
        columns: List<String>,
        values: List<Any?>,
        whereColumns: List<String>,
        whereValues: List<Any?>
    ) {
        sink.emit(
            RelationalRow(
                tableName = tableName,
                columns = columns,
                values = values,
                operation = RelationalRowOperation.UPDATE,
                whereColumns = whereColumns,
                whereValues = whereValues
            )
        )
    }
}
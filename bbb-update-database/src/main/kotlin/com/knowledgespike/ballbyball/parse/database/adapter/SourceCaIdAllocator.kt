package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.cricketarchive.InvalidStateException

internal sealed interface SourceCaIdAssignment {
    data object NoOp : SourceCaIdAssignment

    data class RegisterNow(
        val canonicalMatchId: CanonicalMatchId,
        val sourceCaId: String
    ) : SourceCaIdAssignment

    data class PendingUpdate(
        val canonicalMatchId: CanonicalMatchId,
        val sourceCaId: String
    ) : SourceCaIdAssignment

    data class Reject(val failure: InvalidStateException) : SourceCaIdAssignment
}

internal data class SourceCaIdOwnership(
    val canonicalMatchOwners: List<CanonicalMatchId>,
    val uncanonicalMatchKey: Long? = null
)

/** Coordinates source CA-ID decisions and transaction state for every output format. */
internal class SourceCaIdAllocator {
    private val matchSourceCaIds = TransactionMap<CanonicalMatchId, String>()
    private val sourceCaOwners = TransactionMap<String, CanonicalMatchId>()
    private val pendingSourceCaIds = TransactionMap<CanonicalMatchId, String>()

    fun sourceCaId(canonicalMatchId: CanonicalMatchId): String? = matchSourceCaIds[canonicalMatchId]

    fun sourceCaOwner(sourceCaId: String): CanonicalMatchId? = sourceCaOwners[sourceCaId]

    fun pendingAssignments(): Map<CanonicalMatchId, String> = pendingSourceCaIds.toMap()

    fun decideRegistration(
        canonicalMatchId: CanonicalMatchId,
        sourceCaId: String?,
        existingSourceCaId: String?,
        ownership: SourceCaIdOwnership
    ): SourceCaIdAssignment = decideAssignment(canonicalMatchId, sourceCaId, existingSourceCaId, ownership) {
        if (existingSourceCaId == null) SourceCaIdAssignment.RegisterNow(canonicalMatchId, requireNotNull(sourceCaId))
        else SourceCaIdAssignment.NoOp
    }

    fun decideReconciliation(
        canonicalMatchId: CanonicalMatchId,
        sourceCaId: String?,
        existingSourceCaId: String?,
        ownership: SourceCaIdOwnership,
        matchExists: Boolean
    ): SourceCaIdAssignment {
        if (sourceCaId == null) return SourceCaIdAssignment.NoOp
        return decideAssignment(
            canonicalMatchId,
            sourceCaId,
            existingSourceCaId,
            ownership,
            validatePending = existingSourceCaId == null && matchExists
        ) {
            if (existingSourceCaId == null && matchExists) {
                SourceCaIdAssignment.PendingUpdate(canonicalMatchId, sourceCaId)
            } else {
                SourceCaIdAssignment.NoOp
            }
        }
    }

    fun apply(assignment: SourceCaIdAssignment) {
        when (assignment) {
            SourceCaIdAssignment.NoOp -> Unit
            is SourceCaIdAssignment.RegisterNow -> {
                sourceCaOwners[assignment.sourceCaId] = assignment.canonicalMatchId
                matchSourceCaIds[assignment.canonicalMatchId] = assignment.sourceCaId
            }
            is SourceCaIdAssignment.PendingUpdate -> pendingSourceCaIds[assignment.canonicalMatchId] = assignment.sourceCaId
            is SourceCaIdAssignment.Reject -> throw assignment.failure
        }
    }

    fun finish() {
        val assignments = pendingAssignments()
        assignments.forEach { (canonicalMatchId, sourceCaId) ->
            matchSourceCaIds[canonicalMatchId] = sourceCaId
            sourceCaOwners[sourceCaId] = canonicalMatchId
        }
        pendingSourceCaIds.clear()
        matchSourceCaIds.commit()
        sourceCaOwners.commit()
        pendingSourceCaIds.commit()
    }

    fun begin() {
        matchSourceCaIds.beginTransaction()
        sourceCaOwners.beginTransaction()
        pendingSourceCaIds.beginTransaction()
    }

    fun rollback() {
        matchSourceCaIds.rollback()
        sourceCaOwners.rollback()
        pendingSourceCaIds.rollback()
    }


    private fun decideAssignment(
        canonicalMatchId: CanonicalMatchId,
        sourceCaId: String?,
        existingSourceCaId: String?,
        ownership: SourceCaIdOwnership,
        validatePending: Boolean = true,
        assignment: () -> SourceCaIdAssignment
    ): SourceCaIdAssignment {
        if (sourceCaId == null) return SourceCaIdAssignment.NoOp
        return try {
            ownership.uncanonicalMatchKey?.let { matchKey ->
                throw InvalidStateException(
                    "sourceCaId $sourceCaId is assigned to match $matchKey without a canonical match id"
                )
            }
            val owners = ownership.canonicalMatchOwners.distinct()
            if (owners.size > 1) {
                throw InvalidStateException("sourceCaId $sourceCaId is assigned to multiple canonical matches")
            }
            RelationalWriteSupport.validateSourceCaId(
                canonicalMatchId,
                sourceCaId,
                existingSourceCaId,
                owners.firstOrNull()
            )
            if (validatePending) validatePendingUpdate(canonicalMatchId, sourceCaId)
            assignment()
        } catch (failure: InvalidStateException) {
            SourceCaIdAssignment.Reject(failure)
        }
    }

    private fun validatePendingUpdate(canonicalMatchId: CanonicalMatchId, sourceCaId: String) {
        pendingSourceCaIds[canonicalMatchId]?.let { pendingSourceCaId ->
            if (pendingSourceCaId != sourceCaId) {
                throw InvalidStateException(
                    "Canonical match ${canonicalMatchId.value} already has pending sourceCaId $pendingSourceCaId, " +
                        "received $sourceCaId"
                )
            }
        }
    }
}
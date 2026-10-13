package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.util.UUID

class SourceCaIdAllocatorTest {
    private val firstMatch = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
    private val secondMatch = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b1"))

    @Test
    fun `given an unassigned match when registering a source ca id then return register now`() {
        val allocator = SourceCaIdAllocator()

        val assignment = allocator.decideRegistration(
            canonicalMatchId = firstMatch,
            sourceCaId = "ca-12345",
            existingSourceCaId = null,
            ownership = SourceCaIdOwnership(emptyList())
        )

        expectThat(assignment).isA<SourceCaIdAssignment.RegisterNow>()
        allocator.apply(assignment)
        expectThat(allocator.sourceCaId(firstMatch)).isEqualTo("ca-12345")
    }

    @Test
    fun `given an existing match without a source ca id when reconciling then stage a pending update`() {
        val allocator = SourceCaIdAllocator()

        val assignment = allocator.decideReconciliation(
            canonicalMatchId = firstMatch,
            sourceCaId = "ca-12345",
            existingSourceCaId = null,
            ownership = SourceCaIdOwnership(emptyList()),
            matchExists = true
        )

        expectThat(assignment).isA<SourceCaIdAssignment.PendingUpdate>()
        allocator.apply(assignment)
        expectThat(allocator.pendingAssignments()).isEqualTo(mapOf(firstMatch to "ca-12345"))
    }

    @Test
    fun `given a match already registered with the same source ca id when registering again then return no op`() {
        val allocator = SourceCaIdAllocator()
        register(allocator, firstMatch, "ca-12345")

        val assignment = allocator.decideRegistration(
            canonicalMatchId = firstMatch,
            sourceCaId = "ca-12345",
            existingSourceCaId = allocator.sourceCaId(firstMatch),
            ownership = ownership(allocator, "ca-12345")
        )

        expectThat(assignment).isEqualTo(SourceCaIdAssignment.NoOp)
    }

    @Test
    fun `given a source ca id owned by another match when registering then return reject`() {
        val allocator = SourceCaIdAllocator()
        register(allocator, firstMatch, "ca-12345")

        val assignment = allocator.decideRegistration(
            canonicalMatchId = secondMatch,
            sourceCaId = "ca-12345",
            existingSourceCaId = allocator.sourceCaId(secondMatch),
            ownership = ownership(allocator, "ca-12345")
        )

        expectThat(assignment).isA<SourceCaIdAssignment.Reject>()
        expectThat(allocator.sourceCaId(secondMatch)).isEqualTo(null)
    }

    @Test
    fun `given a source ca id owned by an uncanonical match when registering then return reject`() {
        val allocator = SourceCaIdAllocator()

        val assignment = allocator.decideRegistration(
            canonicalMatchId = firstMatch,
            sourceCaId = "ca-12345",
            existingSourceCaId = null,
            ownership = SourceCaIdOwnership(canonicalMatchOwners = emptyList(), uncanonicalMatchKey = 55)
        )

        expectThat(assignment).isA<SourceCaIdAssignment.Reject>()
    }

    private fun register(allocator: SourceCaIdAllocator, matchId: CanonicalMatchId, sourceCaId: String) {
        val assignment = allocator.decideRegistration(
            canonicalMatchId = matchId,
            sourceCaId = sourceCaId,
            existingSourceCaId = allocator.sourceCaId(matchId),
            ownership = ownership(allocator, sourceCaId)
        )
        allocator.apply(assignment)
    }

    private fun ownership(allocator: SourceCaIdAllocator, sourceCaId: String): SourceCaIdOwnership =
        SourceCaIdOwnership(
            canonicalMatchOwners = listOfNotNull(allocator.sourceCaOwner(sourceCaId)) +
                allocator.pendingAssignments().filterValues { it == sourceCaId }.keys
        )
}
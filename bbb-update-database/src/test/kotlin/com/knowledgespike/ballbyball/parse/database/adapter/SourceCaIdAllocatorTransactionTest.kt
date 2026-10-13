package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.cricketarchive.InvalidStateException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.util.UUID

class SourceCaIdAllocatorTransactionTest {
    private val firstMatch = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
    private val secondMatch = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b1"))

    @Test
    fun `given a pending assignment when identity state is committed then it becomes current`() {
        val state = SourceCaIdAllocator()
        state.begin()
        register(state, firstMatch, null)
        reconcile(state, firstMatch, "ca-12345")

        state.finish()

        expectThat(state.pendingAssignments()).isEqualTo(emptyMap())
        expectThat(state.sourceCaId(firstMatch)).isEqualTo("ca-12345")
    }

    @Test
    fun `given two matches when the same source ca id is pending then the second assignment is rejected`() {
        val state = SourceCaIdAllocator()
        register(state, firstMatch, null)
        register(state, secondMatch, null)
        reconcile(state, firstMatch, "ca-12345")

        assertThrows<InvalidStateException> {
            reconcile(state, secondMatch, "ca-12345")
        }
    }

    @Test
    fun `given an active transaction when state changes then rollback returns the complete source state`() {
        val state = SourceCaIdAllocator()
        register(state, firstMatch, "ca-12345")
        state.begin()
        register(state, secondMatch, null)
        reconcile(state, secondMatch, "ca-67890")

        state.rollback()

        expectThat(state.sourceCaId(firstMatch)).isEqualTo("ca-12345")
        expectThat(state.sourceCaId(secondMatch)).isEqualTo(null)
        expectThat(state.sourceCaOwner("ca-67890")).isEqualTo(null)
        expectThat(state.pendingAssignments()).isEqualTo(emptyMap())
    }

    private fun register(state: SourceCaIdAllocator, matchId: CanonicalMatchId, sourceCaId: String?) {
        state.apply(
            state.decideRegistration(
                canonicalMatchId = matchId,
                sourceCaId = sourceCaId,
                existingSourceCaId = state.sourceCaId(matchId),
                ownership = sourceCaId?.let { ownership(state, it) } ?: SourceCaIdOwnership(emptyList())
            )
        )
    }

    private fun reconcile(state: SourceCaIdAllocator, matchId: CanonicalMatchId, sourceCaId: String) {
        state.apply(
            state.decideReconciliation(
                canonicalMatchId = matchId,
                sourceCaId = sourceCaId,
                existingSourceCaId = state.sourceCaId(matchId),
                ownership = ownership(state, sourceCaId),
                matchExists = true
            )
        )
    }

    private fun ownership(state: SourceCaIdAllocator, sourceCaId: String): SourceCaIdOwnership =
        SourceCaIdOwnership(
            canonicalMatchOwners = listOfNotNull(state.sourceCaOwner(sourceCaId)) +
                state.pendingAssignments().filterValues { it == sourceCaId }.keys
        )
}
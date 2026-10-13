package com.knowledgespike.ballbyball.parse.database.adapter

internal interface MatchTransactionParticipant {
    fun begin()

    fun prepareCommit() = Unit

    fun durableCommit() = Unit

    fun commit()

    fun rollback()
}

internal class MatchTransaction(
    private val participants: List<MatchTransactionParticipant>
) {
    var isActive: Boolean = false
        private set

    fun begin() {
        check(!isActive) { "A match transaction is already active" }
        val started = mutableListOf<MatchTransactionParticipant>()
        try {
            participants.forEach { participant ->
                participant.begin()
                started += participant
            }
            isActive = true
        } catch (failure: Exception) {
            rollbackParticipants(started.asReversed(), failure)
            throw failure
        }
    }

    fun commit() {
        check(isActive) { "No match transaction is active" }
        try {
            participants.forEach(MatchTransactionParticipant::prepareCommit)
        } catch (failure: Exception) {
            rollbackParticipants(participants.asReversed(), failure)
            isActive = false
            throw failure
        }

        try {
            participants.forEach(MatchTransactionParticipant::durableCommit)
        } catch (failure: Exception) {
            rollbackParticipants(participants.asReversed(), failure)
            isActive = false
            throw failure
        }

        val failure = commitParticipants()
        isActive = false
        failure?.let { throw it }
    }

    fun rollback() {
        if (!isActive) return
        var failure: Exception? = null
        participants.asReversed().forEach { participant ->
            try {
                participant.rollback()
            } catch (rollbackFailure: Exception) {
                val previousFailure = failure
                if (previousFailure == null) failure = rollbackFailure else previousFailure.addSuppressed(rollbackFailure)
            }
        }
        isActive = false
        failure?.let { throw it }
    }

    private fun rollbackParticipants(
        participantsToRollback: List<MatchTransactionParticipant>,
        failure: Exception
    ) {
        participantsToRollback.forEach { participant ->
            try {
                participant.rollback()
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
        }
    }

    private fun commitParticipants(failure: Exception? = null): Exception? {
        var commitFailure = failure
        participants.forEach { participant ->
            try {
                participant.commit()
            } catch (participantFailure: Exception) {
                val previousFailure = commitFailure
                if (previousFailure == null) {
                    commitFailure = participantFailure
                } else {
                    previousFailure.addSuppressed(participantFailure)
                }
            }
        }
        return commitFailure
    }
}
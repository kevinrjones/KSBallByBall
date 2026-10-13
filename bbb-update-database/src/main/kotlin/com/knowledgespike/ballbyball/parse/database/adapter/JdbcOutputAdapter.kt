package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalMatchSession
import java.sql.Connection

/** JDBC facade that delegates relational decisions to the shared match session. */
abstract class JdbcOutputAdapter(
    protected val connection: Connection
) : SessionBackedOutputAdapter(
    RelationalMatchSession(JdbcRowSink(connection), JdbcIdentityStore(connection))
) {
    private val connectionTransaction = object : MatchTransactionParticipant {
        override fun begin() {
            val previousAutoCommit = connection.autoCommit
            if (previousAutoCommit) connection.autoCommit = false
            autoCommitBeforeMatch = previousAutoCommit
        }

        override fun durableCommit() {
            if (!connection.autoCommit) connection.commit()
        }

        override fun commit() = restoreAutoCommit()

        override fun rollback() {
            try {
                connection.rollback()
            } finally {
                restoreAutoCommit()
            }
        }
    }
    private val transaction = MatchTransaction(listOf(session.transactionParticipant(), connectionTransaction))
    private var autoCommitBeforeMatch: Boolean? = null


    override fun beginMatch() = transaction.begin()

    override fun commit() = transaction.commit()

    override fun rollback() = transaction.rollback()

    override fun close() {
        if (transaction.isActive) transaction.rollback()
    }

    private fun restoreAutoCommit() {
        val previousAutoCommit = autoCommitBeforeMatch ?: return
        try {
            connection.autoCommit = previousAutoCommit
        } finally {
            autoCommitBeforeMatch = null
        }
    }
}
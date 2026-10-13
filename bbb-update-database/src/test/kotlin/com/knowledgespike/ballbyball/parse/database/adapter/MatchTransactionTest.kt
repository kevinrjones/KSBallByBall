package com.knowledgespike.ballbyball.parse.database.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class MatchTransactionTest {
    @Test
    fun `given no active transaction when committing then it is rejected`() {
        val transaction = MatchTransaction(emptyList())

        assertThrows<IllegalStateException> { transaction.commit() }
    }

    @Test
    fun `given active participants when committing then phases run in order`() {
        val events = mutableListOf<String>()
        val transaction = MatchTransaction(
            listOf(
                participant(
                    onBegin = { events += "begin-1" },
                    onPrepareCommit = { events += "prepare-1" },
                    onDurableCommit = { events += "durable-1" },
                    onCommit = { events += "commit-1" }
                ),
                participant(
                    onBegin = { events += "begin-2" },
                    onPrepareCommit = { events += "prepare-2" },
                    onDurableCommit = { events += "durable-2" },
                    onCommit = { events += "commit-2" }
                )
            )
        )

        transaction.begin()
        transaction.commit()

        expectThat(events.toList()).isEqualTo(
            listOf(
                "begin-1", "begin-2",
                "prepare-1", "prepare-2",
                "durable-1", "durable-2",
                "commit-1", "commit-2"
            )
        )
    }

    @Test
    fun `given prepare failure when committing then all participants roll back`() {
        val events = mutableListOf<String>()
        val transaction = MatchTransaction(
            listOf(
                participant(
                    onBegin = { events += "begin-1" },
                    onRollback = { events += "rollback-1" }
                ),
                participant(
                    onBegin = { events += "begin-2" },
                    onPrepareCommit = { error("prepare failed") },
                    onRollback = { events += "rollback-2" }
                )
            )
        )
        transaction.begin()

        assertThrows<IllegalStateException> { transaction.commit() }

        expectThat(events.toList()).isEqualTo(listOf("begin-1", "begin-2", "rollback-2", "rollback-1"))
        expectThat(transaction.isActive).isEqualTo(false)
    }

    @Test
    fun `given durable failure when committing then all participants roll back without finalization`() {
        val events = mutableListOf<String>()
        val transaction = MatchTransaction(
            listOf(
                participant(
                    onBegin = { events += "begin-1" },
                    onDurableCommit = { events += "durable-1" },
                    onCommit = { events += "commit-1" },
                    onRollback = { events += "rollback-1" }
                ),
                participant(
                    onBegin = { events += "begin-2" },
                    onDurableCommit = {
                        events += "durable-2"
                        error("durable failed")
                    },
                    onCommit = { events += "commit-2" },
                    onRollback = {
                        events += "rollback-2"
                        error("rollback failed")
                    }
                )
            )
        )
        transaction.begin()

        val failure = assertThrows<IllegalStateException> { transaction.commit() }

        expectThat(failure.message).isEqualTo("durable failed")
        expectThat(failure.suppressed.map { it.message }).isEqualTo(listOf("rollback failed"))
        expectThat(events.toList()).isEqualTo(
            listOf("begin-1", "begin-2", "durable-1", "durable-2", "rollback-2", "rollback-1")
        )
        expectThat(transaction.isActive).isEqualTo(false)

        transaction.rollback()

        expectThat(events.toList()).isEqualTo(
            listOf("begin-1", "begin-2", "durable-1", "durable-2", "rollback-2", "rollback-1")
        )
    }

    @Test
    fun `given mark failure after durable commit then remaining participants are finalized`() {
        val events = mutableListOf<String>()
        val transaction = MatchTransaction(
            listOf(
                participant(
                    onDurableCommit = { events += "durable-1" },
                    onCommit = {
                        events += "commit-1"
                        error("mark failed")
                    },
                    onRollback = { events += "rollback-1" }
                ),
                participant(
                    onDurableCommit = { events += "durable-2" },
                    onCommit = { events += "commit-2" },
                    onRollback = { events += "rollback-2" }
                )
            )
        )
        transaction.begin()

        val failure = assertThrows<IllegalStateException> { transaction.commit() }

        expectThat(failure.message).isEqualTo("mark failed")
        expectThat(events.toList()).isEqualTo(listOf("durable-1", "durable-2", "commit-1", "commit-2"))
        expectThat(transaction.isActive).isEqualTo(false)
    }

    @Test
    fun `given an active transaction when beginning another then it is rejected`() {
        var beginCount = 0
        val transaction = MatchTransaction(
            listOf(
                participant(onBegin = { beginCount++ })
            )
        )
        transaction.begin()

        assertThrows<IllegalStateException> { transaction.begin() }
        expectThat(beginCount).isEqualTo(1)
    }

    @Test
    fun `given no active transaction when rolling back then state is unchanged`() {
        var restored = false
        val transaction = MatchTransaction(
            listOf(
                participant(onRollback = { restored = true })
            )
        )

        transaction.rollback()

        expectThat(restored).isEqualTo(false)
    }

    private fun participant(
        onBegin: () -> Unit = {},
        onPrepareCommit: () -> Unit = {},
        onDurableCommit: () -> Unit = {},
        onCommit: () -> Unit = {},
        onRollback: () -> Unit = {}
    ): MatchTransactionParticipant = object : MatchTransactionParticipant {
        override fun begin() = onBegin()

        override fun prepareCommit() = onPrepareCommit()

        override fun durableCommit() = onDurableCommit()

        override fun commit() = onCommit()

        override fun rollback() = onRollback()
    }
}
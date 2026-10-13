package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.parse.database.adapter.IdentityCore
import com.knowledgespike.ballbyball.parse.database.adapter.MatchTransaction
import com.knowledgespike.ballbyball.parse.database.adapter.SessionBackedOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalMatchSession
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalSink
import java.nio.file.Path

/** Writes relational tables as UTF-8 CSV files suitable for bulk loading into MariaDB. */
class CsvOutputAdapter private constructor(
    private val context: AdapterContext
) : SessionBackedOutputAdapter(context.session) {
    constructor(output: Path, force: Boolean = false) : this(AdapterContext(CsvBundlePublisher(output, force = force)))

    constructor(output: Path, publishBundle: (Path, Path) -> Unit) : this(
        AdapterContext(CsvBundlePublisher(output, publishBundleOverride = publishBundle))
    )

    override fun beginMatch() = context.transaction.begin()

    override fun commit() = context.transaction.commit()

    override fun rollback() = context.transaction.rollback()

    override fun close() {
        if (context.closed) return
        if (context.transaction.isActive) rollback()
        context.closed = true
    }

    private class AdapterContext(bundlePublisher: CsvBundlePublisher) {
        var closed = false
        private val outputState = CsvOutputState(bundlePublisher)
        val session = RelationalMatchSession(
            sink = RelationalSink { row ->
                check(!closed) { "Cannot write to a closed CSV output adapter" }
                outputState.emitRow(row)
            },
            identityStore = IdentityCore()
        )
        val transaction = MatchTransaction(listOf(session.transactionParticipant(), outputState))
    }
}
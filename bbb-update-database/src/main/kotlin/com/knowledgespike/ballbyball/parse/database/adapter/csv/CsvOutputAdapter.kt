package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.parse.database.adapter.IdentityCore
import com.knowledgespike.ballbyball.parse.database.adapter.JournaledMatchReplace
import com.knowledgespike.ballbyball.parse.database.adapter.JournaledReplaceKind
import com.knowledgespike.ballbyball.parse.database.adapter.MatchTransaction
import com.knowledgespike.ballbyball.parse.database.adapter.SessionBackedOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalMatchSession
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRow
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalSink
import java.nio.file.Files
import java.nio.file.Path

/** Writes relational tables as UTF-8 CSV files suitable for bulk loading into MariaDB. */
class CsvOutputAdapter private constructor(
    private val context: AdapterContext
) : SessionBackedOutputAdapter(context.session) {
    constructor(output: Path, force: Boolean = false) : this(AdapterContext(output, force = force))

    constructor(output: Path, publishBundle: (Path, Path) -> Unit) : this(
        AdapterContext(output, publishBundleOverride = publishBundle)
    )

    internal constructor(
        output: Path,
        force: Boolean = false,
        publishBundleOverride: ((Path, Path) -> Unit)? = null
    ) : this(AdapterContext(output, force, publishBundleOverride))

    override fun beginMatch() = context.beginMatch()

    override fun commit() = context.transaction.commit()

    override fun rollback() = context.transaction.rollback()

    override fun complete() = context.complete()

    override fun close() = context.close()

    companion object {
        fun recoverPublication(output: Path): Boolean =
            JournaledMatchReplace.recover(output, JournaledReplaceKind.DIRECTORY)
    }

    private class AdapterContext(
        outputPath: Path,
        force: Boolean = false,
        publishBundleOverride: ((Path, Path) -> Unit)? = null
    ) {
        var closed = false
        private val stagedPath = prepareStagedPath(outputPath, force)
        private val outputState = CsvOutputState(stagedPath, outputPath, publishBundleOverride)
        val session = RelationalMatchSession(RelationalSink(::emitRow), IdentityCore())
        val transaction = MatchTransaction(listOf(session.transactionParticipant(), outputState))

        private fun emitRow(row: RelationalRow): Long? {
            check(!closed) { "Cannot write to a closed CSV output adapter" }
            check(transaction.isActive) { "No match transaction is active" }
            outputState.emitRow(row)
            return null
        }

        fun beginMatch() {
            check(!closed) { "Cannot begin match on a closed CSV output adapter" }
            transaction.begin()
        }

        fun complete() {
            check(!closed) { "CSV output is already closed" }
            check(!transaction.isActive) { "Cannot complete CSV output with an active match transaction" }
            outputState.complete()
            closed = true
        }

        fun close() {
            if (closed) return
            var failure: Exception? = null
            try {
                if (transaction.isActive) transaction.rollback()
            } catch (rollbackFailure: Exception) {
                failure = rollbackFailure
            }
            try {
                outputState.close()
            } catch (closeFailure: Exception) {
                failure = failure.withSuppressed(closeFailure)
            }
            closed = true
            failure?.let { throw it }
        }

        private fun prepareStagedPath(output: Path, force: Boolean): Path {
            recoverPublication(output)
            if (Files.exists(output)) {
                require(Files.isDirectory(output)) { "CSV output path must be a directory: $output" }
                val hasEntries = Files.list(output).use { it.findAny().isPresent }
                if (hasEntries) {
                    require(force) { "The CSV destination directory must be empty: $output" }
                    deleteDirectoryContents(output)
                }
            } else {
                Files.createDirectories(output)
            }
            val parent = output.toAbsolutePath().parent ?: output.toAbsolutePath()
            Files.createDirectories(parent)
            return Files.createTempDirectory(parent, ".${output.fileName}-bundle-")
        }
    }
}

private fun Exception?.withSuppressed(other: Exception): Exception =
    this?.also { it.addSuppressed(other) } ?: other

package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.parse.database.adapter.JournaledMatchReplace
import com.knowledgespike.ballbyball.parse.database.adapter.JournaledReplaceKind
import com.knowledgespike.ballbyball.parse.database.adapter.MatchTransactionParticipant
import com.knowledgespike.ballbyball.parse.database.adapter.RelationalWriteSupport
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRow
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRowOperation
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class CsvOutputState(
    private val stagedPath: Path,
    private val outputPath: Path,
    private val publishBundleOverride: ((Path, Path) -> Unit)? = null
) : MatchTransactionParticipant {
    private val tableWriters = mutableMapOf<String, TableWriterState>()
    private val matchRows = linkedMapOf<Long, List<Any?>>()
    private val inningsRows = linkedMapOf<Long, List<Any?>>()
    private val peopleRows = linkedMapOf<Long, List<Any?>>()
    private var snapshot: Snapshot? = null
    private var completed = false

    override fun begin() {
        check(snapshot == null) { "CSV output transaction is already active" }
        val tableOffsets = tableWriters.mapValues { (_, writer) -> writer.currentSize() }
        snapshot = Snapshot(
            matchRows = matchRows.toMap(),
            inningsRows = inningsRows.toMap(),
            peopleRows = peopleRows.toMap(),
            tableOffsets = tableOffsets
        )
    }

    override fun durableCommit() {
        // Direct writes occur into staged files; atomic directory publish occurs on complete().
    }

    override fun commit() {
        snapshot = null
    }

    override fun rollback() {
        val snap = snapshot ?: return
        matchRows.replaceWith(snap.matchRows)
        inningsRows.replaceWith(snap.inningsRows)
        peopleRows.replaceWith(snap.peopleRows)
        tableWriters.forEach { (name, writer) ->
            val prevSize = snap.tableOffsets[name] ?: 0L
            if (writer.currentSize() > prevSize) {
                writer.truncate(prevSize)
            }
        }
        snapshot = null
    }

    fun emitRow(row: RelationalRow) {
        if (row.operation == RelationalRowOperation.UPDATE) {
            applyUpdate(row)
            return
        }
        when (row.tableName) {
            "matches" -> {
                val rowValues = row.columns.zip(row.values).toMap()
                matchRows[rowValues.getValue("id") as Long] = row.values.toList()
            }

            "innings" -> {
                val rowValues = row.columns.zip(row.values).toMap()
                inningsRows[rowValues.getValue("id") as Long] = row.values.toList()
            }

            "people" -> {
                val rowValues = row.columns.zip(row.values).toMap()
                peopleRows[rowValues.getValue("id") as Long] = row.values.toList()
            }

            else -> {
                val table = tableWriters.getOrPut(row.tableName) {
                    TableWriterState(stagedPath.resolve("${row.tableName}.csv"))
                }
                table.ensureHeaders(row.columns)
                table.writeRow(row.values)
            }
        }
    }

    fun complete() {
        check(!completed) { "CSV output is already completed" }
        if (matchRows.isNotEmpty()) {
            val matchFile = stagedPath.resolve("matches.csv")
            Files.newBufferedWriter(matchFile, StandardCharsets.UTF_8).use { writer ->
                writer.append(RelationalWriteSupport.MATCH_COLUMNS.joinToString(","))
                writer.append('\n')
                matchRows.values.forEach { row ->
                    writeCsvRow(writer, row)
                }
            }
        }
        if (inningsRows.isNotEmpty()) {
            val inningsFile = stagedPath.resolve("innings.csv")
            Files.newBufferedWriter(inningsFile, StandardCharsets.UTF_8).use { writer ->
                writer.append(RelationalWriteSupport.INNINGS_COLUMNS.joinToString(","))
                writer.append('\n')
                inningsRows.values.forEach { row ->
                    writeCsvRow(writer, row)
                }
            }
        }
        if (peopleRows.isNotEmpty()) {
            val peopleFile = stagedPath.resolve("people.csv")
            Files.newBufferedWriter(peopleFile, StandardCharsets.UTF_8).use { writer ->
                writer.append(RelationalWriteSupport.PERSON_COLUMNS.joinToString(","))
                writer.append('\n')
                peopleRows.values.forEach { row ->
                    writeCsvRow(writer, row)
                }
            }
        }
        tableWriters.values.forEach(TableWriterState::close)

        if (publishBundleOverride != null) {
            publishBundleOverride.invoke(stagedPath, outputPath)
        } else {
            JournaledMatchReplace.replace(stagedPath, outputPath, JournaledReplaceKind.DIRECTORY)
        }
        completed = true
    }

    fun close() {
        var failure: Exception? = null
        tableWriters.values.forEach { writer ->
            try {
                writer.close()
            } catch (e: Exception) {
                failure = failure.withSuppressed(e)
            }
        }
        try {
            if (!JournaledMatchReplace.hasPendingJournal(outputPath, JournaledReplaceKind.DIRECTORY)) {
                deleteDirectory(stagedPath)
            }
        } catch (e: Exception) {
            failure = failure.withSuppressed(e)
        }
        failure?.let { throw it }
    }

    private fun applyUpdate(row: RelationalRow) {
        when (row.tableName) {
            "matches" -> {
                val entry = matchRows.entries.firstOrNull { (_, values) ->
                    rowMatches(row, RelationalWriteSupport.MATCH_COLUMNS, values)
                } ?: error("No known match row matches the requested update")
                matchRows[entry.key] = updatedValues(row, RelationalWriteSupport.MATCH_COLUMNS, entry.value)
            }

            "innings" -> {
                val entry = inningsRows.entries.firstOrNull { (_, values) ->
                    rowMatches(row, RelationalWriteSupport.INNINGS_COLUMNS, values)
                } ?: error("No known innings row matches the requested update")
                inningsRows[entry.key] = updatedValues(row, RelationalWriteSupport.INNINGS_COLUMNS, entry.value)
            }

            "people" -> {
                val entry = peopleRows.entries.firstOrNull { (_, values) ->
                    rowMatches(row, RelationalWriteSupport.PERSON_COLUMNS, values)
                } ?: error("No known people row matches the requested update")
                peopleRows[entry.key] = updatedValues(row, RelationalWriteSupport.PERSON_COLUMNS, entry.value)
            }

            else -> error("CSV output does not support updates for ${row.tableName}")
        }
    }

    private fun rowMatches(row: RelationalRow, headers: List<String>, values: List<Any?>): Boolean {
        val conditionIndexes = row.whereColumns.map(headers::indexOf)
        require(conditionIndexes.all { it >= 0 }) { "Update conditions must exist in the ${row.tableName} table" }
        return conditionIndexes.indices.all { index -> values[conditionIndexes[index]] == row.whereValues[index] }
    }

    private fun updatedValues(row: RelationalRow, headers: List<String>, values: List<Any?>): List<Any?> {
        val updateIndexes = row.columns.map(headers::indexOf)
        require(updateIndexes.all { it >= 0 }) { "Update columns must exist in the ${row.tableName} table" }
        return values.toMutableList().also { updated ->
            updateIndexes.forEachIndexed { index, columnIndex -> updated[columnIndex] = row.values[index] }
        }
    }

    private class TableWriterState(
        val file: Path
    ) : AutoCloseable {
        private var writer: BufferedWriter? = null
        var hasHeaders: Boolean = false

        fun ensureHeaders(headers: List<String>) {
            if (hasHeaders) return
            val w = getOrCreateWriter()
            w.append(headers.joinToString(","))
            w.append('\n')
            hasHeaders = true
        }

        fun writeRow(values: List<Any?>) {
            val w = getOrCreateWriter()
            writeCsvRow(w, values)
        }

        fun flush() {
            writer?.flush()
        }

        fun currentSize(): Long {
            flush()
            return if (Files.exists(file)) Files.size(file) else 0L
        }

        fun truncate(size: Long) {
            flush()
            close()
            if (size == 0L) {
                Files.deleteIfExists(file)
                hasHeaders = false
            } else {
                Files.newByteChannel(file, StandardOpenOption.WRITE).use { channel ->
                    channel.truncate(size)
                }
            }
        }

        private fun getOrCreateWriter(): BufferedWriter {
            writer?.let { return it }
            val w = Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
            writer = w
            return w
        }

        override fun close() {
            writer?.close()
            writer = null
        }
    }

    private data class Snapshot(
        val matchRows: Map<Long, List<Any?>>,
        val inningsRows: Map<Long, List<Any?>>,
        val peopleRows: Map<Long, List<Any?>>,
        val tableOffsets: Map<String, Long>
    )
}

private fun <K, V> MutableMap<K, V>.replaceWith(values: Map<K, V>) {
    clear()
    putAll(values)
}

private fun Exception?.withSuppressed(other: Exception): Exception =
    this?.also { it.addSuppressed(other) } ?: other

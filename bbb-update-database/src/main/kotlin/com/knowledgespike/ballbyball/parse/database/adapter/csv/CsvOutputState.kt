package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.parse.database.adapter.MatchTransactionParticipant
import com.knowledgespike.ballbyball.parse.database.adapter.RelationalWriteSupport
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRow
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRowOperation

internal class CsvOutputState(
    private val bundlePublisher: CsvBundlePublisher
) : MatchTransactionParticipant {
    private val pendingRows = mutableMapOf<String, CsvPendingTable>()
    private val matchRows = linkedMapOf<Long, List<Any?>>()
    private val inningsRows = linkedMapOf<Long, List<Any?>>()
    private val updatedTables = mutableSetOf<String>()
    private var snapshot: Snapshot? = null

    override fun begin() {
        check(snapshot == null) { "CSV output transaction is already active" }
        snapshot = Snapshot(
            pendingRows = pendingRows.mapValues { (_, table) -> table.snapshot() },
            matchRows = matchRows.toMap(),
            inningsRows = inningsRows.toMap(),
            updatedTables = updatedTables.toSet()
        )
    }

    override fun durableCommit() {
        val changedTables = pendingRows.keys.toMutableSet().apply { addAll(updatedTables) }
        if (changedTables.isEmpty()) return

        val publicationRows = pendingRows.mapValues { (_, table) -> table.publicationSnapshot() }.toMutableMap()
        if ("innings" in changedTables) {
            publicationRows["innings"] = CsvTableSnapshot(
                headers = RelationalWriteSupport.INNINGS_COLUMNS,
                rows = inningsRows.values.toList()
            )
        }
        bundlePublisher.publish(changedTables, publicationRows, matchRows.toMap())
    }

    override fun commit() {
        pendingRows.clear()
        updatedTables.clear()
        snapshot = null
    }

    override fun rollback() {
        snapshot?.let(::restore)
        snapshot = null
    }

    fun emitRow(row: RelationalRow): Long? {
        if (row.operation == RelationalRowOperation.UPDATE) {
            applyUpdate(row)
            return null
        }
        val table = pendingRows.getOrPut(row.tableName) {
            CsvPendingTable(row.columns.toList(), mutableListOf())
        }
        check(table.headers == row.columns) { "CSV headers changed for table ${row.tableName}" }
        table.rows += row.values.toList()
        when (row.tableName) {
            "matches" -> {
                val rowValues = row.columns.zip(row.values).toMap()
                matchRows[rowValues.getValue("id") as Long] = row.values.toList()
            }

            "innings" -> {
                val rowValues = row.columns.zip(row.values).toMap()
                inningsRows[rowValues.getValue("id") as Long] = row.values.toList()
            }
        }
        return null
    }

    private fun restore(snapshot: Snapshot) {
        pendingRows.replaceWith(snapshot.pendingRows.mapValues { (_, table) -> table.snapshot() })
        matchRows.replaceWith(snapshot.matchRows)
        inningsRows.replaceWith(snapshot.inningsRows)
        updatedTables.replaceWith(snapshot.updatedTables)
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

            else -> error("CSV output does not support updates for ${row.tableName}")
        }
        updatedTables += row.tableName
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

    private data class Snapshot(
        val pendingRows: Map<String, CsvPendingTable>,
        val matchRows: Map<Long, List<Any?>>,
        val inningsRows: Map<Long, List<Any?>>,
        val updatedTables: Set<String>
    )
}

private fun <K, V> MutableMap<K, V>.replaceWith(values: Map<K, V>) {
    clear()
    putAll(values)
}

private fun <T> MutableSet<T>.replaceWith(values: Set<T>) {
    clear()
    addAll(values)
}

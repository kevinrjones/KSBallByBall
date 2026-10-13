package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRow
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalRowOperation
import com.knowledgespike.ballbyball.parse.database.adapter.relational.RelationalSink
import com.knowledgespike.cricketarchive.InvalidStateException
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime

internal class JdbcRowSink(
    private val connection: Connection
) : RelationalSink {

    override fun emit(row: RelationalRow): Long? = when (row.operation) {
        RelationalRowOperation.INSERT -> insert(row)
        RelationalRowOperation.UPDATE -> {
            update(row)
            null
        }
    }

    private fun insert(row: RelationalRow): Long? {
        val insertValues = row.columns.zip(row.values).filterNot { (column, _) -> column in row.generatedColumns }
        val sql = RelationalWriteSupport.insertSql(row.tableName, insertValues.map { it.first }, "insert into")
        val keyRequest = if (row.generatedColumns.isEmpty()) Statement.NO_GENERATED_KEYS else Statement.RETURN_GENERATED_KEYS
        return connection.prepareStatement(sql, keyRequest).use { statement ->
            insertValues.forEachIndexed { index, (_, value) -> setJdbcValue(statement, index + 1, value) }
            val affectedRows = statement.executeUpdate()
            check(affectedRows == 1) {
                "Expected one inserted row in ${row.tableName}; got $affectedRows"
            }
            if (row.generatedColumns.isEmpty() || affectedRows == 0) return@use null
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected generated key for ${row.tableName}")
                keys.getLong(1)
            }
        }
    }

    private fun update(row: RelationalRow) {
        val sql = RelationalWriteSupport.updateSql(
            row.tableName,
            row.columns,
            row.whereColumns,
            row.whereValues
        )
        connection.prepareStatement(sql).use { statement ->
            val conditionValues = row.whereValues.filterNotNull()
            (row.values + conditionValues).forEachIndexed { index, value ->
                setJdbcValue(statement, index + 1, value)
            }
            check(statement.executeUpdate() == 1) { "Expected one updated row in ${row.tableName}" }
        }
    }

}

internal fun setJdbcValue(statement: PreparedStatement, index: Int, value: Any?) {
    when (value) {
        null -> statement.setNull(index, Types.NULL)
        is Int -> statement.setInt(index, value)
        is Long -> statement.setLong(index, value)
        is Boolean -> statement.setBoolean(index, value)
        is String -> statement.setString(index, value)
        is LocalDate -> statement.setDate(index, java.sql.Date.valueOf(value))
        is java.sql.Date -> statement.setDate(index, value)
        is java.sql.Timestamp -> statement.setTimestamp(index, value)
        is LocalDateTime -> statement.setTimestamp(index, java.sql.Timestamp.valueOf(value))
        else -> statement.setObject(index, value)
    }
}
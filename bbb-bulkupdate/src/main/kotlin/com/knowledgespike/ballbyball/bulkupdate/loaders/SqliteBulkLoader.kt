package com.knowledgespike.ballbyball.bulkupdate.loaders

import com.knowledgespike.cricketarchive.LoggerDelegate
import java.nio.file.Path
import java.sql.Connection
import java.sql.Types
import kotlin.io.path.bufferedReader

class SqliteBulkLoader(private val batchSize: Int = 10000) : BulkLoader {

    companion object {
        private val log by LoggerDelegate()
    }

    override fun load(connection: Connection, file: Path, tableName: String): Result<Unit> = runCatching {
        log.info("Loading ${file.fileName} into SQLite table $tableName")

        file.bufferedReader().use { reader ->
            val headerLine = reader.readLine()
            if (headerLine.isNullOrBlank()) {
                log.warn("Empty file or header in ${file.fileName}, skipping table $tableName")
                return@runCatching
            }

            val columns = parseCsvRow(headerLine).map { it.trim().removeSurrounding("\"").removeSurrounding("`") }
            if (columns.isEmpty()) {
                log.warn("No columns parsed in ${file.fileName}, skipping table $tableName")
                return@runCatching
            }

            val columnNames = columns.joinToString(",") { "\"$it\"" }
            val placeholders = columns.joinToString(",") { "?" }
            val sql = "INSERT INTO \"$tableName\" ($columnNames) VALUES ($placeholders)"

            log.debug("Executing SQLite Prepared Statement: $sql")

            connection.prepareStatement(sql).use { statement ->
                var count = 0
                var totalRows = 0

                reader.forEachLine { line ->
                    if (line.isNotBlank()) {
                        val values = parseCsvRow(line)
                        for (i in columns.indices) {
                            val rawValue = values.getOrNull(i)
                            if (rawValue == null || rawValue == "\\N" || rawValue == "NULL" || rawValue.isEmpty()) {
                                statement.setNull(i + 1, Types.VARCHAR)
                            } else {
                                statement.setString(i + 1, rawValue)
                            }
                        }
                        statement.addBatch()
                        count++
                        totalRows++

                        if (count % batchSize == 0) {
                            statement.executeBatch()
                            count = 0
                        }
                    }
                }

                if (count > 0) {
                    statement.executeBatch()
                }

                log.info("Successfully loaded $totalRows rows from ${file.fileName} into $tableName")
            }
        }
    }.onFailure { e ->
        log.error("Failed to load ${file.fileName} into SQLite table $tableName: ${e.message}", e)
    }

    internal fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

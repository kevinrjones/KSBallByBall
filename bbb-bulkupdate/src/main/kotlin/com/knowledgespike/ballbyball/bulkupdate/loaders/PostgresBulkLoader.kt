package com.knowledgespike.ballbyball.bulkupdate.loaders

import com.knowledgespike.cricketarchive.LoggerDelegate
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Connection
import kotlin.io.path.bufferedReader
import kotlin.io.path.inputStream

class PostgresBulkLoader : BulkLoader {

    companion object {
        private val log by LoggerDelegate()
    }

    override fun load(connection: Connection, file: Path, tableName: String): Result<Unit> = runCatching {
        log.info("Loading ${file.fileName} into PostgreSQL table $tableName")

        val columns = getColumns(file)
        if (columns.isBlank()) {
            log.warn("Empty header in ${file.fileName}, skipping table $tableName")
            return@runCatching
        }

        val baseConnection = if (connection.isWrapperFor(BaseConnection::class.java)) {
            connection.unwrap(BaseConnection::class.java)
        } else {
            connection as BaseConnection
        }

        val copyManager = CopyManager(baseConnection)
        val sql = "COPY \"$tableName\" ($columns) FROM STDIN WITH (FORMAT CSV, HEADER TRUE, DELIMITER ',', NULL '\\N', QUOTE '\"', ESCAPE '\"')"

        log.debug("Executing PostgreSQL COPY: $sql")

        file.inputStream().use { inputStream ->
            InputStreamReader(inputStream, StandardCharsets.UTF_8).use { reader ->
                val rowsCopied = copyManager.copyIn(sql, reader)
                log.info("Successfully copied $rowsCopied rows from ${file.fileName} into $tableName")
            }
        }
    }.onFailure { e ->
        log.error("Failed to load ${file.fileName} into PostgreSQL table $tableName: ${e.message}", e)
    }

    private fun getColumns(file: Path): String {
        val header = file.bufferedReader().use { it.readLine() } ?: return ""
        return header.split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
            .joinToString(",") { "\"$it\"" }
    }
}

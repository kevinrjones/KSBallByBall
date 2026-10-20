package com.knowledgespike.ballbyball.bulkupdate.loaders

import com.knowledgespike.cricketarchive.LoggerDelegate
import java.nio.file.Path
import java.sql.Connection
import java.sql.Statement
import kotlin.io.path.bufferedReader
import kotlin.io.path.pathString

class MySqlBulkLoader : BulkLoader {

    companion object {
        private val log by LoggerDelegate()
    }

    override fun load(connection: Connection, file: Path, tableName: String): Result<Unit> = runCatching {
        log.info("Loading ${file.fileName} into MySQL/MariaDB table $tableName")

        val columns = getColumns(file)
        if (columns.isBlank()) {
            log.warn("Empty header in ${file.fileName}, skipping table $tableName")
            return@runCatching
        }

        // MySQL expects forward slashes even on Windows for LOAD DATA INFILE
        val filePath = file.pathString.replace("\\", "/")
        val sql = """
            LOAD DATA LOCAL INFILE '$filePath'
            INTO TABLE `$tableName`
            FIELDS TERMINATED BY ','
            OPTIONALLY ENCLOSED BY '"'
            LINES TERMINATED BY '\n'
            IGNORE 1 LINES
            ($columns)
        """.trimIndent()

        log.debug("Executing SQL: $sql")

        connection.createStatement().use { statement ->
            statement.execute(sql)
            logWarnings(statement)
        }
        log.info("Successfully loaded ${file.fileName} into $tableName")
    }.onFailure { e ->
        log.error("Failed to load ${file.fileName} into MySQL table $tableName: ${e.message}", e)
    }

    private fun getColumns(file: Path): String {
        val header = file.bufferedReader().use { it.readLine() } ?: return ""
        return header.split(",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("`") }
            .filter { it.isNotBlank() }
            .joinToString(",") { "`$it`" }
    }

    private fun logWarnings(statement: Statement) {
        var warning = statement.warnings
        while (warning != null) {
            log.warn("MySQL warning: ${warning.message}")
            warning = warning.nextWarning
        }
    }
}

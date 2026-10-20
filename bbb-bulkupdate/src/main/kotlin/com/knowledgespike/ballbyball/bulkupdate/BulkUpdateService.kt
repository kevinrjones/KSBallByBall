package com.knowledgespike.ballbyball.bulkupdate

import com.knowledgespike.ballbyball.bulkupdate.discovery.CsvDiscovery
import com.knowledgespike.ballbyball.bulkupdate.loaders.BulkLoader
import com.knowledgespike.ballbyball.bulkupdate.loaders.MySqlBulkLoader
import com.knowledgespike.ballbyball.bulkupdate.loaders.PostgresBulkLoader
import com.knowledgespike.ballbyball.bulkupdate.loaders.SqliteBulkLoader
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricketarchive.shared.DatabaseConnection
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import kotlin.io.path.isDirectory
import kotlin.io.path.nameWithoutExtension

class BulkUpdateService(
    private val config: AppConfig,
    private val discovery: CsvDiscovery = CsvDiscovery(),
    private val loaderFactory: (DbType) -> BulkLoader = { dbType ->
        when (dbType) {
            DbType.SQLite -> SqliteBulkLoader()
            DbType.PostgreSQL -> PostgresBulkLoader()
            DbType.MySQL, DbType.MariaDB -> MySqlBulkLoader()
        }
    }
) {

    companion object {
        private val log by LoggerDelegate()
    }

    fun execute(): Result<Unit> = runCatching {
        val targetCsvDir = resolveCsvDirectory()
        log.info("Searching for CSV files in: $targetCsvDir")

        val csvFiles = discovery.discoverCsvFiles(targetCsvDir)
        if (csvFiles.isEmpty()) {
            log.warn("No CSV files found to process in $targetCsvDir")
            return Result.success(Unit)
        }

        log.info("Found ${csvFiles.size} CSV files to process")

        val dbConnection = DatabaseConnection(
            connectionString = config.connectionString,
            userName = config.userName,
            password = config.password
        )

        dbConnection.connect.use { db ->
            val connection = db.connection
            val autoCommit = connection.autoCommit
            connection.autoCommit = false

            try {
                configureSession(connection, disableForeignKeys = true)

                val loader = loaderFactory(config.dbType)
                for (file in csvFiles) {
                    val tableName = file.nameWithoutExtension
                    log.info("Processing file: ${file.fileName} for table: $tableName")
                    val loadResult = loader.load(connection, file, tableName)
                    if (loadResult.isFailure) {
                        val exception = loadResult.exceptionOrNull()
                        log.error("Failed loading table $tableName from ${file.fileName}", exception)
                        throw exception ?: RuntimeException("Bulk load failed for table $tableName")
                    }
                }

                connection.commit()
                log.info("Successfully committed all bulk update data")
            } catch (e: Exception) {
                log.error("Error occurred during bulk update; rolling back transaction", e)
                try {
                    connection.rollback()
                } catch (rollbackEx: Exception) {
                    log.error("Failed to rollback transaction", rollbackEx)
                }
                throw e
            } finally {
                try {
                    configureSession(connection, disableForeignKeys = false)
                } catch (fkEx: Exception) {
                    log.warn("Failed to re-enable foreign keys: ${fkEx.message}", fkEx)
                }
                connection.autoCommit = autoCommit
            }
        }
    }

    private fun resolveCsvDirectory(): Path {
        val csvPath = Paths.get(config.csvDir)
        val basePath = Paths.get(config.baseDirectory)
        val dbTypeName = config.dbType.name.lowercase()

        return if (csvPath.isAbsolute) {
            val withDbType = csvPath.resolve(dbTypeName)
            if (withDbType.isDirectory()) withDbType else csvPath
        } else {
            val resolved = basePath.resolve(csvPath)
            val withDbType = resolved.resolve(dbTypeName)
            if (withDbType.isDirectory()) withDbType else resolved
        }
    }

    private fun configureSession(connection: Connection, disableForeignKeys: Boolean) {
        when (config.dbType) {
            DbType.SQLite -> {
                connection.createStatement().use { stmt ->
                    val fkState = if (disableForeignKeys) "OFF" else "ON"
                    stmt.execute("PRAGMA foreign_keys = $fkState")
                }
            }
            DbType.PostgreSQL -> {
                connection.createStatement().use { stmt ->
                    val role = if (disableForeignKeys) "replica" else "origin"
                    stmt.execute("SET session_replication_role = '$role'")
                }
            }
            DbType.MySQL, DbType.MariaDB -> {
                connection.createStatement().use { stmt ->
                    val checkState = if (disableForeignKeys) "0" else "1"
                    stmt.execute("SET FOREIGN_KEY_CHECKS = $checkState")
                }
            }
        }
    }
}

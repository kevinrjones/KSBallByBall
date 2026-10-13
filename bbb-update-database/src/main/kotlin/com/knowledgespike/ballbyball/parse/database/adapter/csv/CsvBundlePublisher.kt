package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.parse.database.adapter.JournaledMatchReplace
import com.knowledgespike.ballbyball.parse.database.adapter.JournaledReplaceKind
import com.knowledgespike.ballbyball.parse.database.adapter.RelationalWriteSupport
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Deletes the specified directory and all of its contents recursively.
 *
 * @param path The path to the directory that should be deleted.
 * Throws an exception if the directory cannot be deleted.
 */
private fun deleteDirectory(path: Path) {
    if (Files.exists(path)) {
        check(path.toFile().deleteRecursively()) { "Unable to delete directory: $path" }
    }
}

/**
 * A typealias representing a function used to write CSV table data to files.
 *
 * This function is responsible for serializing data from multiple tables into CSV files.
 * It takes the following parameters:
 * - A file path where the CSV file(s) will be written.
 * - A string representing the encoding format for the output.
 * - A map where the key is the table name and the value is a `CsvTableSnapshot` representing
 *   the headers and rows to be written for each table.
 * - A map where the key is a unique identifier (typically a Long) and the value is a list
 *   of row-level data for a specific table.
 *
 * The function outputs the serialized CSV content to the specified path.
 */
internal typealias CsvTableWriter = (
    Path,
    String,
    Map<String, CsvTableSnapshot>,
    Map<Long, List<Any?>>
) -> Unit
/**
 * A type alias for a function responsible for writing CSV journal data to specified file paths.
 *
 * This writer function takes the following parameters:
 * - `matchesPath`: The file path where the "matches" data will be written.
 * - `inningsPath`: The file path where the "innings" data will be written.
 * - `pendingPath`: The file path where any pending table rows will be saved.
 * - `updatedPath`: The file path where updated table rows (if any) will be saved.
 * - `bundleName`: A string representing the name of the output bundle associated with the CSV.
 *
 * This function performs the actual writing of relational data (formatted as UTF-8 CSV) for use
 * in bulk loading or further processing.
 */
internal typealias CsvJournalWriter = (Path, Path, Path, Path, String) -> Unit
/**
 * Represents a function type alias for synchronizing a directory of CSV files.
 *
 * This function is expected to take a file system path as input and perform
 * synchronization operations on the specified directory.
 *
 * The primary usage of this alias is in the context of operations where
 * CSV data needs to be managed, often as part of a data output handling
 * or publication process, such as bulk loading into MariaDB.
 *
 * @param Path The path to the directory containing CSV files to be synchronized.
 */
internal typealias CsvDirectorySync = (Path) -> Unit

/**
 * Handles the publication of CSV bundles by managing directories and file synchronization.
 *
 * The class is responsible for copying files, writing CSV data, creating temporary bundles,
 * and ensuring the synchronization of directories for CSV publishing processes.
 *
 * @constructor Initializes a CsvBundlePublisher instance.
 *
 * @param output The output directory path where the bundle will be published.
 * @param force A flag indicating whether to enforce overwriting non-empty directories.
 * @param publishBundleOverride An optional override function for publishing bundles.
 * @param copyFile A function used for copying files from source to target.
 * @param tableWriter An optional instance of CsvTableWriter for writing table data.
 * @param backupCleanup A function used to clean up backups.
 * @param directorySync An optional function for ensuring directory synchronization.
 * @param journalWriter An optional writer to record actions during journaled operations.
 */
internal class CsvBundlePublisher(
    output: Path,
    force: Boolean = false,
    publishBundleOverride: ((Path, Path) -> Unit)? = null,
    private val copyFile: (Path, Path) -> Unit = { source, target ->
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    },
    private val tableWriter: CsvTableWriter? = null,
    private val backupCleanup: (Path) -> Unit = ::deleteDirectory,
    directorySync: CsvDirectorySync? = null,
    journalWriter: CsvJournalWriter? = null
) {
    /**
     * Represents the synchronization directory for managing file operations during the publishing process.
     * This property is initialized based on the provided directorySync value or defaults to the syncDirectory
     * method in the JournaledMatchReplace class.
     *
     * The synchronization directory ensures that all temporary and final bundle files are stored and
     * managed consistently during various operations like publishing and populating CSV bundles.
     */
    private val syncDirectory = directorySync ?: JournaledMatchReplace::syncDirectory
    /**
     * A function reference that defines the behavior for publishing a bundle of files or directories
     * from a source path to a target path. The function employs journaling and optional backup mechanisms
     * to ensure robust replacement, even in scenarios that require fallback operations.
     *
     * If an override function (`publishBundleOverride`) is not specified, a default implementation
     * is provided. The default implementation uses `JournaledMatchReplace.replace` to handle the
     * publishing process.
     *
     * Key aspects of the default implementation:
     * - Employs journaling for tracking the replacement process, allowing for recovery if needed.
     * - Ensures directory-level or file-level synchronization, as specified by the `JournaledReplaceKind`.
     * - Allows for optional backup cleanup operations.
     * - Optionally integrates a custom journal writer for recording replacement activity.
     *
     * Dependencies:
     * - `JournaledMatchReplace.replace`: The core mechanism for robust replacement operations.
     * - `journalWriter`: A lambda function (if provided) to log details such as source, target,
     *   backup paths, and phases.
     * - `backupCleanup`: A function to handle cleanup for backup files or directories.
     * - `syncDirectory`: A function to ensure the target directory is properly synced.
     */
    private val publishBundle: (Path, Path) -> Unit = publishBundleOverride
        ?: { source, target ->
            JournaledMatchReplace.replace(
                source = source,
                target = target,
                kind = JournaledReplaceKind.DIRECTORY,
                journalWriter = journalWriter?.let { writer ->
                    { journal, record ->
                        writer(journal, record.source, record.target, record.backup, record.phase.journalValue)
                    }
                },
                backupCleanup = backupCleanup,
                directorySync = syncDirectory
            )
        }
    /**
     * Represents the output directory path for the CSV bundle publication process.
     * This path is prepared and validated based on the provided output directory and force flag.
     *
     * The directory must satisfy the following conditions:
     * - It must either not exist or be a directory.
     * - If it exists, it must be empty unless the `force` parameter is used,
     *   in which case its contents will be replaced.
     * - The required directory structure will be created if it does not already exist.
     *
     * This path serves as the location where the generated CSV files will be published.
     */
    val outputPath: Path = prepareOutput(output, force)

    /**
     * Publishes a set of changed tables to the specified output destination, utilizing
     * temporary storage during the process.
     *
     * @param changedTables A set of table names that have been modified and need to be published.
     * @param pendingTables A map containing table names as keys and their corresponding
     *                      `CsvTableSnapshot` as values, representing the updated table data.
     * @param matchRows A map where the key is a row identifier and the value is a list of
     *                  column values, representing the rows that need to be matched or updated.
     */
    fun publish(
        changedTables: Set<String>,
        pendingTables: Map<String, CsvTableSnapshot>,
        matchRows: Map<Long, List<Any?>>
    ) {
        withTemporaryBundle { bundle ->
            populateBundle(bundle, changedTables, pendingTables, matchRows)
            publishBundle(bundle, outputPath)
        }
    }

    /**
     * Creates a temporary directory for use within the provided block of code and ensures it is cleaned up after use.
     * The directory is created in the parent directory of `outputPath` with a name prefixed by the file name of `outputPath`.
     * After the block is executed, the directory is deleted unless there is a pending journal for it.
     *
     * @param block A lambda function that receives the path to the temporary directory and performs operations on it.
     * @return The result of the lambda function passed as the `block` parameter.
     */
    private fun <T> withTemporaryBundle(block: (Path) -> T): T {
        var bundle: Path? = null
        return try {
            Files.createTempDirectory(
                outputPath.toAbsolutePath().parent,
                ".${outputPath.fileName}-bundle-"
            ).also { bundle = it }.let(block)
        } finally {
            bundle?.takeUnless { JournaledMatchReplace.hasPendingJournal(outputPath, JournaledReplaceKind.DIRECTORY) }
                ?.let(::deleteDirectory)
        }
    }

    /**
     * Populates a bundle directory with the necessary files and updates based on the provided data.
     *
     * @param bundle The target directory where the bundle is being created.
     * @param changedTables A set of table names that have been modified and need to be included in the bundle.
     * @param pendingTables A map containing table snapshots with headers and rows that are pending update.
     * @param matchRows A map where the key represents an identifier, and the value is a list of data rows to be written for match-related tables.
     */
    private fun populateBundle(
        bundle: Path,
        changedTables: Set<String>,
        pendingTables: Map<String, CsvTableSnapshot>,
        matchRows: Map<Long, List<Any?>>
    ) {
        Files.list(outputPath).use { files ->
            files.filter(Files::isRegularFile).forEach { file ->
                copyFile(file, bundle.resolve(file.fileName))
            }
        }
        changedTables.forEach { tableName ->
            (tableWriter ?: ::writeTable)(bundle, tableName, pendingTables, matchRows)
        }
    }

    /**
     * Writes the specified table data to a CSV file within the given bundle path.
     * Supports specific handling for "matches" and "innings" table names.
     *
     * @param bundle The base path where the table's CSV file will be written.
     * @param tableName The name of the table to write. Special handling applies for "matches" and "innings".
     * @param pendingTables A map containing table snapshot objects for pending tables, keyed by table name.
     * @param matchRows A map containing row data for the "matches" table, keyed by row ID.
     */
    private fun writeTable(
        bundle: Path,
        tableName: String,
        pendingTables: Map<String, CsvTableSnapshot>,
        matchRows: Map<Long, List<Any?>>
    ) {
        val target = bundle.resolve("$tableName.csv")
        if (tableName == "matches") {
            Files.newBufferedWriter(target, StandardCharsets.UTF_8).use { writer ->
                writer.appendLine(RelationalWriteSupport.MATCH_COLUMNS.joinToString(","))
                matchRows.values.forEach { values ->
                    writer.appendLine(values.joinToString(",", transform = ::toCsvValue))
                }
            }
            return
        }

        if (tableName == "innings") {
            val table = pendingTables.getValue(tableName)
            Files.newBufferedWriter(target, StandardCharsets.UTF_8).use { writer ->
                writer.appendLine(table.headers.joinToString(","))
                table.rows.forEach { row -> writer.appendLine(row.joinToString(",", transform = ::toCsvValue)) }
            }
            return
        }

        val pendingTable = pendingTables.getValue(tableName)
        if (!Files.exists(target)) {
            Files.newBufferedWriter(target, StandardCharsets.UTF_8).use { writer ->
                writer.appendLine(pendingTable.headers.joinToString(","))
            }
        }
        Files.newBufferedWriter(
            target,
            StandardCharsets.UTF_8,
            StandardOpenOption.APPEND
        ).use { writer ->
            pendingTable.rows.forEach { row ->
                writer.appendLine(row.joinToString(",", transform = ::toCsvValue))
            }
        }
    }

    /**
     * Converts a given value to its corresponding CSV representation.
     *
     * @param value The input value to be converted. It can be of various types including `null`, `Boolean`,
     *              `LocalDateTime`, `String`, or any other type that can be converted to a string.
     * @return A `String` representing the converted CSV value. The method handles special cases such as:
     *         - `null`: Returns the string "\\N".
     *         - `Boolean`: Returns "1" for `true` and "0" for `false`.
     *         - `LocalDateTime`: Formats the value using the `DATETIME_FORMATTER`.
     *         - `String`: Escapes the value for CSV compatibility using the `escapeCsv` method.
     *         - Other types: Calls `toString` on the value.
     */
    private fun toCsvValue(value: Any?): String = when (value) {
        null -> "\\N"
        is Boolean -> if (value) "1" else "0"
        is LocalDateTime -> value.format(DATETIME_FORMATTER)
        is String -> escapeCsv(value)
        else -> value.toString()
    }

    /**
     * Escapes a given string so that it can be safely used in a CSV file.
     * If the string contains special characters such as a comma, double quotes, or newline characters,
     * it wraps the string in double quotes and escapes any double quotes by doubling them.
     *
     * @param value The string to be escaped for CSV formatting.
     * @return The escaped string, safe for inclusion in a CSV file.
     */
    private fun escapeCsv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    /**
     * A companion object that provides utility functions and constants related to
     * handling file operations and directory preparation for CSV publishing tasks.
     */
    companion object {
        /**
         * A formatter for representing dates and times in the "yyyy-MM-dd HH:mm:ss" pattern.
         * Used for formatting and parsing date-time values consistently across operations.
         */
        private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        /**
         * Attempts to recover a publication directory from a journaled operation.
         *
         * @param target the path to the directory that should be recovered.
         * @return true if the recovery was successful; false otherwise.
         */
        internal fun recoverPublication(target: Path): Boolean =
            JournaledMatchReplace.recover(target, JournaledReplaceKind.DIRECTORY)

        /**
         * Prepares the specified output directory for file generation.
         *
         * @param output the target output directory path.
         * @param force whether to force the recreation of the directory if it is not empty.
         * @return the prepared output path.
         * @throws IllegalArgumentException if the specified path is not a directory or if the directory
         * is not empty and `force` is not set to true.
         * @throws IOException if the output directory cannot be created or cleared.
         */
        private fun prepareOutput(output: Path, force: Boolean): Path {
            recoverPublication(output)
            require(!Files.exists(output) || Files.isDirectory(output)) {
                "CSV output path must be a directory: $output"
            }
            if (Files.exists(output)) {
                val isEmpty = Files.list(output).use { entries -> !entries.findAny().isPresent }
                require(isEmpty || force) {
                    "CSV output directory is not empty: $output; pass --force to replace its contents"
                }
                if (!isEmpty) deleteDirectory(output)
            }
            Files.createDirectories(output)
            return output
        }
    }

}

/**
 * An internal representation of a pending table consisting of headers and rows, used for building
 * CSV outputs incrementally before final publication or snapshot creation.
 *
 * @property headers A list of headers representing the column names of the table.
 * @property rows A mutable list containing the rows of the table, where each row is represented
 * as a list of nullable values.
 */
internal data class CsvPendingTable(
    val headers: List<String>,
    val rows: MutableList<List<Any?>>
) {
    /**
     * Creates a snapshot of the current state of the table.
     *
     * @return A new instance of [CsvPendingTable] containing a deep copy of the headers and rows.
     *         The headers are copied into a new list, and each row is copied into a new mutable list.
     */
    fun snapshot() = CsvPendingTable(headers.toList(), rows.map(List<Any?>::toList).toMutableList())

    /**
     * Creates a snapshot of the current state of the table, intended for publication purposes.
     *
     * The method captures the current headers and rows from the table and maps them into
     * an immutable `CsvTableSnapshot` object. The snapshot ensures the data is copied
     * into new collections, preventing any changes to the original data affecting the snapshot.
     *
     * This method is typically used to prepare a consistent and immutable representation
     * of the table data for external use or publication.
     *
     * @return A `CsvTableSnapshot` instance containing a copy of the current headers and rows.
     */
    fun publicationSnapshot() = CsvTableSnapshot(headers.toList(), rows.map(List<Any?>::toList))
}

/**
 * Represents a snapshot of a CSV table's state, containing its headers and rows.
 *
 * This data class is used to encapsulate the contents of a CSV table at a specific point in time.
 * It is primarily utilized for capturing and publishing data from relational tables in a CSV format.
 *
 * @property headers The list of column headers for the CSV table.
 * @property rows The list of rows in the CSV table, where each row is a list of nullable values.
 */
internal data class CsvTableSnapshot(
    val headers: List<String>,
    val rows: List<List<Any?>>
)
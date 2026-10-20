package com.knowledgespike.ballbyball.bulkupdate.discovery

import com.knowledgespike.cricketarchive.LoggerDelegate
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * CsvDiscovery is responsible for finding CSV files in the specified directory.
 */
class CsvDiscovery {

    companion object {
        private val log by LoggerDelegate()
    }

    /**
     * Scans the specified directory to discover CSV files.
     * Only `.csv` files in the given directory are considered.
     *
     * @param csvDir The directory to scan for CSV files.
     * @return A sorted list of paths to the CSV files in that directory.
     *         Returns an empty list if the directory does not exist, is not a directory, or contains no CSV files.
     */
    fun discoverCsvFiles(csvDir: Path): List<Path> {
        if (!csvDir.isDirectory()) {
            log.error("CSV directory $csvDir does not exist or is not a directory")
            return emptyList()
        }

        val dirName = csvDir.name
        log.info("Scanning directory: $dirName")
        val files = csvDir.listDirectoryEntries("*.csv")
        if (files.isNotEmpty()) {
            log.info("Found ${files.size} CSV files in $dirName")
        }

        log.info("Total found: ${files.size} CSV files")
        return files.sortedBy { it.name }
    }
}

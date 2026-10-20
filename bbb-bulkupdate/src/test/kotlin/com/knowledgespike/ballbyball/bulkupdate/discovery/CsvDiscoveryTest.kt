package com.knowledgespike.ballbyball.bulkupdate.discovery

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path

class CsvDiscoveryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should discover csv files in directory`() {
        val tFile1 = tempDir.resolve("matches.csv")
        Files.createFile(tFile1)
        val tFile2 = tempDir.resolve("people.csv")
        Files.createFile(tFile2)
        val nonCsv = tempDir.resolve("readme.txt")
        Files.createFile(nonCsv)

        val discovery = CsvDiscovery()
        val result = discovery.discoverCsvFiles(tempDir)

        expectThat(result).hasSize(2)
        expectThat(result[0].fileName.toString()).isEqualTo("matches.csv")
        expectThat(result[1].fileName.toString()).isEqualTo("people.csv")
    }

    @Test
    fun `should return empty list if no csv files match`() {
        val subDir = tempDir.resolve("other")
        Files.createDirectories(subDir)
        Files.createFile(subDir.resolve("readme.txt"))

        val discovery = CsvDiscovery()
        val result = discovery.discoverCsvFiles(subDir)

        expectThat(result).hasSize(0)
    }

    @Test
    fun `should return empty list if directory does not exist`() {
        val nonExistent = tempDir.resolve("does_not_exist")

        val discovery = CsvDiscovery()
        val result = discovery.discoverCsvFiles(nonExistent)

        expectThat(result).hasSize(0)
    }
}

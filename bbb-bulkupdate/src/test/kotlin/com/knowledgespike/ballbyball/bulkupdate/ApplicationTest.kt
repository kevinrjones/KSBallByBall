package com.knowledgespike.ballbyball.bulkupdate

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path

class ApplicationTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `should print help and return 0 for help arguments`() {
        val result = Application.execute(arrayOf("-h"))
        expectThat(result).isEqualTo(0)

        val resultLong = Application.execute(arrayOf("--help"))
        expectThat(resultLong).isEqualTo(0)

        val resultEmpty = Application.execute(arrayOf())
        expectThat(resultEmpty).isEqualTo(0)
    }

    @Test
    fun `should return 2 when required arguments are missing`() {
        val result = Application.execute(arrayOf("-c", "jdbc:sqlite:test.db"))
        expectThat(result).isEqualTo(2)
    }

    @Test
    fun `should return 0 when valid arguments are supplied and execution succeeds`() {
        val dbPath = tempDir.resolve("app_test.db")
        val csvDir = tempDir.resolve("csv")
        Files.createDirectories(csvDir)

        val args = arrayOf(
            "-c", "jdbc:sqlite:${dbPath.toAbsolutePath()}",
            "-bd", tempDir.toAbsolutePath().toString(),
            "-cd", "csv",
            "-dt", "sqlite"
        )

        val result = Application.execute(args)
        expectThat(result).isEqualTo(0)
    }
}

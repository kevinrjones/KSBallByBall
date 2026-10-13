package com.knowledgespike.ballbyball.parsecricsheet

import org.apache.commons.cli.ParseException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path

class ParseCricsheetArgumentsTest {
    @Test
    fun `given parser paths when parsed then directories resolve beneath base`() {
        val input = ParseCricsheetArguments.parse(
            arrayOf(
                "-bd", "/tmp/cricsheet", "-i", "raw", "-o", "parsed", "-np", "not-parsed",
                "--stats-connection-string", "jdbc:stats", "--bbb-connection-string", "jdbc:bbb"
            )
        )

        expectThat(input).isEqualTo(
            ParseCricsheetInput(
                baseDirectory = Path.of("/tmp/cricsheet"),
                inputDirectory = Path.of("/tmp/cricsheet/raw"),
                outputDirectory = Path.of("/tmp/cricsheet/parsed"),
                notParsedDirectory = Path.of("/tmp/cricsheet/not-parsed"),
                statsConnectionString = "jdbc:stats",
                bbbConnectionString = "jdbc:bbb"
            )
        )
    }

    @Test
    fun `given database settings when parsed then both connections are captured`() {
        val input = ParseCricsheetArguments.parse(
            arrayOf(
                "--base-directory", "/tmp/cricsheet",
                "--input", "raw",
                "--output", "parsed",
                "--not-parsed", "not-parsed",
                "--stats-connection-string", "jdbc:postgresql://stats/database",
                "--stats-user-name", "stats-user",
                "--stats-password-environment-variable", "STATS_DB_PASSWORD",
                "--bbb-connection-string", "jdbc:sqlite:/tmp/ball-by-ball.db",
                "--bbb-user-name", "bbb-user",
                "--bbb-password-environment-variable", "BBB_DB_PASSWORD"
            )
        )

        expectThat(input.statsConnectionString).isEqualTo("jdbc:postgresql://stats/database")
        expectThat(input.statsUserName).isEqualTo("stats-user")
        expectThat(input.statsPasswordEnvironmentVariable).isEqualTo("STATS_DB_PASSWORD")
        expectThat(input.bbbConnectionString).isEqualTo("jdbc:sqlite:/tmp/ball-by-ball.db")
        expectThat(input.bbbUserName).isEqualTo("bbb-user")
        expectThat(input.bbbPasswordEnvironmentVariable).isEqualTo("BBB_DB_PASSWORD")
    }

    @Test
    fun `given unsafe parser path when parsed then parsing fails`() {
        assertThrows<ParseException> {
            ParseCricsheetArguments.parse(
                arrayOf(
                    "--base-directory", "/tmp/cricsheet", "--input", "../raw",
                    "--output", "normalized", "--not-parsed", "not-parsed"
                )
            )
        }
    }

    @Test
    fun `given an output directory inside input when parsed then parsing fails`() {
        assertThrows<ParseException> {
            ParseCricsheetArguments.parse(
                arrayOf(
                    "--base-directory", "/tmp/cricsheet", "--input", "raw",
                    "--output", "raw/parsed", "--not-parsed", "not-parsed",
                    "--stats-connection-string", "jdbc:stats", "--bbb-connection-string", "jdbc:bbb"
                )
            )
        }
    }

    @Test
    fun `given an input directory symlink when parsed then parsing fails`() {
        val root = Files.createTempDirectory("parse-cricsheet-input-symlink")
        val outside = Files.createTempDirectory("parse-cricsheet-input-target")
        Files.createSymbolicLink(root.resolve("raw"), outside)

        assertThrows<ParseException> {
            ParseCricsheetArguments.parse(
                arrayOf(
                    "--base-directory", root.toString(), "--input", "raw",
                    "--output", "parsed", "--not-parsed", "not-parsed",
                    "--stats-connection-string", "jdbc:stats", "--bbb-connection-string", "jdbc:bbb"
                )
            )
        }
    }

    @Test
    fun `given an output directory symlink when parsed then parsing fails`() {
        val root = Files.createTempDirectory("parse-cricsheet-output-symlink")
        val outside = Files.createTempDirectory("parse-cricsheet-output-target")
        Files.createSymbolicLink(root.resolve("parsed"), outside)

        assertThrows<ParseException> {
            ParseCricsheetArguments.parse(
                arrayOf(
                    "--base-directory", root.toString(), "--input", "raw",
                    "--output", "parsed", "--not-parsed", "not-parsed",
                    "--stats-connection-string", "jdbc:stats", "--bbb-connection-string", "jdbc:bbb"
                )
            )
        }
    }

    @Test
    fun `given missing required database setting when parsed then parsing fails`() {
        assertThrows<ParseException> {
            ParseCricsheetArguments.parse(
                arrayOf(
                    "--base-directory", "/tmp/cricsheet", "--input", "raw",
                    "--output", "parsed", "--not-parsed", "not-parsed",
                    "--stats-connection-string", "jdbc:stats"
                )
            )
        }
    }
}
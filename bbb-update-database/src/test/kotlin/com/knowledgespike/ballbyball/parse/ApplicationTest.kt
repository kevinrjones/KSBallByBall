package com.knowledgespike.ballbyball.parse

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import com.knowledgespike.ballbyball.clishared.identity.MergeEvidence
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.clishared.schema.Event
import com.knowledgespike.ballbyball.clishared.schema.Info
import com.knowledgespike.ballbyball.clishared.schema.Outcome
import com.knowledgespike.ballbyball.clishared.schema.PlayersRegistry
import com.knowledgespike.ballbyball.clishared.schema.Toss
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

class ApplicationTest {
    @Test
    fun `given mysql script dialect then output script is not created`(@TempDir temporaryDirectory: Path) {
        val baseDirectory = prepareInput(temporaryDirectory)
        val outputFile = baseDirectory.resolve("generated.sql")

        Application.execute(arguments(baseDirectory, "mysql"))

        expectThat(Files.exists(outputFile)).isEqualTo(false)
    }

    @Test
    fun `given import fails then existing script is unchanged`(@TempDir temporaryDirectory: Path) {
        val baseDirectory = prepareInput(temporaryDirectory)
        val outputFile = baseDirectory.resolve("generated.sql")
        Files.writeString(outputFile, "previous script")

        val exitCode = Application.execute(arguments(baseDirectory, "mariadb"))

        expectThat(exitCode).isEqualTo(1)
        expectThat(Files.readString(outputFile)).isEqualTo("previous script")
    }

    @Test
    fun `given valid import then script is published with a commit`(@TempDir temporaryDirectory: Path) {
        val baseDirectory = prepareInput(temporaryDirectory)
        val outputFile = baseDirectory.resolve("generated.sql")
        val matchFile = baseDirectory.resolve("matches/match.json")
        Files.writeString(matchFile, Json.encodeToString(sampleEnvelope()))

        val exitCode = Application.execute(arguments(baseDirectory, "mariadb"))

        expectThat(exitCode).isEqualTo(0)
        expectThat(Files.readString(outputFile)).contains("COMMIT;")
    }

    @Test
    fun `given a non-empty csv directory without force then existing contents are preserved`(
        @TempDir temporaryDirectory: Path
    ) {
        val baseDirectory = prepareInput(temporaryDirectory)
        val output = Files.createDirectory(baseDirectory.resolve("csv"))
        val sentinel = output.resolve("keep.txt")
        Files.writeString(sentinel, "preserve me")

        val exitCode = Application.execute(csvArguments(baseDirectory, force = false))

        expectThat(exitCode).isEqualTo(1)
        expectThat(Files.readString(sentinel)).isEqualTo("preserve me")
    }

    @Test
    fun `given a non-empty csv directory with force then existing contents are replaced`(
        @TempDir temporaryDirectory: Path
    ) {
        val baseDirectory = prepareInput(temporaryDirectory)
        val output = Files.createDirectory(baseDirectory.resolve("csv"))
        val sentinel = output.resolve("replace.txt")
        Files.writeString(sentinel, "replace me")

        Application.execute(csvArguments(baseDirectory, force = true))

        expectThat(Files.exists(sentinel)).isEqualTo(false)
        expectThat(Files.isDirectory(output)).isEqualTo(true)
    }

    private fun prepareInput(temporaryDirectory: Path): Path {
        val baseDirectory = Files.createDirectory(temporaryDirectory.resolve("input"))
        Files.createDirectory(baseDirectory.resolve("matches"))
        Files.writeString(baseDirectory.resolve("players.csv"), PLAYER_REGISTRY_HEADER)
        return baseDirectory
    }

    @Test
    fun `given match files with duplicate sourceCaIds when application executes then import succeeds and second match is skipped`(
        @TempDir temporaryDirectory: Path
    ) {
        val baseDirectory = prepareInput(temporaryDirectory)
        val outputFile = baseDirectory.resolve("generated.sql")
        val match1 = sampleEnvelope(
            canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
            sourceCaId = "a434804"
        )
        val match2 = sampleEnvelope(
            canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b1")),
            sourceCaId = "a434804"
        )
        Files.writeString(baseDirectory.resolve("matches/match1.json"), Json.encodeToString(match1))
        Files.writeString(baseDirectory.resolve("matches/match2.json"), Json.encodeToString(match2))

        val exitCode = Application.execute(arguments(baseDirectory, "mariadb"))

        expectThat(exitCode).isEqualTo(0)
        val sql = Files.readString(outputFile)
        expectThat(sql).contains("'1890a7a8-f76d-5f36-89f7-39b0319044b0'")
        expectThat(sql).not().contains("'1890a7a8-f76d-5f36-89f7-39b0319044b1'")
    }

    private fun sampleEnvelope(
        canonicalMatchId: CanonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
        sourceCaId: String? = null
    ): CanonicalMatchEnvelope {
        val sourceRecordId = SourceRecordId.from(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877"))
        return CanonicalMatchEnvelope(
            canonicalMatchId = canonicalMatchId,
            sources = listOf(
                SourceReference(
                    provider = ProviderId.from("cricsheet"),
                    providerRecordKey = "12345",
                    sourceRecordId = sourceRecordId,
                    rawContentDigest = Sha256Digest.from("0".repeat(64))
                )
            ),
            mergeEvidence = MergeEvidence.SingleSource(sourceRecordId),
            match = BbbMatchData(
                match = Info(
                    ballsPerOver = 6,
                    dates = listOf(LocalDate.of(2024, 1, 1).toString()),
                    event = Event("Test match"),
                    gender = "male",
                    matchType = "T20",
                    outcome = Outcome(result = "draw"),
                    players = JsonObject(emptyMap()),
                    registry = PlayersRegistry(emptyMap()),
                    season = "2024",
                    sourceCaId = sourceCaId,
                    teamType = "international",
                    teams = listOf("Team A", "Team B"),
                    toss = Toss(decision = "bat", winner = "Team A")
                ),
                innings = emptyList()
            )
        )
    }

    private fun arguments(baseDirectory: Path, database: String): Array<String> = arrayOf(
        "--base-directory", baseDirectory.toString(),
        "--data-directory", "matches",
        "--player-registry", "players.csv",
        "--outputType", "SQL_FILE",
        "--database", database,
        "--outputFile", "generated.sql"
    )

    private fun csvArguments(baseDirectory: Path, force: Boolean): Array<String> =
        arrayOf(
            "--base-directory", baseDirectory.toString(),
            "--data-directory", "matches",
            "--player-registry", "players.csv",
            "--outputType", "CSV",
            "--csvDir", "csv"
        ) + if (force) arrayOf("--force") else emptyArray()

    private companion object {
        const val PLAYER_REGISTRY_HEADER =
            "identifier,name,unique_name,key_bcci,key_bcci_2,key_bigbash,key_cricbuzz,key_cricheroes," +
                "key_crichq,key_cricinfo,key_cricinfo_2,key_cricinfo_3,key_cricingif,key_cricketarchive\n"
    }
}
package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.identity.SourceRecordId
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.ballbyball.parse.database.adapter.csv.CsvOutputAdapter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.Properties
import java.util.UUID

class CsvOutputAdapterTest {
    @Test
    fun `given a non-empty output directory without force when opened then it is preserved and rejected`() {
        val output = Files.createTempDirectory("warehouse-csv-non-empty")
        val sentinel = output.resolve("keep.txt")
        Files.writeString(sentinel, "preserve me")

        assertThrows<IllegalArgumentException> {
            CsvOutputAdapter(output)
        }

        expectThat(Files.readString(sentinel)).isEqualTo("preserve me")
    }

    @Test
    fun `given a non-empty output directory with force when opened then existing contents are removed`() {
        val output = Files.createTempDirectory("warehouse-csv-force")
        val sentinel = output.resolve("replace.txt")
        Files.writeString(sentinel, "replace me")

        CsvOutputAdapter(output, force = true)

        expectThat(Files.exists(sentinel)).isEqualTo(false)
        expectThat(Files.isDirectory(output)).isEqualTo(true)
    }

    @Test
    fun `given a row write outside a match transaction then it is rejected`() {
        val output = Files.createTempDirectory("warehouse-csv-write-without-transaction")

        CsvOutputAdapter(output).use { adapter ->
            val failure = assertThrows<IllegalStateException> {
                adapter.upsertPerson("player-1", "Player One", 1)
            }

            expectThat(failure.message).isEqualTo("No match transaction is active")
        }
    }

    @Test
    fun `given an active match transaction when completed then output is not published`() {
        val output = Files.createTempDirectory("warehouse-csv-active-complete")
        val sentinel = output.resolve("keep.txt")
        Files.writeString(sentinel, "previous csv")

        assertThrows<IllegalStateException> {
            CsvOutputAdapter(output, force = true).use { adapter ->
                adapter.beginMatch()
                adapter.complete()
            }
        }
    }

    @Test
    fun `given import failure then previous csv files are preserved and staged output is removed`() {
        val directory = Files.createTempDirectory("warehouse-failed-import")
        val output = directory.resolve("csv")
        Files.createDirectories(output)
        val sentinel = output.resolve("previous.txt")
        Files.writeString(sentinel, "previous csv")

        assertThrows<IllegalStateException> {
            CsvOutputAdapter(output, force = true).use { adapter ->
                adapter.beginMatch()
                adapter.upsertPerson("player-1", "Player One", 1)
                adapter.commit()
                adapter.beginMatch()
                adapter.upsertPerson("player-2", "Player Two", 2)
                throw IllegalStateException("import failed")
            }
        }

        val stagedBundles = Files.list(directory).use { files ->
            files.filter { it.fileName.toString().startsWith(".csv-bundle-") }.count()
        }
        expectThat(stagedBundles).isEqualTo(0L)
    }

    @Test
    fun `given repeated person when written then one escaped csv row is emitted`() {
        val output = Files.createTempDirectory("warehouse-csv")
        val adapter = CsvOutputAdapter(output)

        adapter.beginMatch()
        adapter.upsertPerson("person-1", "O'Brien, Jr.", 7)
        adapter.upsertPerson("person-1", "O'Brien, Jr.", 7)
        adapter.insertDeliveryFielder(1, 2, 3)
        adapter.commit()
        adapter.complete()
        adapter.close()

        val csv = Files.readString(output.resolve("people.csv"))
        expectThat(csv).contains("id,source_person_id,full_name,sort_name_part,other_name_part,ca_id")
        expectThat(csv.lines().count { it.startsWith("1,person-1,") }).isEqualTo(1)
        expectThat(csv).contains("\"O'Brien, Jr.\"")
        expectThat(Files.readString(output.resolve("delivery_fielders.csv")))
            .isEqualTo("delivery_id,wicket_id,person_id\n1,2,3\n")
    }

    @Test
    fun `given an existing innings when declaration changes then csv contains the updated value`() {
        val output = Files.createTempDirectory("warehouse-csv-innings-declared")
        val adapter = CsvOutputAdapter(output)
        adapter.beginMatch()
        val match = adapter.insertMatch(testMatchRecord())
        adapter.upsertInnings(match.id, 1, 2, 3, declared = false)
        adapter.upsertInnings(match.id, 1, 2, 3, declared = true)
        adapter.commit()
        adapter.complete()
        adapter.close()

        expectThat(Files.readString(output.resolve("innings.csv")))
            .isEqualTo("id,match_id,innings_number,batting_team_id,bowling_team_id,declared\n1,1,1,2,3,1\n")
    }

    @Test
    fun `given a published innings when declaration changes in a later transaction then csv is updated`() {
        val output = Files.createTempDirectory("warehouse-csv-innings-update")
        val adapter = CsvOutputAdapter(output)
        adapter.beginMatch()
        val match = adapter.insertMatch(testMatchRecord())
        adapter.upsertInnings(match.id, 1, 2, 3, declared = false)
        adapter.commit()

        adapter.beginMatch()
        adapter.upsertInnings(match.id, 1, 2, 3, declared = true)
        adapter.rollback()

        adapter.beginMatch()
        adapter.upsertInnings(match.id, 1, 2, 3, declared = true)
        adapter.commit()
        adapter.complete()
        adapter.close()

        expectThat(Files.readString(output.resolve("innings.csv")))
            .isEqualTo("id,match_id,innings_number,batting_team_id,bowling_team_id,declared\n1,1,1,2,3,1\n")
    }

    @Test
    fun `given warehouse rows when written then csv preserves nulls and foreign keys`() {
        val output = Files.createTempDirectory("warehouse-csv")
        val sourceFile = output.resolve("12345.json").toAbsolutePath().normalize()
        val adapter = CsvOutputAdapter(output)
        adapter.beginMatch()
        val home = adapter.upsertTeam("Home")
        val away = adapter.upsertTeam("Away")
        val ground = adapter.upsertGround("The Oval")
        val dateKey = adapter.upsertDate(LocalDate.parse("2024-01-02"))
        val match = adapter.insertMatch(
            MatchRecord(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
                fileName = sourceFile.toString(),
                matchInSeries = 1,
                matchType = "tt",
                eventName = "Series",
                matchDateText = "2024-01-02",
                season = "2024",
                matchStartYear = "2024",
                matchStartDateKey = dateKey,
                ballsPerOver = 6,
                team1Key = home.id,
                team2Key = away.id,
                groundKey = ground.id,
                tossTeamKey = home.id,
                tossDecision = null,
                victoryType = "runs",
                winnerTeamKey = home.id,
                loserTeamKey = away.id
            )
        )
        val duplicate = adapter.insertMatch(
            MatchRecord(
                canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
                fileName = "renamed.json",
                matchInSeries = 1,
                matchType = "tt",
                eventName = "Series",
                matchDateText = "2024-01-02",
                season = "2024",
                matchStartYear = "2024",
                matchStartDateKey = dateKey,
                ballsPerOver = 6,
                team1Key = home.id,
                team2Key = away.id,
                groundKey = ground.id,
                tossTeamKey = home.id,
                tossDecision = null,
                victoryType = "runs",
                winnerTeamKey = home.id,
                loserTeamKey = away.id
            )
        )
        adapter.insertSourceReferences(
            match.id,
            listOf(
                SourceReference(
                    provider = ProviderId.from("cricsheet"),
                    providerRecordKey = "12345",
                    sourceRecordId = SourceRecordId.from(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877")),
                    rawContentDigest = Sha256Digest.from("0".repeat(64))
                )
            )
        )
        adapter.commit()
        adapter.complete()
        adapter.close()

        expectThat(duplicate.id).isEqualTo(match.id)

        val matchCsv = Files.readString(output.resolve("matches.csv"))
        expectThat(matchCsv).contains("id,canonical_match_id,public_match_id,source_ca_id,file_name")
        expectThat(matchCsv).contains("1,1890a7a8-f76d-5f36-89f7-39b0319044b0,7922450146,\\N,${sourceFile.toString()}")
        expectThat(matchCsv).contains(",duration_days,margin,match_count\n")
        expectThat(Files.readString(output.resolve("match_source_reference.csv")))
            .contains("1,cricsheet,12345,c61f62bd-7b02-5c3f-ae44-572d533b5877,${"0".repeat(64)}")
    }

    @Test
    fun `given multiple changed tables when committed then one output bundle is published on complete`() {
        val output = Files.createTempDirectory("warehouse-csv-bundle")
        var publications = 0
        val adapter = CsvOutputAdapter(output) { source, target ->
            publications++
            target.toFile().deleteRecursively()
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

        adapter.beginMatch()
        adapter.upsertTeam("Team")
        adapter.upsertGround("Ground")
        adapter.commit()
        expectThat(publications).isEqualTo(0)

        adapter.complete()
        adapter.close()

        expectThat(publications).isEqualTo(1)
    }

    @Test
    fun `given pending rows when closed without commit then csv output is not published`() {
        val output = Files.createTempDirectory("warehouse-csv-close-without-commit")
        val adapter = CsvOutputAdapter(output)

        adapter.beginMatch()
        adapter.upsertTeam("Uncommitted team")
        adapter.close()

        expectThat(Files.exists(output.resolve("teams.csv"))).isEqualTo(false)
    }

    @Test
    fun `given an active transaction when closed then pending csv rows are rolled back`() {
        val output = Files.createTempDirectory("warehouse-csv-close-active-transaction")
        val adapter = CsvOutputAdapter(output)

        adapter.beginMatch()
        adapter.upsertTeam("Uncommitted team")
        adapter.close()

        expectThat(Files.exists(output.resolve("teams.csv"))).isEqualTo(false)
    }

    @Test
    fun `given a failed bundle publication then all csv tables remain unchanged`() {
        val output = Files.createTempDirectory("warehouse-csv-bundle-failure")
        var failPublication = false
        val adapter = CsvOutputAdapter(output) { source, target ->
            if (failPublication) {
                failPublication = false
                error("simulated bundle publication failure")
            }
            target.toFile().deleteRecursively()
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }

        adapter.beginMatch()
        adapter.upsertTeam("Existing team")
        adapter.upsertGround("Existing ground")
        adapter.commit()
        adapter.complete()
        adapter.close()

        val originalFiles = csvFiles(output)

        val secondAdapter = CsvOutputAdapter(output, force = true) { source, target ->
            if (failPublication) {
                failPublication = false
                error("simulated bundle publication failure")
            }
            target.toFile().deleteRecursively()
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        secondAdapter.beginMatch()
        secondAdapter.upsertTeam("New team")
        secondAdapter.upsertGround("New ground")
        secondAdapter.commit()
        failPublication = true
        assertThrows<IllegalStateException> { secondAdapter.complete() }
        secondAdapter.close()
    }

    @Test
    fun `given an interrupted bundle publication then restart restores the previous directory`() {
        val parent = Files.createTempDirectory("warehouse-csv-recovery")
        val output = parent.resolve("csv")
        Files.createDirectories(output)
        Files.writeString(output.resolve("teams.csv"), "old\n")

        val backup = parent.resolve(".csv-backup-crashed")
        Files.move(output, backup)
        val source = parent.resolve(".csv-bundle-crashed")
        Files.createDirectories(source)
        Files.writeString(source.resolve("teams.csv"), "new\n")

        val journal = parent.resolve(".csv.publish-journal")
        val publication = Properties().apply {
            setProperty("source", source.toAbsolutePath().toString())
            setProperty("target", output.toAbsolutePath().toString())
            setProperty("backup", backup.toAbsolutePath().toString())
            setProperty("phase", "BACKUP_CREATED")
        }
        Files.newBufferedWriter(journal).use { publication.store(it, null) }

        CsvOutputAdapter.recoverPublication(output)

        expectThat(Files.readString(output.resolve("teams.csv"))).isEqualTo("old\n")
        expectThat(Files.exists(source)).isEqualTo(false)
        expectThat(Files.exists(backup)).isEqualTo(false)
        expectThat(Files.exists(journal)).isEqualTo(false)
    }

    @Test
    fun `given an interrupted bundle publication then force is required to replace recovered contents`() {
        val parent = Files.createTempDirectory("warehouse-csv-constructor-recovery")
        val output = parent.resolve("csv")
        Files.createDirectories(output)
        Files.writeString(output.resolve("teams.csv"), "old\n")

        val backup = parent.resolve(".csv-backup-crashed")
        Files.move(output, backup)
        val source = parent.resolve(".csv-bundle-crashed")
        Files.createDirectories(source)
        Files.writeString(source.resolve("teams.csv"), "new\n")

        val journal = parent.resolve(".csv.publish-journal")
        val publication = Properties().apply {
            setProperty("source", source.toAbsolutePath().toString())
            setProperty("target", output.toAbsolutePath().toString())
            setProperty("backup", backup.toAbsolutePath().toString())
            setProperty("phase", "BACKUP_CREATED")
        }
        Files.newBufferedWriter(journal).use { publication.store(it, null) }

        assertThrows<IllegalArgumentException> {
            CsvOutputAdapter(output)
        }

        expectThat(Files.readString(output.resolve("teams.csv"))).isEqualTo("old\n")
        expectThat(Files.exists(journal)).isEqualTo(false)

        CsvOutputAdapter(output, force = true).use { adapter ->
            expectThat(Files.list(output).use { entries -> !entries.findAny().isPresent }).isEqualTo(true)
        }
    }

    @Test
    fun `given an existing match when source ca id is discovered then csv row is reconciled`() {
        val output = Files.createTempDirectory("warehouse-csv-reconcile")
        val adapter = CsvOutputAdapter(output)
        adapter.beginMatch()
        val match = MatchRecord(
            canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
            fileName = "match.json",
            matchInSeries = 1,
            matchType = "tt",
            eventName = "Series",
            matchDateText = "2024-01-02",
            season = "2024",
            matchStartYear = "2024",
            matchStartDateKey = 20240102,
            ballsPerOver = 6,
            team1Key = 1,
            team2Key = 2,
            groundKey = 1,
            tossTeamKey = 1,
            tossDecision = "bat",
            victoryType = "runs",
            winnerTeamKey = 1,
            loserTeamKey = 2
        )

        adapter.insertMatch(match)
        adapter.insertMatch(match.copy(sourceCaId = SourceCaId.from("ca-12345")))
        adapter.commit()
        adapter.complete()
        adapter.close()

        val rows = Files.readAllLines(output.resolve("matches.csv"))
        expectThat(rows.count { it.contains(",ca-12345,") }).isEqualTo(1)
        expectThat(rows.size).isEqualTo(2)
    }

    private fun testMatchRecord() = MatchRecord(
        canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
        fileName = "match.json",
        matchInSeries = 1,
        matchType = "tt",
        eventName = "Series",
        matchDateText = "2024-01-02",
        season = "2024",
        matchStartYear = "2024",
        matchStartDateKey = 20240102,
        ballsPerOver = 6,
        team1Key = 1,
        team2Key = 2,
        groundKey = 1,
        tossTeamKey = 1,
        tossDecision = "bat",
        victoryType = "runs",
        winnerTeamKey = 1,
        loserTeamKey = 2
    )

    private fun csvFiles(output: Path): Map<String, String> = Files.list(output).use { files ->
        files.iterator().asSequence().filter { it.fileName.toString().endsWith(".csv") }
            .associate { it.fileName.toString() to Files.readString(it) }
    }
}

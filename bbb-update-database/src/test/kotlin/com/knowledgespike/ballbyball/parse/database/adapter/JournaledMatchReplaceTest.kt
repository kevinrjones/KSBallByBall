package com.knowledgespike.ballbyball.parse.database.adapter

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Properties

class JournaledMatchReplaceTest {
    @Test
    fun `given file paths when replacing then publish through the shared journal utility`() {
        val directory = Files.createTempDirectory("journaled-match-file")
        val source = directory.resolve("source.sql")
        val target = directory.resolve("target.sql")
        Files.writeString(source, "new")
        Files.writeString(target, "old")

        JournaledMatchReplace.replace(source, target, JournaledReplaceKind.FILE)

        expectThat(Files.readString(target)).isEqualTo("new")
        expectThat(Files.exists(source)).isEqualTo(false)
        expectThat(JournaledMatchReplace.hasPendingJournal(target, JournaledReplaceKind.FILE)).isEqualTo(false)
    }

    @Test
    fun `given directory paths when replacing then publish through the shared journal utility`() {
        val directory = Files.createTempDirectory("journaled-match-directory")
        val source = Files.createDirectory(directory.resolve("source"))
        val target = Files.createDirectory(directory.resolve("target"))
        Files.writeString(source.resolve("matches.csv"), "new")
        Files.writeString(target.resolve("matches.csv"), "old")

        JournaledMatchReplace.replace(source, target, JournaledReplaceKind.DIRECTORY)

        expectThat(Files.readString(target.resolve("matches.csv"))).isEqualTo("new")
        expectThat(Files.exists(source)).isEqualTo(false)
    }

    @Test
    fun `given unsupported atomic directory moves when replacing then use the fallback move`() {
        val directory = Files.createTempDirectory("journaled-match-directory-fallback")
        val source = Files.createDirectory(directory.resolve("source"))
        val target = Files.createDirectory(directory.resolve("target"))
        Files.writeString(source.resolve("matches.csv"), "new")
        Files.writeString(target.resolve("matches.csv"), "old")
        val attemptedMoveOptions = mutableListOf<List<CopyOption>>()

        JournaledMatchReplace.replace(
            source = source,
            target = target,
            kind = JournaledReplaceKind.DIRECTORY,
            move = { from, to, options ->
                attemptedMoveOptions += options.toList()
                if (StandardCopyOption.ATOMIC_MOVE in options) {
                    throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "unsupported")
                }
                Files.move(from, to, *options)
            }
        )

        expectThat(attemptedMoveOptions.filter { StandardCopyOption.ATOMIC_MOVE in it }).hasSize(2)
        expectThat(Files.readString(target.resolve("matches.csv"))).isEqualTo("new")
    }

    @Test
    fun `given a legacy source moved file journal when recovered then published target is retained`() {
        val directory = Files.createTempDirectory("journaled-match-legacy-file")
        val source = directory.resolve("source.sql")
        val target = directory.resolve("target.sql")
        val backup = directory.resolve("target-backup.sql")
        val journal = directory.resolve(".target.sql.replace-journal")
        Files.writeString(target, "new")
        Files.writeString(backup, "old")
        val properties = Properties().apply {
            setProperty("source", source.toAbsolutePath().toString())
            setProperty("target", target.toAbsolutePath().toString())
            setProperty("backup", backup.toAbsolutePath().toString())
            setProperty("phase", "SOURCE_MOVED")
            setProperty("targetExisted", "true")
        }
        Files.newBufferedWriter(journal).use { properties.store(it, null) }

        expectThat(JournaledMatchReplace.recover(target, JournaledReplaceKind.FILE)).isEqualTo(true)

        expectThat(Files.readString(target)).isEqualTo("new")
        expectThat(Files.exists(backup)).isEqualTo(false)
        expectThat(Files.exists(journal)).isEqualTo(false)
    }

    @Test
    fun `given backup taken journal with target present when recovered then backup is restored for both kinds`() {
        val artifacts = listOf(
            JournaledReplaceKind.FILE,
            JournaledReplaceKind.DIRECTORY
        ).map { kind ->
            val files = createJournal(kind, targetExists = true)

            JournaledMatchReplace.recover(files.target, kind)

            expectThat(readContent(files.target, kind)).isEqualTo("old")
            files
        }

        artifacts.forEach { files ->
            expectThat(Files.exists(files.source)).isEqualTo(false)
            expectThat(Files.exists(files.backup)).isEqualTo(false)
            expectThat(Files.exists(files.journal)).isEqualTo(false)
        }
    }

    @Test
    fun `given backup taken journal with target absent when recovered then backup is restored for both kinds`() {
        val artifacts = listOf(
            JournaledReplaceKind.FILE,
            JournaledReplaceKind.DIRECTORY
        ).map { kind ->
            val files = createJournal(kind, targetExists = false)

            JournaledMatchReplace.recover(files.target, kind)

            expectThat(readContent(files.target, kind)).isEqualTo("old")
            files
        }

        artifacts.forEach { files ->
            expectThat(Files.exists(files.source)).isEqualTo(false)
            expectThat(Files.exists(files.backup)).isEqualTo(false)
            expectThat(Files.exists(files.journal)).isEqualTo(false)
        }
    }

    @Test
    fun `given staged journal with backup when recovered then backup is restored for both kinds`() {
        JournaledReplaceKind.entries.forEach { kind ->
            val files = createJournal(kind, targetExists = true, phase = "PREPARED")

            JournaledMatchReplace.recover(files.target, kind)

            expectThat(readContent(files.target, kind)).isEqualTo("old")
            expectThat(Files.exists(files.source)).isEqualTo(false)
        }
    }

    @Test
    fun `given published journal when recovered then published target is retained for both kinds`() {
        JournaledReplaceKind.entries.forEach { kind ->
            val files = createJournal(kind, targetExists = true, phase = "PUBLISHED")

            JournaledMatchReplace.recover(files.target, kind)

            expectThat(readContent(files.target, kind)).isEqualTo("new")
            expectThat(Files.exists(files.source)).isEqualTo(false)
            expectThat(Files.exists(files.backup)).isEqualTo(false)
            expectThat(Files.exists(files.journal)).isEqualTo(false)
        }
    }

    private fun createJournal(
        kind: JournaledReplaceKind,
        targetExists: Boolean,
        phase: String = "BACKUP_CREATED"
    ): JournalArtifacts {
        val directory = Files.createTempDirectory("journaled-match-backup-taken")
        val suffix = if (kind == JournaledReplaceKind.FILE) ".sql" else ""
        val source = directory.resolve("source$suffix")
        val target = directory.resolve("target$suffix")
        val backup = directory.resolve("backup$suffix")
        val journalSuffix = if (kind == JournaledReplaceKind.FILE) "replace-journal" else "publish-journal"
        val journal = directory.resolve(".${target.fileName}.$journalSuffix")

        writeContent(source, kind, "source")
        writeContent(backup, kind, "old")
        if (targetExists) writeContent(target, kind, "new")

        val properties = Properties().apply {
            setProperty("source", source.toAbsolutePath().toString())
            setProperty("target", target.toAbsolutePath().toString())
            setProperty("backup", backup.toAbsolutePath().toString())
            setProperty("phase", phase)
            setProperty("targetExisted", "true")
        }
        Files.newBufferedWriter(journal).use { properties.store(it, null) }

        return JournalArtifacts(source, target, backup, journal)
    }

    private fun writeContent(path: Path, kind: JournaledReplaceKind, content: String) {
        if (kind == JournaledReplaceKind.DIRECTORY) {
            Files.createDirectory(path)
            Files.writeString(path.resolve("matches.csv"), content)
        } else {
            Files.writeString(path, content)
        }
    }

    private fun readContent(path: Path, kind: JournaledReplaceKind): String =
        if (kind == JournaledReplaceKind.DIRECTORY) {
            Files.readString(path.resolve("matches.csv"))
        } else {
            Files.readString(path)
        }
}

private data class JournalArtifacts(
    val source: Path,
    val target: Path,
    val backup: Path,
    val journal: Path
)
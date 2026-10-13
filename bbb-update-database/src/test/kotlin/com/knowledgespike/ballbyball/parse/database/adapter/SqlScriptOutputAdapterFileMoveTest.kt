package com.knowledgespike.ballbyball.parse.database.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardCopyOption
import java.util.Properties

class SqlScriptOutputAdapterFileMoveTest {
    @Test
    fun `given an unsupported atomic move then replacement move is used`() {
        val directory = Files.createTempDirectory("sql-script-move")
        val source = directory.resolve("source.sql")
        val target = directory.resolve("target.sql")
        Files.writeString(source, "new")
        Files.writeString(target, "old")
        var attemptedAtomicMove = false

        JournaledMatchReplace.replace(source, target, JournaledReplaceKind.FILE, move = { from, to, options ->
            if (options.contains(StandardCopyOption.ATOMIC_MOVE)) {
                attemptedAtomicMove = true
                throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "test")
            }
            Files.move(from, to, *options)
        })

        expectThat(attemptedAtomicMove).isEqualTo(true)
        expectThat(Files.readString(target)).isEqualTo("new")
    }

    @Test
    fun `given an interrupted fallback replacement then the next replacement restores the previous file`() {
        val directory = Files.createTempDirectory("sql-script-recovery")
        val target = directory.resolve("target.sql")
        val backup = directory.resolve("target-backup.sql")
        val source = directory.resolve("source.sql")
        Files.writeString(target, "old")
        Files.writeString(source, "new")
        Files.move(target, backup)
        val journal = directory.resolve(".target.sql.replace-journal")
        Properties().apply {
            setProperty("source", source.toAbsolutePath().toString())
            setProperty("target", target.toAbsolutePath().toString())
            setProperty("backup", backup.toAbsolutePath().toString())
            setProperty("phase", "BACKUP_CREATED")
        }.let { properties ->
            Files.newBufferedWriter(journal).use { properties.store(it, null) }
        }

        JournaledMatchReplace.replace(source, target, JournaledReplaceKind.FILE)

        expectThat(Files.readString(target)).isEqualTo("new")
        expectThat(Files.exists(journal)).isEqualTo(false)
        expectThat(Files.exists(backup)).isEqualTo(false)
    }

    @Test
    fun `given a failed fallback replacement then the previous file is restored`() {
        val directory = Files.createTempDirectory("sql-script-failed-replacement")
        val target = directory.resolve("target.sql")
        val source = directory.resolve("source.sql")
        Files.writeString(target, "old")
        Files.writeString(source, "new")

        assertThrows<IllegalStateException> {
            JournaledMatchReplace.replace(source, target, JournaledReplaceKind.FILE, move = { from, to, options ->
                if (options.contains(StandardCopyOption.ATOMIC_MOVE)) {
                    throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "test")
                }
                if (from == source) throw IllegalStateException("replacement failed")
                Files.move(from, to, *options)
            })
        }

        expectThat(Files.readString(target)).isEqualTo("old")
    }

    @Test
    fun `given a failed fallback replacement without a previous file then the new file is removed`() {
        val directory = Files.createTempDirectory("sql-script-failed-new-replacement")
        val target = directory.resolve("target.sql")
        val source = directory.resolve("source.sql")
        Files.writeString(source, "new")

        assertThrows<IllegalStateException> {
            JournaledMatchReplace.replace(source, target, JournaledReplaceKind.FILE, move = { from, to, options ->
                if (options.contains(StandardCopyOption.ATOMIC_MOVE)) {
                    throw AtomicMoveNotSupportedException(from.toString(), to.toString(), "test")
                }
                Files.move(from, to, *options)
                if (from == source) throw IllegalStateException("journal persistence failed")
                to
            })
        }

        expectThat(Files.exists(target)).isEqualTo(false)
        expectThat(Files.exists(source)).isEqualTo(false)
    }

    @Test
    fun `given a prepared journal without a previous file then recovery preserves a moved target`() {
        val directory = Files.createTempDirectory("sql-script-recovery-without-backup")
        val target = directory.resolve("target.sql")
        val source = directory.resolve("missing-source.sql")
        val backup = directory.resolve("target-backup.sql")
        Files.writeString(target, "published")

        Properties().apply {
            setProperty("source", source.toAbsolutePath().toString())
            setProperty("target", target.toAbsolutePath().toString())
            setProperty("backup", backup.toAbsolutePath().toString())
            setProperty("phase", "PREPARED")
            setProperty("targetExisted", "false")
        }.let { properties ->
            Files.newBufferedWriter(directory.resolve(".target.sql.replace-journal")).use { properties.store(it, null) }
        }

        assertThrows<NoSuchFileException> {
            JournaledMatchReplace.replace(source, target, JournaledReplaceKind.FILE)
        }

        expectThat(Files.readString(target)).isEqualTo("published")
    }
}
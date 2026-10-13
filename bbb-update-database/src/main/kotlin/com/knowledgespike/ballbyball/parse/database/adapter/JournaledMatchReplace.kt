package com.knowledgespike.ballbyball.parse.database.adapter

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Properties

internal enum class JournaledReplaceKind {
    FILE,
    DIRECTORY
}

internal enum class JournaledReplacePhase(val journalValue: String) {
    STAGED("PREPARED"),
    BACKUP_TAKEN("BACKUP_CREATED"),
    PUBLISHED("PUBLISHED");

    companion object {
        fun fromJournalValue(value: String): JournaledReplacePhase = when (value) {
            "PREPARED" -> STAGED
            "BACKUP_CREATED" -> BACKUP_TAKEN
            "PUBLISHED", "SOURCE_MOVED" -> PUBLISHED
            else -> error("Unknown journal phase: $value")
        }
    }
}

internal data class JournaledReplaceRecord(
    val source: Path,
    val target: Path,
    val backup: Path,
    val phase: JournaledReplacePhase,
    val targetExisted: Boolean
)

internal typealias JournaledReplaceJournalWriter = (Path, JournaledReplaceRecord) -> Unit

/** Replaces a single file or a directory bundle using a durable, recoverable journal. */
internal object JournaledMatchReplace {
    internal fun syncDirectory(path: Path) = forceDirectory(path)

    internal fun hasPendingJournal(target: Path, kind: JournaledReplaceKind): Boolean =
        Files.exists(journalPath(target, kind))

    private data class PathOperations(
        val move: (Path, Path) -> Unit,
        val delete: (Path) -> Unit,
        val deleteBackup: (Path) -> Unit
    )

    fun replace(
        source: Path,
        target: Path,
        kind: JournaledReplaceKind,
        journalWriter: JournaledReplaceJournalWriter? = null,
        move: (Path, Path, Array<out CopyOption>) -> Path = { from, to, options -> Files.move(from, to, *options) },
        backupCleanup: (Path) -> Unit = ::deleteDirectory,
        directorySync: (Path) -> Unit = ::forceDirectory
    ) {
        recoverJournal(target, kind, source)
        if (kind == JournaledReplaceKind.FILE) {
            try {
                move(source, target, arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING))
                return
            } catch (_: AtomicMoveNotSupportedException) {
                // Use the journaled path when the file system cannot publish atomically.
            }
        }
        publishNonAtomically(source, target, kind, journalWriter, move, backupCleanup, directorySync)
    }

    fun recover(target: Path, kind: JournaledReplaceKind): Boolean = recoverJournal(target, kind, sourceToPreserve = null)

    private fun recoverJournal(target: Path, kind: JournaledReplaceKind, sourceToPreserve: Path?): Boolean {
        val journal = journalPath(target, kind)
        if (!Files.exists(journal)) return false
        val properties = Properties().apply { Files.newInputStream(journal).use(::load) }
        val record = JournaledReplaceRecord(
            source = Path.of(requireNotNull(properties.getProperty("source"))),
            target = Path.of(requireNotNull(properties.getProperty("target"))),
            backup = Path.of(requireNotNull(properties.getProperty("backup"))),
            phase = JournaledReplacePhase.fromJournalValue(requireNotNull(properties.getProperty("phase"))),
            targetExisted = properties.getProperty("targetExisted")?.toBoolean() ?: true
        )
        val operations = pathOperations(
            kind,
            move = { from, to, options -> Files.move(from, to, *options) },
            backupCleanup = ::deleteDirectory
        )
        if (record.phase == JournaledReplacePhase.PUBLISHED) {
            operations.delete(record.source)
            operations.deleteBackup(record.backup)
        } else {
            restoreUnpublished(
                record,
                operations,
                removeNewTargetWithoutBackup = false,
                sourceToPreserve = sourceToPreserve
            )
            operations.deleteBackup(record.backup)
        }
        Files.deleteIfExists(journal)
        return true
    }

    private fun publishNonAtomically(
        source: Path,
        target: Path,
        kind: JournaledReplaceKind,
        journalWriter: JournaledReplaceJournalWriter?,
        move: (Path, Path, Array<out CopyOption>) -> Path,
        backupCleanup: (Path) -> Unit,
        directorySync: (Path) -> Unit
    ) {
        val operations = pathOperations(kind, move, backupCleanup)
        val targetExisted = Files.exists(target)
        val backup = createBackupPath(target, kind)
        val journal = journalPath(target, kind)
        val staged = JournaledReplaceRecord(source, target, backup, JournaledReplacePhase.STAGED, targetExisted)
        persistJournal(journal, staged, journalWriter, directorySync)
        var published = false

        try {
            if (targetExisted) operations.move(target, backup)
            persistJournal(
                journal,
                staged.copy(phase = JournaledReplacePhase.BACKUP_TAKEN),
                journalWriter,
                directorySync
            )
            operations.move(source, target)
            persistJournal(
                journal,
                staged.copy(phase = JournaledReplacePhase.PUBLISHED),
                journalWriter,
                directorySync
            )
            published = true
            operations.deleteBackup(backup)
            Files.deleteIfExists(journal)
        } catch (failure: Exception) {
            if (!published) {
                try {
                    restoreUnpublished(staged, operations, removeNewTargetWithoutBackup = true)
                    Files.deleteIfExists(journal)
                } catch (restoreFailure: Exception) {
                    failure.addSuppressed(restoreFailure)
                }
            }
            throw failure
        } finally {
            if (!Files.exists(journal)) {
                operations.delete(source)
                operations.delete(backup)
            }
        }
    }

    private fun restoreUnpublished(
        record: JournaledReplaceRecord,
        operations: PathOperations,
        removeNewTargetWithoutBackup: Boolean,
        sourceToPreserve: Path? = null
    ) {
        if (Files.exists(record.backup)) {
            if (Files.exists(record.target)) operations.delete(record.target)
            operations.move(record.backup, record.target)
        } else if (removeNewTargetWithoutBackup && !record.targetExisted) {
            operations.delete(record.target)
        }
        if (!samePath(record.source, sourceToPreserve)) operations.delete(record.source)
    }

    private fun samePath(first: Path, second: Path?): Boolean =
        second != null && first.toAbsolutePath().normalize() == second.toAbsolutePath().normalize()

    private fun pathOperations(
        kind: JournaledReplaceKind,
        move: (Path, Path, Array<out CopyOption>) -> Path,
        backupCleanup: (Path) -> Unit
    ): PathOperations = when (kind) {
        JournaledReplaceKind.FILE -> PathOperations(
            move = { from, to -> move(from, to, arrayOf(StandardCopyOption.REPLACE_EXISTING)) },
            delete = { Files.deleteIfExists(it) },
            deleteBackup = { Files.deleteIfExists(it) }
        )

        JournaledReplaceKind.DIRECTORY -> PathOperations(
            move = { from, to -> moveDirectory(from, to, move) },
            delete = ::deleteDirectory,
            deleteBackup = backupCleanup
        )
    }

    private fun createBackupPath(target: Path, kind: JournaledReplaceKind): Path {
        val parent = target.toAbsolutePath().parent
        val backup = when (kind) {
            JournaledReplaceKind.FILE -> Files.createTempFile(parent, ".${target.fileName}.backup-", ".tmp")
            JournaledReplaceKind.DIRECTORY -> Files.createTempDirectory(parent, ".${target.fileName}-backup-")
        }
        when (kind) {
            JournaledReplaceKind.FILE -> Files.deleteIfExists(backup)
            JournaledReplaceKind.DIRECTORY -> deleteDirectory(backup)
        }
        return backup
    }

    private fun moveDirectory(
        source: Path,
        target: Path,
        move: (Path, Path, Array<out CopyOption>) -> Path
    ) {
        try {
            move(source, target, arrayOf(StandardCopyOption.ATOMIC_MOVE))
        } catch (_: AtomicMoveNotSupportedException) {
            move(source, target, arrayOf(StandardCopyOption.REPLACE_EXISTING))
        }
    }

    private fun persistJournal(
        journal: Path,
        record: JournaledReplaceRecord,
        journalWriter: JournaledReplaceJournalWriter?,
        directorySync: (Path) -> Unit
    ) {
        journalWriter?.let { it(journal, record) } ?: writeJournal(journal, record, directorySync)
    }

    private fun writeJournal(journal: Path, record: JournaledReplaceRecord, directorySync: (Path) -> Unit) {
        val properties = Properties().apply {
            setProperty("source", record.source.toAbsolutePath().toString())
            setProperty("target", record.target.toAbsolutePath().toString())
            setProperty("backup", record.backup.toAbsolutePath().toString())
            setProperty("phase", record.phase.journalValue)
            setProperty("targetExisted", record.targetExisted.toString())
        }
        val temporary = Files.createTempFile(journal.parent, ".${journal.fileName}-", ".tmp")
        try {
            val content = java.io.StringWriter().also { properties.store(it, null) }.toString()
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val bytes = ByteBuffer.wrap(content.toByteArray(StandardCharsets.UTF_8))
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            moveJournal(temporary, journal)
            directorySync(journal.parent)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun forceDirectory(path: Path) {
        try {
            FileChannel.open(path, StandardOpenOption.READ).use { it.force(true) }
        } catch (_: UnsupportedOperationException) {
            // Directory fsync is not supported by every file-system provider.
        }
    }

    private fun moveJournal(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun deleteDirectory(path: Path) {
        if (Files.exists(path)) {
            check(path.toFile().deleteRecursively()) { "Unable to delete directory: $path" }
        }
    }

    private fun journalPath(target: Path, kind: JournaledReplaceKind): Path {
        val fileName = when (kind) {
            JournaledReplaceKind.FILE -> ".${target.fileName}.replace-journal"
            JournaledReplaceKind.DIRECTORY -> ".${target.fileName}.publish-journal"
        }
        return target.toAbsolutePath().resolveSibling(fileName)
    }
}
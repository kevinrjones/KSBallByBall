package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.parse.database.adapter.JournaledMatchReplace
import com.knowledgespike.ballbyball.parse.database.adapter.JournaledReplaceKind
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun deleteDirectory(path: Path) {
    if (Files.exists(path)) {
        check(path.toFile().deleteRecursively()) { "Unable to delete directory: $path" }
    }
}

internal fun deleteDirectoryContents(path: Path) {
    if (!Files.exists(path)) return
    Files.list(path).use { stream ->
        stream.forEach { entry ->
            if (Files.isDirectory(entry)) {
                entry.toFile().deleteRecursively()
            } else {
                Files.deleteIfExists(entry)
            }
        }
    }
}

internal fun writeCsvRow(appendable: Appendable, values: List<Any?>) {
    for (i in values.indices) {
        if (i > 0) appendable.append(',')
        writeCsvValue(appendable, values[i])
    }
    appendable.append('\n')
}

internal fun writeCsvValue(appendable: Appendable, value: Any?) {
    when (value) {
        null -> appendable.append("\\N")
        is Boolean -> appendable.append(if (value) '1' else '0')
        is Number -> appendable.append(value.toString())
        is LocalDateTime -> appendable.append(value.format(DATETIME_FORMATTER))
        is String -> escapeCsv(appendable, value)
        else -> escapeCsv(appendable, value.toString())
    }
}

internal fun escapeCsv(appendable: Appendable, value: String) {
    var hasSpecial = false
    for (i in 0 until value.length) {
        val c = value[i]
        if (c == ',' || c == '"' || c == '\n' || c == '\r') {
            hasSpecial = true
            break
        }
    }
    if (!hasSpecial) {
        appendable.append(value)
        return
    }
    appendable.append('"')
    for (i in 0 until value.length) {
        val c = value[i]
        if (c == '"') {
            appendable.append("\"\"")
        } else {
            appendable.append(c)
        }
    }
    appendable.append('"')
}

internal class CsvBundlePublisher {
    companion object {
        fun recoverPublication(target: Path): Boolean =
            JournaledMatchReplace.recover(target, JournaledReplaceKind.DIRECTORY)
    }
}

package com.knowledgespike.ballbyball.parsecricsheet

import arrow.core.Either
import com.knowledgespike.ballbyball.clishared.identity.SingleSourceCanonicalizer
import com.knowledgespike.ballbyball.clishared.identity.SourceMatchEnvelope
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

data class ParseFailure(val input: Path, val message: String)

data class ParseResult(val failures: List<ParseFailure>) {
    val succeeded: Boolean get() = failures.isEmpty()
}

class CricSheetParser(
    private val sourceJson: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val outputJson: Json = Json { encodeDefaults = false },
    private val converter: CricSheetConverter = CricSheetConverter(sourceJson),
    private val matchLinker: MatchLinker
) {
    private val log = LoggerFactory.getLogger(CricSheetParser::class.java)

    fun parse(input: ParseCricsheetInput): ParseResult {
        val layout = input.layout
        val normalizedInputDirectory = layout.input
        val normalizedOutputDirectory = layout.output
        val normalizedNotParsedDirectory = layout.notParsed
        log.info(
            "Starting Cricsheet parsing from {} into parsed {} and not-parsed {}",
            normalizedInputDirectory,
            normalizedOutputDirectory,
            normalizedNotParsedDirectory
        )
        require(Files.isDirectory(normalizedInputDirectory)) {
            "Input directory does not exist: $normalizedInputDirectory"
        }
        Files.createDirectories(normalizedOutputDirectory)
        Files.createDirectories(normalizedNotParsedDirectory)

        val inputs = jsonFiles(normalizedInputDirectory)
        log.info("Found {} Cricsheet JSON files in {}", inputs.size, normalizedInputDirectory)
        if (inputs.isEmpty()) {
            log.warn("No Cricsheet JSON files found in {}", normalizedInputDirectory)
            return ParseResult(listOf(ParseFailure(normalizedInputDirectory, "No Cricsheet JSON files found")))
        }

        val failures = inputs.mapNotNull { input -> processFile(input, layout) }
        if (failures.isNotEmpty()) {
            log.error(
                "Cricsheet parsing completed with {} errors:\n{}",
                failures.size,
                failures.joinToString(separator = "\n") { "${it.input}: ${it.message}" }
            )
        }
        log.info(
            "Completed Cricsheet parsing of {} files with {} failures",
            inputs.size,
            failures.size
        )
        return ParseResult(failures)
    }

    private fun processFile(input: Path, layout: ParseDirectoryLayout): ParseFailure? {
        val output = layout.output.resolve(layout.input.relativize(input))
        return try {
            log.debug("Parsing {} into {}", input, output)
            val sourceEnvelope = converter.convert(
                rawBytes = Files.readAllBytes(input),
                providerRecordKey = input.nameWithoutExtension
            )
            val linkedEnvelope = when (val linkResult = linkMatch(sourceEnvelope, input)) {
                is Either.Left -> return preserveFailedFile(input, output, layout, linkResult.value.message)
                is Either.Right -> linkResult.value
            }
            val canonicalEnvelope = SingleSourceCanonicalizer.canonicalize(linkedEnvelope)
            writeAtomically(output, outputJson.encodeToString(canonicalEnvelope))
            removeStaleFile(notParsedOutput(input, layout.input, layout.notParsed))
            log.debug("Wrote normalized match data to {}", output)
            null
        } catch (exception: Exception) {
            preserveFailedFile(
                input,
                output,
                layout,
                exception.message ?: exception::class.simpleName.orEmpty(),
                exception
            )
        }
    }

    private fun preserveFailedFile(
        input: Path,
        output: Path,
        layout: ParseDirectoryLayout,
        failureMessage: String,
        exception: Exception? = null
    ): ParseFailure {
        val failure = ParseFailure(input, failureMessage)
        if (exception == null) {
            log.error("Unable to parse {}: {}", input, failure.message)
        } else {
            log.error("Unable to parse {}: {}", input, failure.message, exception)
        }
        val cleanupFailure = runCatching { removeStaleFile(output) }.exceptionOrNull()
        cleanupFailure?.let { log.error("Unable to remove stale parsed output {}", output, it) }
        val preservedMessage = preserveRawFile(input, layout.input, layout.notParsed, failure.message)
        return failure.copy(
            message = cleanupFailure?.let { "$preservedMessage; unable to remove parsed output: ${it.message}" }
                ?: preservedMessage
        )
    }

    private fun linkMatch(
        sourceEnvelope: SourceMatchEnvelope,
        input: Path
    ): Either<ParseFailure, SourceMatchEnvelope> = when (val result = matchLinker.link(sourceEnvelope.match.match)) {
        is StatsLinkResult.Linked -> Either.Right(sourceEnvelope.withSourceCaId(result.sourceCaId.value))
        is StatsLinkResult.NotFound -> Either.Left(
            ParseFailure(input, "No stats match found for ${result.criteria}")
        )

        is StatsLinkResult.Ambiguous -> Either.Left(
            ParseFailure(
                input,
                "Multiple stats matches found for ${result.criteria}: ${result.caIds.map { it.value }}"
            )
        )

        is StatsLinkResult.InvalidAlias -> Either.Left(ParseFailure(input, result.message))
        StatsLinkResult.Skipped -> Either.Right(sourceEnvelope.withSourceCaId(null))
    }

    private fun SourceMatchEnvelope.withSourceCaId(sourceCaId: String?): SourceMatchEnvelope = copy(
        match = match.copy(match = match.match.copy(sourceCaId = sourceCaId))
    )

    private fun preserveRawFile(
        input: Path,
        inputDirectory: Path,
        notParsedDirectory: Path,
        failureMessage: String
    ): String {
        val rawOutput = notParsedOutput(input, inputDirectory, notParsedDirectory)
        return try {
            Files.createDirectories(rawOutput.parent)
            Files.copy(input, rawOutput, REPLACE_EXISTING)
            failureMessage
        } catch (copyException: Exception) {
            log.error("Unable to preserve raw file {} at {}: {}", input, rawOutput, copyException.message, copyException)
            "$failureMessage; unable to preserve raw file: ${copyException.message ?: copyException::class.simpleName}"
        }
    }

    private fun notParsedOutput(input: Path, inputDirectory: Path, notParsedDirectory: Path): Path =
        notParsedDirectory.resolve(inputDirectory.relativize(input))

    private fun removeStaleFile(path: Path) {
        Files.deleteIfExists(path)
    }

    private fun jsonFiles(inputDirectory: Path): List<Path> = Files.walk(inputDirectory).use { files ->
        files.filter { Files.isRegularFile(it) && it.extension.equals("json", ignoreCase = true) }
            .sorted()
            .toList()
    }

    private fun writeAtomically(path: Path, content: String) {
        Files.createDirectories(path.parent)
        val temporary = Files.createTempFile(path.parent, ".${path.name}.", ".tmp")
        try {
            Files.writeString(temporary, content)
            try {
                Files.move(temporary, path, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

}
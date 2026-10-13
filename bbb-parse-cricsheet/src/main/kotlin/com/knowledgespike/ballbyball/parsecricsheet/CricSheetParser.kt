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
        log.info(
            "Starting Cricsheet parsing from {} into parsed {}",
            normalizedInputDirectory,
            normalizedOutputDirectory
        )
        require(Files.isDirectory(normalizedInputDirectory)) {
            "Input directory does not exist: $normalizedInputDirectory"
        }
        Files.createDirectories(normalizedOutputDirectory)

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
        log.debug("Parsing {} into {}", input, output)
        val sourceEnvelope = try {
            converter.convert(
                rawBytes = Files.readAllBytes(input),
                providerRecordKey = input.nameWithoutExtension
            )
        } catch (exception: Exception) {
            return failureWithoutOutput(input, output, exception)
        }

        val linkResult = try {
            linkMatch(sourceEnvelope, input)
        } catch (exception: Exception) {
            log.error("Unable to link stats match for {}: {}", input, exception.message, exception)
            Either.Left(ParseFailure(input, exception.message ?: exception::class.simpleName.orEmpty()))
        }
        val linkFailure: ParseFailure?
        val envelopeToWrite: SourceMatchEnvelope
        when (linkResult) {
            is Either.Left -> {
                linkFailure = linkResult.value
                log.error("Unable to link stats match for {}: {}", input, linkFailure.message)
                envelopeToWrite = sourceEnvelope.withSourceCaId(null)
            }

            is Either.Right -> {
                linkFailure = null
                envelopeToWrite = linkResult.value
            }
        }

        return try {
            val canonicalEnvelope = SingleSourceCanonicalizer.canonicalize(envelopeToWrite)
            writeAtomically(output, outputJson.encodeToString(canonicalEnvelope))
            log.debug("Wrote normalized match data to {}", output)
            linkFailure
        } catch (exception: Exception) {
            val failure = failureWithoutOutput(input, output, exception)
            linkFailure?.let { failure.copy(message = "${it.message}; ${failure.message}") } ?: failure
        }
    }

    private fun failureWithoutOutput(
        input: Path,
        output: Path,
        exception: Exception
    ): ParseFailure {
        val failure = ParseFailure(input, exception.message ?: exception::class.simpleName.orEmpty())
        log.error("Unable to parse {}: {}", input, failure.message, exception)
        val cleanupFailure = runCatching { removeStaleFile(output) }.exceptionOrNull()
        cleanupFailure?.let { log.error("Unable to remove stale parsed output {}", output, it) }
        return failure.copy(
            message = cleanupFailure?.let { "${failure.message}; unable to remove parsed output: ${it.message}" }
                ?: failure.message
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
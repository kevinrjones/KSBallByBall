package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.cricketarchive.shared.DatabaseConnection
import io.github.cdimascio.dotenv.dotenv
import org.apache.commons.cli.ParseException
import org.apache.commons.cli.help.HelpFormatter
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.sql.Connection
import kotlin.system.exitProcess

object Application {
    private const val APPLICATION_NAME = "bbb-parse-cricsheet"
    private val log = LoggerFactory.getLogger(Application::class.java)
    private val productionConnectionFactory = ApplicationConnectionFactory { connectionString, userName, password, readOnly ->
        DatabaseConnectionHandle(
            DatabaseConnection(connectionString, userName, password, readOnly).connect
        )
    }
    private val productionParserFactory = ApplicationParserFactory { statsConnection, bbbConnection ->
        val matchLinker = StatsMatchLinker.fromConnections(statsConnection, bbbConnection)
        val playerLinker = StatsPlayerLinker.fromConnections(statsConnection, bbbConnection)
        val parser = CricSheetParser(
            matchLinker = matchLinker,
            playerLinker = playerLinker
        )
        ApplicationParser { input ->
            parser.parse(input)
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        loadEnvironmentVariables()
        val exitCode = execute(args)
        if (exitCode != 0) exitProcess(exitCode)
    }

    internal fun execute(
        args: Array<String>,
        connectionFactory: ApplicationConnectionFactory = productionConnectionFactory,
        parserFactory: ApplicationParserFactory = productionParserFactory
    ): Int = try {
        if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
            printHelp()
            0
        } else {
            val input = ParseCricsheetArguments.parse(args)
            log.info(
                "Parsing Cricsheet JSON from {} into parsed {}",
                input.inputDirectory,
                input.outputDirectory
            )
            val result = connectionFactory.open(
                input.statsConnectionString,
                input.statsUserName,
                environmentPassword(input.statsPasswordEnvironmentVariable),
                readOnly = true
            ).use { stats ->
                connectionFactory.open(
                    input.bbbConnectionString,
                    input.bbbUserName,
                    environmentPassword(input.bbbPasswordEnvironmentVariable),
                    readOnly = false
                ).use { bbb ->
                    parserFactory.create(stats.connection, bbb.connection).parse(input)
                }
            }
            result.failures.forEach { failure ->
                log.error("Unable to parse {}: {}", failure.input, failure.message)
            }
            log.info("Finished {} with {} failures", APPLICATION_NAME, result.failures.size)
            if (result.succeeded) 0 else 1
        }
    } catch (exception: ParseException) {
        log.error("Unable to parse command-line arguments: {}", exception.message)
        printHelp()
        2
    } catch (exception: Exception) {
        log.error("Unable to start {}", APPLICATION_NAME, exception)
        1
    }

    private fun printHelp() {
        HelpFormatter.builder().get().printHelp(
            APPLICATION_NAME,
            "Normalize Cricsheet match data into the shared Ball By Ball schema",
            ParseCricsheetArguments.options(),
            "",
            true
        )
    }

    private fun environmentPassword(variable: String?): String? = variable?.let {
        System.getenv(it)
            ?: System.getProperty(it)
            ?: throw IllegalArgumentException("Password environment variable $it is not set")
    }
}

internal fun loadEnvironmentVariables(directory: String = environmentFileDirectory()) {
    val dotenv = dotenv {
        this.directory = directory
        ignoreIfMissing = true
        ignoreIfMalformed = true
    }

    dotenv.entries().forEach { entry ->
        val key = entry.key
        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, entry.value)
        }
    }
}

private fun environmentFileDirectory(): String = when {
    File("bbb-parse-cricsheet/.env").exists() -> "bbb-parse-cricsheet"
    else -> "./"
}

internal fun interface ApplicationConnectionFactory {
    fun open(
        connectionString: String,
        userName: String?,
        password: String?,
        readOnly: Boolean
    ): ApplicationConnection
}

internal interface ApplicationConnection : Closeable {
    val connection: Connection
}

private class DatabaseConnectionHandle(
    private val delegate: DatabaseConnection.DbConnection
) : ApplicationConnection {
    override val connection: Connection
        get() = delegate.connection

    override fun close() {
        delegate.close()
    }
}

internal fun interface ApplicationParserFactory {
    fun create(statsConnection: Connection, bbbConnection: Connection): ApplicationParser
}

internal fun interface ApplicationParser {
    fun parse(input: ParseCricsheetInput): ParseResult
}
package com.knowledgespike.ballbyball.parse

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.cricketarchive.shared.DatabaseConnection
import com.knowledgespike.ballbyball.parse.parser.BallByBallParser
import com.knowledgespike.ballbyball.parse.parser.PlayerRegistryParser
import com.knowledgespike.ballbyball.parse.database.Database
import com.knowledgespike.ballbyball.parse.database.PersonEntity
import com.knowledgespike.ballbyball.parse.database.adapter.OutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.csv.CsvOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.mariadb.SqlOutputAdapter as MariaDbSqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.mariadb.SqlScriptOutputAdapter as MariaDbSqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.postgres.SqlOutputAdapter as PostgresSqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.postgres.SqlScriptOutputAdapter as PostgresSqlScriptOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter as SqliteSqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlScriptOutputAdapter as SqliteSqlScriptOutputAdapter
import org.apache.commons.cli.*
import org.apache.commons.cli.help.HelpFormatter
import java.nio.file.Path
import kotlin.system.exitProcess

private sealed interface OutputTarget {
    data class DatabaseTarget(
        val connectionString: String,
        val userName: String?,
        val password: String?
    ) : OutputTarget

    data class ScriptTarget(val databaseType: String, val output: Path) : OutputTarget
    data class CsvTarget(val directory: Path, val force: Boolean) : OutputTarget
}

class Application {

    companion object {
        private val log by LoggerDelegate()

        @JvmStatic
        fun main(args: Array<String>) {
            execute(args).takeIf { it != 0 }?.let(::exitProcess)
        }

        internal fun execute(args: Array<String>): Int = try {
            val options = createCommandLineOptions()
            val formatter = HelpFormatter.builder().get()
            val header = "Parse cricsheet data into database"
            if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
                formatter.printHelp("java parsecard", header, options, "", true)
                return 0
            }

            val cmd = try {
                DefaultParser().parse(options, args)
            } catch (exception: Exception) {
                println(exception.message)
                formatter.printHelp("java parsecard", header, options, "", true)
                return 2
            }

            val input = UpdateDatabaseArguments.from(cmd)
            val outputTarget = resolveOutputTarget(cmd, input)
            val playerRegistry = try {
                PlayerRegistryParser().parse(input.playerRegistry.toFile()).map {
                    PersonEntity(it.id, it.name, it.caId.toIntOrNull() ?: 0)
                }.toList().associateBy { it.id }
            } catch (e: Exception) {
                log.warn("Could not load player registry from {}: {}", input.playerRegistry, e.message)
                emptyMap()
            }

            when (outputTarget) {
                is OutputTarget.ScriptTarget -> createScriptOutputAdapter(outputTarget.databaseType, outputTarget.output)
                    .use { adapter ->
                        runImport(adapter, input, playerRegistry)
                        adapter.complete()
                    }
                is OutputTarget.DatabaseTarget -> {
                    val dbConnection = DatabaseConnection(
                        outputTarget.connectionString,
                        outputTarget.userName,
                        outputTarget.password
                    )
                    dbConnection.connect.use { db ->
                        createSqlOutputAdapter(outputTarget.connectionString, db.connection)
                            .use { adapter ->
                                runImport(adapter, input, playerRegistry)
                                adapter.complete()
                            }
                    }
                }
                is OutputTarget.CsvTarget -> CsvOutputAdapter(outputTarget.directory, outputTarget.force)
                    .use { adapter ->
                        runImport(adapter, input, playerRegistry)
                        adapter.complete()
                    }
            }
            log.info("finished")
            0
        } catch (exception: Exception) {
            log.error("Unable to parse cricsheet data", exception)
            1
        }

        private fun resolveOutputTarget(cmd: CommandLine, input: UpdateDatabaseInput): OutputTarget {
            val outputType = cmd.getOptionValue("ot", "SQL").uppercase()
            val outputFile = cmd.getOptionValue("o") ?: cmd.getOptionValue("sf")
            val csvDirectory = cmd.getOptionValue("cd")
            val databaseType = cmd.getOptionValue("db", "mariadb").lowercase()
            val outputPath = outputFile?.let {
                UpdateDatabaseArguments.resolveOutputPath(input.baseDirectory, it, "--outputFile")
            }
            val csvPath = csvDirectory?.let {
                UpdateDatabaseArguments.resolveCsvOutputPath(input, it)
            }

            return when (outputType) {
                "SQL_FILE" -> {
                    require(!outputFile.isNullOrBlank()) {
                        "--outputFile is required when --outputType SQL_FILE is selected"
                    }
                    scriptTarget(databaseType, requireNotNull(outputPath))
                }

                "SQL", "DATABASE" -> if (outputType == "SQL" && !outputFile.isNullOrBlank()) {
                    scriptTarget(databaseType, requireNotNull(outputPath))
                } else {
                    val connectionString = cmd.getOptionValue("c")
                        ?: throw IllegalArgumentException("--connectionString is required for DATABASE output")
                    OutputTarget.DatabaseTarget(connectionString, cmd.getOptionValue("u"), cmd.getOptionValue("p"))
                }

                "CSV" -> {
                    require(!csvDirectory.isNullOrBlank()) {
                        "--csvDir is required when --outputType CSV is selected"
                    }
                    OutputTarget.CsvTarget(requireNotNull(csvPath), cmd.hasOption("force"))
                }

                else -> throw IllegalArgumentException("Unsupported output type: $outputType")
            }
        }

        private fun createScriptOutputAdapter(databaseType: String, output: Path): OutputAdapter = when (databaseType) {
            "mariadb" -> MariaDbSqlScriptOutputAdapter(output)
            "postgres", "postgresql" -> PostgresSqlScriptOutputAdapter(output)
            "sqlite" -> SqliteSqlScriptOutputAdapter(output)
            else -> throw IllegalArgumentException("Unsupported database type: $databaseType")
        }

        private fun scriptTarget(databaseType: String, output: Path): OutputTarget.ScriptTarget {
            require(databaseType != "mysql") {
                "MySQL is not supported for generated SQL scripts; use --database mariadb for MariaDB scripts"
            }
            require(databaseType in setOf("mariadb", "postgres", "postgresql", "sqlite")) {
                "Unsupported database type for generated SQL script: $databaseType"
            }
            return OutputTarget.ScriptTarget(databaseType, output)
        }

        private fun createSqlOutputAdapter(connectionString: String, connection: java.sql.Connection): OutputAdapter =
            when (databaseType(connectionString)) {
                "mariadb" -> MariaDbSqlOutputAdapter(connection)
                "postgres" -> PostgresSqlOutputAdapter(connection)
                "sqlite" -> SqliteSqlOutputAdapter(connection)
                else -> throw IllegalArgumentException("Unsupported database connection: $connectionString")
            }

        private fun databaseType(connectionString: String): String = when {
            connectionString.startsWith("jdbc:mariadb:") || connectionString.startsWith("jdbc:mysql:") -> "mariadb"
            connectionString.startsWith("jdbc:postgresql:") -> "postgres"
            connectionString.startsWith("jdbc:sqlite:") -> "sqlite"
            else -> "unknown"
        }

        private fun createCommandLineOptions(): Options {
            val options = Options()
            options.addOption("h", "help", false, "print this message")
            options.addOption("c", "connectionString", true, "database connection string")
            options.addOption("u", "userName", true, "database user name")
            options.addOption("p", "password", true, "database password")
            options.addOption(
                Option.builder("db").longOpt("database").hasArg().argName("database")
                    .desc("SQL-file database dialect: mariadb, postgres, or sqlite (default: mariadb)").get()
            )
            options.addOption(
                Option.builder("ot").longOpt("outputType").hasArg().argName("outputType")
                    .desc("output destination: SQL, DATABASE, SQL_FILE, or CSV (default: SQL)").get()
            )
            options.addOption(
                Option.builder("o").longOpt("outputFile").hasArg().argName("outputFile")
                    .desc("SQL script output file; with SQL this selects file output").get()
            )
            options.addOption("sf", "sqlFile", true, "alias for outputFile")
            options.addOption("cd", "csvDir", true, "CSV output directory; required for CSV output")
            options.addOption("f", "force", false, "replace existing contents in the CSV output directory")
            UpdateDatabaseArguments.options().options.forEach(options::addOption)
            return options
        }

        private fun runImport(
            adapter: OutputAdapter,
            input: UpdateDatabaseInput,
            playerRegistry: Map<String, PersonEntity>
        ) {
            val database = Database(adapter, playerRegistry)
            val ballByBallParser = BallByBallParser()
            importMatches(database, ballByBallParser, input.dataDirectory)
        }

        private fun importMatches(
            database: Database,
            parser: BallByBallParser,
            dataDirectory: Path
        ) {
            log.info("Inserting matches from {}", dataDirectory)
            val files = matchFiles(dataDirectory)
            require(files.isNotEmpty()) { "No canonical match JSON files found in $dataDirectory" }
            files.forEach { file ->
                val sourceFile = file.toAbsolutePath().normalize()
                val envelope = parser.parse(file.toFile())
                log.debug(
                    "Parsing match: {}, {}, {}",
                    file.fileName,
                    envelope.match.match.event?.name ?: "Unknown",
                    envelope.match.match.matchType
                )
                database.writeMatch(sourceFile.toString(), envelope)
            }
        }

    }
}
package com.knowledgespike.ballbyball.bulkupdate

import com.knowledgespike.cricketarchive.LoggerDelegate
import org.apache.commons.cli.*
import kotlin.system.exitProcess

class Application {

    companion object {
        private val log by LoggerDelegate()

        @JvmStatic
        fun main(args: Array<String>) {
            execute(args).takeIf { it != 0 }?.let(::exitProcess)
        }

        internal fun execute(args: Array<String>): Int = try {
            val options = createCommandLineOptions()
            val formatter = org.apache.commons.cli.help.HelpFormatter.builder().get()
            val header = "Bulk update database from CSV files"

            if (args.isEmpty() || args.any { it == "-h" || it == "--help" }) {
                formatter.printHelp("java bulkupdate", header, options, "", true)
                return 0
            }

            val cmd = try {
                DefaultParser().parse(options, args)
            } catch (exception: Exception) {
                println(exception.message)
                formatter.printHelp("java bulkupdate", header, options, "", true)
                return 2
            }

            val connectionString = cmd.getOptionValue("c") ?: ""
            val userName = cmd.getOptionValue("u") ?: ""
            val password = cmd.getOptionValue("p") ?: ""
            val baseDirectory = cmd.getOptionValue("bd") ?: ""
            val csvDir = cmd.getOptionValue("cd") ?: ""
            val dbTypeStr = cmd.getOptionValue("dt", "mysql")

            if (connectionString.isBlank() || baseDirectory.isBlank() || csvDir.isBlank()) {
                println("Missing required arguments")
                formatter.printHelp("java bulkupdate", header, options, "", true)
                return 2
            }

            val dbType = DbType.fromString(dbTypeStr)

            val config = AppConfig(
                connectionString = connectionString,
                userName = userName,
                password = password,
                baseDirectory = baseDirectory,
                dbType = dbType,
                csvDir = csvDir
            )

            log.info("Starting bulk update with config: dbType=$dbType, baseDirectory=$baseDirectory, csvDir=$csvDir")

            val service = BulkUpdateService(config)
            val result = service.execute()

            if (result.isSuccess) {
                log.info("Bulk update completed successfully")
                0
            } else {
                log.error("Bulk update failed: ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
                1
            }
        } catch (exception: Exception) {
            log.error("Unexpected error during bulk update", exception)
            1
        }

        private fun createCommandLineOptions(): Options = Options().apply {
            addOption(
                Option.builder("c")
                    .longOpt("connectionString")
                    .hasArg()
                    .desc("JDBC connection string")
                    .required(true)
                    .get()
            )
            addOption(
                Option.builder("u")
                    .longOpt("userName")
                    .hasArg()
                    .desc("Database user name")
                    .get()
            )
            addOption(
                Option.builder("p")
                    .longOpt("password")
                    .hasArg()
                    .desc("Database password")
                    .get()
            )
            addOption(
                Option.builder("bd")
                    .longOpt("baseDirectory")
                    .hasArg()
                    .desc("Base directory for data files")
                    .required(true)
                    .get()
            )
            addOption(
                Option.builder("cd")
                    .longOpt("csvDir")
                    .hasArg()
                    .desc("Directory containing CSV files")
                    .required(true)
                    .get()
            )
            addOption(
                Option.builder("dt")
                    .longOpt("dbType")
                    .hasArg()
                    .desc("Database type: MySQL, MariaDB, PostgreSQL, SQLite (default: MySQL)")
                    .get()
            )
            addOption(
                Option.builder("h")
                    .longOpt("help")
                    .desc("Print help message")
                    .get()
            )
        }
    }
}

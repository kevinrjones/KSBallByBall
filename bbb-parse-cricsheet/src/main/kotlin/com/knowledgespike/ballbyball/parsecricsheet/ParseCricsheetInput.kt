package com.knowledgespike.ballbyball.parsecricsheet

import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.nio.file.Path

data class ParseCricsheetInput(
    val baseDirectory: Path,
    val inputDirectory: Path,
    val outputDirectory: Path,
    val statsConnectionString: String,
    val statsUserName: String? = null,
    val statsPasswordEnvironmentVariable: String? = null,
    val bbbConnectionString: String,
    val bbbUserName: String? = null,
    val bbbPasswordEnvironmentVariable: String? = null
) {
    internal val layout: ParseDirectoryLayout =
        parseDirectoryLayout(inputDirectory, outputDirectory)

    internal companion object {
        fun fromDirectories(inputDirectory: Path, outputDirectory: Path): ParseCricsheetInput {
            val baseDirectory = inputDirectory.toAbsolutePath().normalize().parent
                ?: inputDirectory.toAbsolutePath().normalize()
            return ParseCricsheetInput(
                baseDirectory = baseDirectory,
                inputDirectory = inputDirectory,
                outputDirectory = outputDirectory,
                statsConnectionString = "",
                bbbConnectionString = ""
            )
        }
    }
}

object ParseCricsheetArguments {
    fun options(): Options = Options().apply {
        addOption("h", "help", false, "print this message")
        addOption(
            Option.builder("bd")
                .longOpt("base-directory")
                .hasArg()
                .argName("directory")
                .desc("absolute root directory")
                .get()
        )
        addOption(
            Option.builder("i")
                .longOpt("input")
                .hasArg()
                .argName("directory")
                .desc("relative Cricsheet input directory")
                .get()
        )
        addOption(
            Option.builder("o")
                .longOpt("output")
                .hasArg()
                .argName("directory")
                .desc("relative normalized output directory")
                .get()
        )
        addOption(
            Option.builder("scs")
                .longOpt("stats-connection-string")
                .hasArg()
                .argName("jdbc-url")
                .desc("JDBC connection string for the CricketArchive stats database")
                .get()
        )
        addOption(
            Option.builder("su")
                .longOpt("stats-user-name")
                .hasArg()
                .argName("username")
                .desc("CricketArchive stats database username")
                .get()
        )
        addOption(
            Option.builder("sp")
                .longOpt("stats-password-environment-variable")
                .hasArg()
                .argName("environment-variable")
                .desc("environment variable containing the CricketArchive stats database password")
                .get()
        )
        addOption(
            Option.builder("bcs")
                .longOpt("bbb-connection-string")
                .hasArg()
                .argName("jdbc-url")
                .desc("JDBC connection string for the ball-by-ball alias database")
                .get()
        )
        addOption(
            Option.builder("bu")
                .longOpt("bbb-user-name")
                .hasArg()
                .argName("username")
                .desc("ball-by-ball alias database username")
                .get()
        )
        addOption(
            Option.builder("bp")
                .longOpt("bbb-password-environment-variable")
                .hasArg()
                .argName("environment-variable")
                .desc("environment variable containing the ball-by-ball alias database password")
                .get()
        )
    }

    fun parse(args: Array<String>): ParseCricsheetInput {
        val commandLine = DefaultParser().parse(options(), args)
        val baseDirectory = absoluteBaseDirectory(commandLine.requiredValue("bd", "base-directory"))
        val inputDirectory = childDirectory(
            baseDirectory,
            commandLine.requiredValue("i", "input"),
            "--input"
        )
        val outputDirectory = childDirectory(
            baseDirectory,
            commandLine.requiredValue("o", "output"),
            "--output"
        )
        return try {
            ParseCricsheetInput(
                baseDirectory = baseDirectory,
                inputDirectory = inputDirectory,
                outputDirectory = outputDirectory,
                statsConnectionString = commandLine.requiredValue("scs", "stats-connection-string"),
                statsUserName = commandLine.getOptionValue("stats-user-name"),
                statsPasswordEnvironmentVariable = commandLine.getOptionValue("stats-password-environment-variable"),
                bbbConnectionString = commandLine.requiredValue("bcs", "bbb-connection-string"),
                bbbUserName = commandLine.getOptionValue("bbb-user-name"),
                bbbPasswordEnvironmentVariable = commandLine.getOptionValue("bbb-password-environment-variable")
            )
        } catch (exception: IllegalArgumentException) {
            throw ParseException(exception.message)
        }
    }

    private fun absoluteBaseDirectory(value: String): Path {
        val path = pathValue(value, "--base-directory")
        if (!path.isAbsolute) {
            throw ParseException("--base-directory must be an absolute path")
        }
        return path.normalize()
    }

    private fun childDirectory(baseDirectory: Path, value: String, option: String): Path {
        val path = pathValue(value, option)
        if (path.isAbsolute) {
            throw ParseException("$option must be relative to --base-directory")
        }
        val resolved = baseDirectory.resolve(path).normalize()
        val physicalBase = try {
            resolvePhysicalPath(baseDirectory)
        } catch (exception: IllegalArgumentException) {
            throw ParseException(exception.message)
        }
        val physicalResolved = try {
            resolvePhysicalPath(resolved)
        } catch (exception: IllegalArgumentException) {
            throw ParseException(exception.message)
        }
        if (!physicalResolved.startsWith(physicalBase)) {
            throw ParseException("$option must resolve within --base-directory")
        }
        return resolved
    }


    private fun pathValue(value: String?, option: String): Path {
        if (value.isNullOrBlank()) {
            throw ParseException("$option must not be blank")
        }
        return try {
            Path.of(value)
        } catch (exception: RuntimeException) {
            throw ParseException("$option is not a valid path: ${exception.message}")
        }
    }

    private fun CommandLine.requiredValue(shortName: String, longName: String): String =
        getOptionValue(shortName) ?: getOptionValue(longName)
        ?: throw ParseException("--$longName is required")
}
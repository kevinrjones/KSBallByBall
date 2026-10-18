package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ApplicationTest {
    @TempDir
    lateinit var baseDirectory: Path

    @Test
    fun `given valid settings when application runs then stats connection is read only, bbb connection is writable, and both are closed`() {
        val openedConnections = mutableListOf<OpenedConnection>()
        val connectionFactory = ApplicationConnectionFactory { connectionString, userName, password, readOnly ->
            val opened = OpenedConnection(
                connectionString = connectionString,
                userName = userName,
                password = password,
                readOnly = readOnly,
                handle = RecordingConnection(connection(connectionString))
            )
            openedConnections += opened
            opened.handle
        }
        var linkedStatsConnection: Connection? = null
        var linkedBbbConnection: Connection? = null
        val parserFactory = ApplicationParserFactory { statsConnection, bbbConnection ->
            linkedStatsConnection = statsConnection
            linkedBbbConnection = bbbConnection
            ApplicationParser { ParseResult(emptyList()) }
        }

        val exitCode = Application.execute(validArguments(), connectionFactory, parserFactory)

        expectThat(exitCode).isEqualTo(0)
        expectThat(openedConnections.map { it.connectionString }).isEqualTo(listOf("jdbc:stats", "jdbc:bbb"))
        expectThat(openedConnections.map { it.readOnly }).isEqualTo(listOf(true, false))
        assertSame(openedConnections[0].handle.connection, linkedStatsConnection)
        assertSame(openedConnections[1].handle.connection, linkedBbbConnection)
        assertTrue(openedConnections.all { it.handle.closed })
    }

    @Test
    fun `given password system properties when application runs then passwords are passed to connections`() {
        val statsPasswordVariable = "BBB_PARSE_STATS_PASSWORD_${System.nanoTime()}"
        val bbbPasswordVariable = "BBB_PARSE_BBB_PASSWORD_${System.nanoTime()}"
        val previousStatsPassword = System.getProperty(statsPasswordVariable)
        val previousBbbPassword = System.getProperty(bbbPasswordVariable)

        try {
            System.setProperty(statsPasswordVariable, "stats-password")
            System.setProperty(bbbPasswordVariable, "bbb-password")
            val passwords = mutableListOf<String?>()
            val connectionFactory = ApplicationConnectionFactory { connectionString, _, password, _ ->
                passwords += password
                RecordingConnection(connection(connectionString))
            }
            val parserFactory = ApplicationParserFactory { _, _ ->
                ApplicationParser { ParseResult(emptyList()) }
            }
            val args = validArguments() + arrayOf(
                "--stats-password-environment-variable", statsPasswordVariable,
                "--bbb-password-environment-variable", bbbPasswordVariable
            )

            val exitCode = Application.execute(args, connectionFactory, parserFactory)

            expectThat(exitCode).isEqualTo(0)
            expectThat(passwords.toList()).isEqualTo(listOf<String?>("stats-password", "bbb-password"))
        } finally {
            restoreSystemProperty(statsPasswordVariable, previousStatsPassword)
            restoreSystemProperty(bbbPasswordVariable, previousBbbPassword)
        }
    }

    @Test
    fun `given a missing named password when application runs then connections are not opened`() {
        val missingVariable = "BBB_PARSE_MISSING_PASSWORD_${System.nanoTime()}"
        assertNull(System.getenv(missingVariable))
        assertNull(System.getProperty(missingVariable))
        var factoryCalled = false
        val connectionFactory = ApplicationConnectionFactory { _, _, _, _ ->
            factoryCalled = true
            error("connection factory should not be called")
        }

        val exitCode = Application.execute(
            validArguments() + arrayOf("--stats-password-environment-variable", missingVariable),
            connectionFactory,
            ApplicationParserFactory { _, _ -> error("parser factory should not be called") }
        )

        expectThat(exitCode).isEqualTo(1)
        assertFalse(factoryCalled)
    }

    @Test
    fun `given dotenv values when environment is loaded then missing properties are added without replacing existing ones`() {
        val loadedVariable = "BBB_PARSE_DOTENV_${System.nanoTime()}"
        val existingVariable = "BBB_PARSE_EXISTING_${System.nanoTime()}"
        val previousLoadedValue = System.getProperty(loadedVariable)
        val previousExistingValue = System.getProperty(existingVariable)
        Files.writeString(
            baseDirectory.resolve(".env"),
            "$loadedVariable=loaded-value\n$existingVariable=dotenv-value\n"
        )

        try {
            System.setProperty(existingVariable, "existing-value")

            loadEnvironmentVariables(baseDirectory.toString())

            expectThat(System.getProperty(loadedVariable)).isEqualTo("loaded-value")
            expectThat(System.getProperty(existingVariable)).isEqualTo("existing-value")
        } finally {
            restoreSystemProperty(loadedVariable, previousLoadedValue)
            restoreSystemProperty(existingVariable, previousExistingValue)
        }
    }

    @Test
    fun `given parser failure when application runs then both connections are closed`() {
        val openedConnections = mutableListOf<RecordingConnection>()
        val connectionFactory = ApplicationConnectionFactory { connectionString, _, _, _ ->
            RecordingConnection(connection(connectionString)).also { openedConnections += it }
        }
        val parserFactory = ApplicationParserFactory { _, _ ->
            ApplicationParser { throw IllegalStateException("parser failed") }
        }

        val exitCode = Application.execute(validArguments(), connectionFactory, parserFactory)

        expectThat(exitCode).isEqualTo(1)
        expectThat(openedConnections.size).isEqualTo(2)
        assertTrue(openedConnections.all { it.closed })
    }

    @Test
    fun `given missing arguments when application runs then factories are not opened`() {
        var factoryCalled = false
        val connectionFactory = ApplicationConnectionFactory { _, _, _, _ ->
            factoryCalled = true
            error("connection factory should not be called")
        }

        val exitCode = Application.execute(
            arrayOf("--base-directory", baseDirectory.toString()),
            connectionFactory,
            ApplicationParserFactory { _, _ -> error("parser factory should not be called") }
        )

        expectThat(exitCode).isEqualTo(2)
        assertFalse(factoryCalled)
    }

    private fun validArguments(): Array<String> = arrayOf(
        "--base-directory", baseDirectory.toString(),
        "--input", "raw",
        "--output", "parsed",
        "--stats-connection-string", "jdbc:stats",
        "--bbb-connection-string", "jdbc:bbb"
    )

    private fun restoreSystemProperty(key: String, value: String?) {
        if (value == null) {
            System.clearProperty(key)
        } else {
            System.setProperty(key, value)
        }
    }

    private fun connection(label: String): Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java)
    ) { _, method, _ ->
        when (method.returnType) {
            Boolean::class.javaPrimitiveType -> false
            Byte::class.javaPrimitiveType -> 0.toByte()
            Short::class.javaPrimitiveType -> 0.toShort()
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0.0f
            Double::class.javaPrimitiveType -> 0.0
            Char::class.javaPrimitiveType -> '\u0000'
            String::class.java -> label
            else -> null
        }
    } as Connection

    private data class OpenedConnection(
        val connectionString: String,
        val userName: String?,
        val password: String?,
        val readOnly: Boolean,
        val handle: RecordingConnection
    )

    private class RecordingConnection(
        override val connection: Connection
    ) : ApplicationConnection {
        var closed = false

        override fun close() {
            closed = true
        }
    }
}
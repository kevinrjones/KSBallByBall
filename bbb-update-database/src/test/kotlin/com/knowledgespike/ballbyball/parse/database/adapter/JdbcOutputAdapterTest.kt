package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.parse.database.adapter.mariadb.SqlOutputAdapter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException

class JdbcOutputAdapterTest {
    @Test
    fun `given durable JDBC commit fails then rollback precedes autocommit restoration`() {
        val events = mutableListOf<String>()
        var autoCommit = true
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "getAutoCommit" -> {
                    events += "getAutoCommit"
                    autoCommit
                }

                "setAutoCommit" -> {
                    autoCommit = arguments?.first() as Boolean
                    events += "setAutoCommit:$autoCommit"
                    null
                }

                "commit" -> {
                    events += "commit"
                    throw SQLException("durable commit failed")
                }

                "rollback" -> {
                    events += "rollback"
                    null
                }

                else -> error("Unexpected JDBC call: ${method.name}")
            }
        } as Connection
        val adapter = SqlOutputAdapter(connection)

        adapter.beginMatch()

        expectThat(assertThrows<SQLException> { adapter.commit() }.message)
            .isEqualTo("durable commit failed")
        expectThat(events.toList()).isEqualTo(
            listOf(
                "getAutoCommit",
                "setAutoCommit:false",
                "getAutoCommit",
                "commit",
                "rollback",
                "setAutoCommit:true"
            )
        )

        adapter.rollback()

        expectThat(events.toList()).isEqualTo(
            listOf(
                "getAutoCommit",
                "setAutoCommit:false",
                "getAutoCommit",
                "commit",
                "rollback",
                "setAutoCommit:true"
            )
        )
    }
}
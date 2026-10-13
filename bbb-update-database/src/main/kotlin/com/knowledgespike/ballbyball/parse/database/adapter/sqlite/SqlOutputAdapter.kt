package com.knowledgespike.ballbyball.parse.database.adapter.sqlite

import com.knowledgespike.ballbyball.parse.database.adapter.JdbcOutputAdapter
import java.sql.Connection

class SqlOutputAdapter(connection: Connection) : JdbcOutputAdapter(connection) {
    init {
        connection.createStatement().use { statement ->
            statement.execute("pragma foreign_keys = on")
        }
    }

}
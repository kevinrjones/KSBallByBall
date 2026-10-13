package com.knowledgespike.ballbyball.parse.database.adapter.mariadb

import com.knowledgespike.ballbyball.parse.database.adapter.JdbcOutputAdapter
import java.sql.Connection

class SqlOutputAdapter(connection: Connection) : JdbcOutputAdapter(connection)
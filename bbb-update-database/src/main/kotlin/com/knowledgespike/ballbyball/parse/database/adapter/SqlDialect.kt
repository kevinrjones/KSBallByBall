package com.knowledgespike.ballbyball.parse.database.adapter

enum class SqlDialect(
    val booleanLiteral: (Boolean) -> String,
    val transactionStart: String
) {
    MARIADB(
        booleanLiteral = { if (it) "1" else "0" },
        transactionStart = "START TRANSACTION;"
    ),
    POSTGRES(
        booleanLiteral = { if (it) "TRUE" else "FALSE" },
        transactionStart = "START TRANSACTION;"
    ),
    SQLITE(
        booleanLiteral = { if (it) "1" else "0" },
        transactionStart = "BEGIN TRANSACTION;"
    )
}
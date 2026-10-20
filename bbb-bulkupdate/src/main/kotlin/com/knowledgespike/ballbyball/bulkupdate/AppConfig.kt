package com.knowledgespike.ballbyball.bulkupdate

enum class DbType {
    SQLite, PostgreSQL, MySQL, MariaDB;

    companion object {
        fun fromString(value: String?): DbType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: MySQL
    }
}

data class AppConfig(
    val connectionString: String,
    val userName: String = "",
    val password: String = "",
    val baseDirectory: String,
    val dbType: DbType = DbType.MySQL,
    val csvDir: String
)

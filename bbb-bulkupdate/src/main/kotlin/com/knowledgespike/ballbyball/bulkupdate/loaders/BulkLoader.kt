package com.knowledgespike.ballbyball.bulkupdate.loaders

import java.nio.file.Path
import java.sql.Connection

interface BulkLoader {
    fun load(connection: Connection, file: Path, tableName: String): Result<Unit>
}

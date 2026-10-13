package com.knowledgespike.ballbyball.parse.database.adapter.relational

internal enum class RelationalRowOperation {
    INSERT,
    UPDATE
}

internal data class RelationalRow(
    val tableName: String,
    val columns: List<String>,
    val values: List<Any?>,
    val generatedColumns: Set<String> = emptySet(),
    val operation: RelationalRowOperation = RelationalRowOperation.INSERT,
    val whereColumns: List<String> = emptyList(),
    val whereValues: List<Any?> = emptyList()
) {
    init {
        require(columns.size == values.size) { "Relational row columns and values must have the same size" }
        require(generatedColumns.all(columns::contains)) { "Generated columns must exist in the relational row" }
        require(whereColumns.size == whereValues.size) { "Relational row conditions must have matching columns and values" }
        require(operation == RelationalRowOperation.UPDATE || whereColumns.isEmpty()) {
            "Only update rows can define conditions"
        }
        require(operation != RelationalRowOperation.UPDATE || whereColumns.isNotEmpty()) {
            "Update rows must define at least one condition"
        }
    }
}

internal fun interface RelationalSink {
    fun emit(row: RelationalRow): Long?
}
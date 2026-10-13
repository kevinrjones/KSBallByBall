package com.knowledgespike.ballbyball.parse.database.adapter

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class RelationalWriteSupportTest {
    @Test
    fun `given a match person row when building insert SQL then include its allocated identity column`() {
        val sql = RelationalWriteSupport.insertSql(
            tableName = "match_people",
            columns = RelationalWriteSupport.MATCH_PERSON_COLUMNS,
            keyword = "insert into"
        )

        expectThat(sql).isEqualTo(
            "insert into match_people (id, match_id, person_id, role_code) VALUES (?, ?, ?, ?)"
        )
    }
}
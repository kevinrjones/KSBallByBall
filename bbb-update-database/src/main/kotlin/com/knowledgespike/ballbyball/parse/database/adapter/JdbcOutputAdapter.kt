package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.types.values.PublicMatchId

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.PersonEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.MatchEntity
import com.knowledgespike.ballbyball.parse.database.getNameParts
import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Writes relational rows directly to the configured database. */
abstract class JdbcOutputAdapter(protected val connection: Connection) : OutputAdapter {
    private val log by LoggerDelegate()
    private var autoCommitBeforeMatch: Boolean? = null
    override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = queryKey(
        "select id from matches where canonical_match_id = ?",
        canonicalMatchId.value.toString()
    )

    override fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        when (matchWriteDecision(canonicalMatchId, publicMatchId)) {
            is MatchWriteDecision.Reuse -> backfillPublicMatchId(canonicalMatchId, publicMatchId)
            MatchWriteDecision.Insert -> Unit
        }
    }

    override fun sourceReferencesChanged(
        canonicalMatchId: CanonicalMatchId,
        sources: List<SourceReference>
    ): Boolean {
        val matchKey = findMatchKey(canonicalMatchId) ?: return false
        return try {
            val existing = mutableSetOf<String>()
            connection.prepareStatement(
                "select provider, provider_record_key, source_record_id, raw_content_digest " +
                    "from match_source_reference where match_id = ?"
            ).use { statement ->
                statement.setLong(1, matchKey)
                statement.executeQuery().use { results ->
                    while (results.next()) {
                        existing += listOf(
                            results.getString("provider"),
                            results.getString("provider_record_key"),
                            results.getString("source_record_id"),
                            results.getString("raw_content_digest")
                        ).joinToString("|")
                    }
                }
            }
            existing.isNotEmpty() && existing != sources.map { fingerprint(it) }.toSet()
        } catch (exception: SQLException) {
            log.warn("Source provenance table is unavailable; skipping correction detection", exception)
            false
        }
    }

    override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        queryKey("select id from people where source_person_id = ?", sourceId)?.let { return it }
        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        return insertWithKey(
            sql = RelationalWriteSupport.insertSql("people", RelationalWriteSupport.PERSON_JDBC_COLUMNS, "insert into"),
            sourceId,
            fullName,
            sortNamePart,
            otherNamePart,
            caId
        )
    }

    override fun upsertTeam(name: String): Team {
        findTeam(name)?.let { return it }
        val sourceId = nextSourceId("source_team_id", "teams")
        val key = insertWithKey(
            RelationalWriteSupport.insertSql("teams", RelationalWriteSupport.TEAM_JDBC_COLUMNS, "insert into"),
            sourceId,
            name
        )
        return Team(key, name)
    }

    override fun upsertGround(name: String): Ground {
        connection.prepareStatement("select id from grounds where ground_name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { results ->
                if (results.next()) return Ground(results.getLong(1), name)
            }
        }
        val sourceId = nextSourceId("source_ground_id", "grounds")
        val key = insertWithKey(
            RelationalWriteSupport.insertSql("grounds", RelationalWriteSupport.GROUND_JDBC_COLUMNS, "insert into"),
            sourceId,
            name
        )
        return Ground(key, name)
    }

    override fun upsertDate(date: LocalDate): Int {
        connection.prepareStatement("select date_id from dates where calendar_date = ?").use { statement ->
            statement.setDate(1, java.sql.Date.valueOf(date))
            statement.executeQuery().use { results ->
                if (results.next()) return results.getInt(1)
            }
        }

        val dimensions = RelationalWriteSupport.dateDimensions(date)
        connection.prepareStatement(
            RelationalWriteSupport.insertSql("dates", RelationalWriteSupport.DATE_COLUMNS, "insert into")
        ).use { statement ->
            dimensions.values(java.sql.Date.valueOf(date)).forEachIndexed { index, value ->
                setValue(statement, index + 1, value)
            }
            check(statement.executeUpdate() == 1)
        }
        return dimensions.dateKey
    }

    override fun insertMatch(match: MatchRecord): MatchEntity {
        return when (val decision = matchWriteDecision(match.canonicalMatchId, match.publicMatchId)) {
            is MatchWriteDecision.Reuse -> {
                backfillPublicMatchId(match.canonicalMatchId, match.publicMatchId)
                MatchEntity(decision.matchKey, match.publicMatchId)
            }

            MatchWriteDecision.Insert -> {
                val key = insertWithKey(
                    RelationalWriteSupport.insertSql(
                        "matches",
                        RelationalWriteSupport.MATCH_JDBC_COLUMNS,
                        "insert into"
                    ),
                    *RelationalWriteSupport.matchInsertValues(
                        match,
                        java.sql.Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC))
                    ).toTypedArray()
                )
                MatchEntity(key, match.publicMatchId)
            }
        }
    }

    override fun insertSourceReferences(matchId: Long, sources: List<SourceReference>) {
        sources.forEach { source ->
            execute(
                RelationalWriteSupport.insertSql(
                    "match_source_reference",
                    RelationalWriteSupport.SOURCE_REFERENCE_COLUMNS,
                    "insert into"
                ),
                *RelationalWriteSupport.sourceReferenceValues(matchId, source).toTypedArray()
            )
        }
    }

    override fun upsertInnings(
        matchId: Long,
        inningsNumber: Int,
        battingTeamId: Long,
        bowlingTeamId: Long,
        declared: Boolean
    ): InningsEntity {
        connection.prepareStatement("select id from innings where match_id = ? and innings_number = ?").use { statement ->
            statement.setLong(1, matchId)
            statement.setInt(2, inningsNumber)
            statement.executeQuery().use { results ->
                if (results.next()) {
                    val inningsKey = results.getLong(1)
                    connection.prepareStatement(
                        "update innings set declared = ? where id = ?"
                    ).use { update ->
                        update.setBoolean(1, declared)
                        update.setLong(2, inningsKey)
                        update.executeUpdate()
                    }
                    return InningsEntity(inningsKey)
                }
            }
        }
        val key = insertWithKey(
            RelationalWriteSupport.insertSql(
                "innings",
                RelationalWriteSupport.INNINGS_JDBC_COLUMNS,
                "insert into"
            ),
            *RelationalWriteSupport.inningsValues(
                inningsKey = 0,
                matchId = matchId,
                inningsNumber = inningsNumber,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                declared = declared
            ).drop(1).toTypedArray()
        )
        return InningsEntity(key)
    }

    override fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long? = queryKey(
        "select id from deliveries where match_id = ? and innings_id = ? and innings_order = ?",
        matchId,
        inningsId,
        inningsOrder
    )

    override fun insertDelivery(delivery: DeliveryRecord): Long {
        val sourceId = nextSourceId("source_ball_id", "deliveries")
        return insertWithKey(
            RelationalWriteSupport.insertSql(
                "deliveries",
                RelationalWriteSupport.DELIVERY_JDBC_COLUMNS,
                "insert into"
            ),
            *RelationalWriteSupport.deliveryInsertValues(sourceId, delivery).toTypedArray()
        )
    }

    override fun insertWicket(kind: String): Long {
        val sourceId = nextSourceId("source_wicket_id", "wickets")
        return insertWithKey(
            RelationalWriteSupport.insertSql("wickets", RelationalWriteSupport.WICKET_JDBC_COLUMNS, "insert into"),
            sourceId,
            kind
        )
    }

    override fun insertDeliveryWicket(deliveryId: Long, wicketId: Long) {
        execute(
            RelationalWriteSupport.insertSql(
                "delivery_wickets",
                RelationalWriteSupport.DELIVERY_WICKET_COLUMNS,
                "insert into"
            ),
            deliveryId,
            wicketId
        )
    }

    override fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long) {
        val affectedRows = executeAllowingNoOp(
            RelationalWriteSupport.insertSql(
                "delivery_fielders",
                RelationalWriteSupport.DELIVERY_FIELDER_COLUMNS,
                "insert into"
            ) + " " + duplicateDeliveryFielderClause(),
            deliveryId,
            wicketId,
            personId
        )
        if (affectedRows == 0) {
            log.warn(
                "Duplicate delivery_fielder suppressed: deliveryId={}, wicketId={}, personId={}",
                deliveryId,
                wicketId,
                personId
            )
        }
    }

    override fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String) {
        executeAllowingNoOp(
            RelationalWriteSupport.insertSql(
                "match_people",
                RelationalWriteSupport.MATCH_PERSON_INSERT_COLUMNS,
                "insert into"
            ) + " " + duplicateMatchPersonClause(),
            matchId,
            personId,
            roleCode
        )
    }

    protected abstract fun duplicateMatchPersonClause(): String

    protected abstract fun duplicateDeliveryFielderClause(): String

    override fun writeAllPeople(people: Sequence<PersonEntity>) = people.forEach { person ->
        upsertPerson(person.id, person.name, person.caId)
    }

    override fun beginMatch() {
        check(autoCommitBeforeMatch == null) { "A match transaction is already active" }
        val previousAutoCommit = connection.autoCommit
        if (previousAutoCommit) connection.autoCommit = false
        autoCommitBeforeMatch = previousAutoCommit
    }

    override fun commit() {
        connection.commit()
        restoreAutoCommit()
    }

    override fun rollback() {
        try {
            connection.rollback()
        } finally {
            restoreAutoCommit()
        }
    }

    override fun close() = Unit

    private fun matchWriteDecision(
        canonicalMatchId: CanonicalMatchId,
        publicMatchId: PublicMatchId
    ): MatchWriteDecision {
        val existingMatchKey = findMatchKey(canonicalMatchId)
        val existingPublicMatchId = existingMatchKey?.let {
            queryNullableKey(
                "select public_match_id from matches where canonical_match_id = ?",
                canonicalMatchId.value.toString()
            )
        }
        val publicMatchOwner = queryNullableKey(
            "select id from matches where public_match_id = ?",
            publicMatchId.value
        )
        return RelationalWriteSupport.matchWriteDecision(
            canonicalMatchId = canonicalMatchId,
            publicMatchId = publicMatchId,
            existingMatchKey = existingMatchKey,
            existingPublicMatchId = existingPublicMatchId,
            publicIdOwnedByAnotherMatch = publicMatchOwner != null && publicMatchOwner != existingMatchKey
        )
    }

    private fun backfillPublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        executeAllowingNoOp(
            "update matches set public_match_id = ? " +
                "where canonical_match_id = ? and public_match_id is null",
            publicMatchId.value,
            canonicalMatchId.value.toString()
        )
    }

    private fun findTeam(name: String): Team? {
        connection.prepareStatement("select id from teams where team_name = ?").use { statement ->
            statement.setString(1, name)
            statement.executeQuery().use { results ->
                if (results.next()) return Team(results.getLong(1), name)
            }
        }
        return null
    }

    private fun queryKey(sql: String, vararg values: Any?): Long? {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            statement.executeQuery().use { results ->
                if (results.next()) return results.getLong(1)
            }
        }
        return null
    }

    private fun queryNullableKey(sql: String, vararg values: Any?): Long? {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            statement.executeQuery().use { results ->
                if (results.next()) {
                    val value = results.getLong(1)
                    return if (results.wasNull()) null else value
                }
            }
        }
        return null
    }

    private fun nextSourceId(column: String, table: String): Long = queryKey(
        "select coalesce(max($column), 0) + 1 from $table"
    ) ?: throw InvalidStateException("Expected next source identifier")

    private fun insertWithKey(sql: String, vararg values: Any?): Long {
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            check(statement.executeUpdate() == 1)
            statement.generatedKeys.use { keys ->
                if (!keys.next()) throw InvalidStateException("Expected generated key")
                return keys.getLong(1)
            }
        }
    }

    private fun execute(sql: String, vararg values: Any?) {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            check(statement.executeUpdate() == 1)
        }
    }

    private fun executeAllowingNoOp(sql: String, vararg values: Any?): Int {
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> setValue(statement, index + 1, value) }
            return statement.executeUpdate().also { check(it >= 0) }
        }
    }

    private fun restoreAutoCommit() {
        val previousAutoCommit = autoCommitBeforeMatch ?: return
        try {
            connection.autoCommit = previousAutoCommit
        } finally {
            autoCommitBeforeMatch = null
        }
    }

    private fun setValue(statement: java.sql.PreparedStatement, index: Int, value: Any?) {
        when (value) {
            null -> statement.setNull(index, Types.NULL)
            is Int -> statement.setInt(index, value)
            is Long -> statement.setLong(index, value)
            is Boolean -> statement.setBoolean(index, value)
            is String -> statement.setString(index, value)
            is java.sql.Date -> statement.setDate(index, value)
            is java.sql.Timestamp -> statement.setTimestamp(index, value)
            else -> statement.setObject(index, value)
        }
    }
}
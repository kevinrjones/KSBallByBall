package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDate
import java.util.UUID

internal class JdbcIdentityStore(
    private val connection: Connection,
    private val identityCore: IdentityCore = IdentityCore()
) : MatchIdentityStore by identityCore {

    override fun findPersonKey(sourceId: String): Long? =
        identityCore.findPersonKey(sourceId) ?: queryPerson("select id, ca_id from people where source_person_id = ?", sourceId)

    override fun findPersonCaId(sourceId: String): Int? =
        identityCore.findPersonCaId(sourceId)

    override fun rememberPerson(sourceId: String, key: Long, caId: Int) {
        identityCore.rememberPerson(sourceId, key, caId)
    }

    override fun setPersonCaId(sourceId: String, caId: Int) {
        identityCore.setPersonCaId(sourceId, caId)
    }

    override fun findTeam(name: String): Team? = identityCore.findTeam(name) ?: queryKey(
        "select id from teams where team_name = ?",
        name
    )?.let { Team(it, name).also(identityCore::rememberTeam) }

    override fun findGround(name: String): Ground? = identityCore.findGround(name) ?: queryKey(
        "select id from grounds where ground_name = ?",
        name
    )?.let { Ground(it, name).also(identityCore::rememberGround) }

    override fun findDateKey(date: LocalDate): Int? = identityCore.findDateKey(date) ?: queryKey(
        "select date_id from dates where calendar_date = ?",
        java.sql.Date.valueOf(date)
    )?.toInt()?.also { identityCore.rememberDate(date, it) }

    override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = identityCore.findMatchKey(canonicalMatchId)
        ?: queryKey("select id from matches where canonical_match_id = ?", canonicalMatchId.value.toString())
            ?.also { identityCore.rememberMatchKey(canonicalMatchId, it) }

    override fun findMatchPublicId(canonicalMatchId: CanonicalMatchId): PublicMatchId? =
        identityCore.findMatchPublicId(canonicalMatchId) ?: findMatchKey(canonicalMatchId)?.let {
            queryNullableKey(
                "select public_match_id from matches where canonical_match_id = ?",
                canonicalMatchId.value.toString()
            )?.let { publicId ->
                PublicMatchId.from(publicId).also { identityCore.setMatchPublicId(canonicalMatchId, it) }
            }
        }

    override fun findPublicMatchOwner(publicMatchId: PublicMatchId): Long? = identityCore.findPublicMatchOwner(publicMatchId)
        ?: queryKey("select id from matches where public_match_id = ?", publicMatchId.value)
            ?.also { identityCore.rememberPublicMatchOwner(publicMatchId, it) }

    override fun findMatchSourceCaId(canonicalMatchId: CanonicalMatchId): String? =
        identityCore.sourceCaIdAllocator().sourceCaId(canonicalMatchId) ?: queryNullableString(
            "select source_ca_id from matches where canonical_match_id = ?",
            canonicalMatchId.value.toString()
        )

    override fun sourceCaOwnership(sourceCaId: String): SourceCaIdOwnership {
        val coreOwnership = identityCore.sourceCaOwnership(sourceCaId)
        val databaseCanonicalOwners = queryStrings(
            "select canonical_match_id from matches where source_ca_id = ? and canonical_match_id is not null",
            sourceCaId
        ).map { CanonicalMatchId.from(UUID.fromString(it)) }
        val uncanonicalMatchKey = queryKey(
            "select id from matches where source_ca_id = ? and canonical_match_id is null",
            sourceCaId
        )
        return SourceCaIdOwnership(
            canonicalMatchOwners = (databaseCanonicalOwners + coreOwnership.canonicalMatchOwners).distinct(),
            uncanonicalMatchKey = uncanonicalMatchKey ?: coreOwnership.uncanonicalMatchKey
        )
    }


    override fun sourceReferencesChanged(matchKey: Long, sources: List<SourceReference>): Boolean {
        val existing = connection.prepareStatement(
            "select provider, provider_record_key, source_record_id, raw_content_digest " +
                "from match_source_reference where match_id = ?"
        ).use { statement ->
            statement.setLong(1, matchKey)
            statement.executeQuery().use { results ->
                buildSet {
                    while (results.next()) {
                        add(
                            listOf(
                                results.getString("provider"),
                                results.getString("provider_record_key"),
                                results.getString("source_record_id"),
                                results.getString("raw_content_digest")
                            ).joinToString("|")
                        )
                    }
                }
            }
        }
        return existing.isEmpty() || existing != sources.map(::fingerprint).toSet()
    }

    override fun rememberSourceReferences(matchKey: Long, sources: List<SourceReference>) = Unit

    override fun findInnings(matchId: Long, inningsNumber: Int): InningsEntity? {
        return identityCore.findInnings(matchId, inningsNumber) ?: connection.prepareStatement(
            "select id, declared from innings where match_id = ? and innings_number = ?"
        ).use { statement ->
            statement.setLong(1, matchId)
            statement.setInt(2, inningsNumber)
            statement.executeQuery().use { results ->
                if (!results.next()) return@use null
                InningsEntity(results.getLong("id")).also { innings ->
                    identityCore.rememberInnings(matchId, inningsNumber, innings, results.getBoolean("declared"))
                }
            }
        }
    }

    override fun findInningsDeclared(matchId: Long, inningsNumber: Int): Boolean? {
        return identityCore.findInningsDeclared(matchId, inningsNumber) ?: connection.prepareStatement(
            "select declared from innings where match_id = ? and innings_number = ?"
        ).use { statement ->
            statement.setLong(1, matchId)
            statement.setInt(2, inningsNumber)
            statement.executeQuery().use { results ->
                if (results.next()) results.getBoolean("declared").also {
                    identityCore.setInningsDeclared(matchId, inningsNumber, it)
                } else null
            }
        }
    }

    override fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long? {
        return identityCore.findDeliveryKey(matchId, inningsId, inningsOrder) ?: queryKey(
            "select id from deliveries where match_id = ? and innings_id = ? and innings_order = ?",
            matchId,
            inningsId,
            inningsOrder
        )?.also { identityCore.rememberDelivery(matchId, inningsId, inningsOrder, it) }
    }

    override fun allocateKey(sequence: IdentitySequence): IdentityKey = when (sequence) {
        IdentitySequence.PERSON,
        IdentitySequence.TEAM,
        IdentitySequence.GROUND,
        IdentitySequence.MATCH,
        IdentitySequence.MATCH_PERSON,
        IdentitySequence.INNINGS,
        IdentitySequence.DELIVERY,
        IdentitySequence.WICKET -> IdentityKey(value = 0, databaseGenerated = true)
        IdentitySequence.TEAM_SOURCE -> IdentityKey(nextSourceId("source_team_id", "teams"))
        IdentitySequence.GROUND_SOURCE -> IdentityKey(nextSourceId("source_ground_id", "grounds"))
        IdentitySequence.BALL_SOURCE -> IdentityKey(nextSourceId("source_ball_id", "deliveries"))
        IdentitySequence.WICKET_SOURCE -> IdentityKey(nextSourceId("source_wicket_id", "wickets"))
    }


    private fun queryPerson(sql: String, sourceId: String): Long? = connection.prepareStatement(sql).use { statement ->
        statement.setString(1, sourceId)
        statement.executeQuery().use { results ->
            if (!results.next()) return@use null
            val id = results.getLong("id")
            val caId = results.getInt("ca_id")
            identityCore.rememberPerson(sourceId, id, caId)
            id
        }
    }

    private fun nextSourceId(column: String, table: String): Long =
        queryKey("select coalesce(max($column), 0) + 1 from $table")
            ?: throw IllegalStateException("Expected next source identifier for $table.$column")

    private fun queryKey(sql: String, vararg values: Any?): Long? = connection.prepareStatement(sql).use { statement ->
        statement.bind(values)
        statement.executeQuery().use { results -> if (results.next()) results.getLong(1) else null }
    }

    private fun queryNullableKey(sql: String, vararg values: Any?): Long? =
        connection.prepareStatement(sql).use { statement ->
            statement.bind(values)
            statement.executeQuery().use { results ->
                if (!results.next()) return@use null
                results.getLong(1).let { if (results.wasNull()) null else it }
            }
        }

    private fun queryNullableString(sql: String, vararg values: Any?): String? =
        connection.prepareStatement(sql).use { statement ->
            statement.bind(values)
            statement.executeQuery().use { results -> if (results.next()) results.getString(1) else null }
        }

    private fun queryStrings(sql: String, vararg values: Any?): List<String> =
        connection.prepareStatement(sql).use { statement ->
            statement.bind(values)
            statement.executeQuery().use { results -> buildList { while (results.next()) add(results.getString(1)) } }
        }

    private fun PreparedStatement.bind(values: Array<out Any?>) {
        values.forEachIndexed { index, value -> setJdbcValue(this, index + 1, value) }
    }

}
package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.clishared.identity.getNameParts
import com.knowledgespike.ballbyball.identity.SourcePersonId
import java.sql.Connection
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository interface for managing player identity mappings between Cricsheet IDs and stats player IDs.
 */
interface PlayerIdentityRepository {
    /**
     * Loads all existing player mappings from the database.
     */
    fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId>

    /**
     * Saves or updates a mapping between [sourcePersonId] and [statsPlayerId].
     */
    fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId)
}

/**
 * In-memory thread-safe cache for player ID mappings.
 */
interface PlayerCache {
    operator fun get(sourcePersonId: SourcePersonId): StatsPlayerId?
    operator fun set(sourcePersonId: SourcePersonId, statsPlayerId: StatsPlayerId)
    fun putAll(mappings: Map<SourcePersonId, StatsPlayerId>)
    val size: Int
}

class ThreadSafePlayerCache(
    initialMappings: Map<SourcePersonId, StatsPlayerId> = emptyMap()
) : PlayerCache {
    private val cache = ConcurrentHashMap<SourcePersonId, StatsPlayerId>(initialMappings)

    override operator fun get(sourcePersonId: SourcePersonId): StatsPlayerId? = cache[sourcePersonId]

    override operator fun set(sourcePersonId: SourcePersonId, statsPlayerId: StatsPlayerId) {
        cache[sourcePersonId] = statsPlayerId
    }

    override fun putAll(mappings: Map<SourcePersonId, StatsPlayerId>) {
        cache.putAll(mappings)
    }

    override val size: Int
        get() = cache.size
}

/**
 * JDBC implementation of [PlayerIdentityRepository] persisting mappings in the `people` table.
 */
class JdbcPlayerIdentityRepository(
    private val connection: Connection
) : PlayerIdentityRepository {

    override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> {
        val mappings = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val sql = "SELECT source_person_id, ca_id FROM people WHERE ca_id > 0"
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    val rawPersonId = rs.getString(1)
                    val sourcePersonId = rawPersonId?.let { SourcePersonId.of(it).getOrNull() }
                    val caId = rs.getInt(2)
                    if (sourcePersonId != null && caId > 0) {
                        mappings[sourcePersonId] = StatsPlayerId.from(caId)
                    }
                }
            }
        }
        return mappings
    }

    override fun savePlayerMapping(
        sourcePersonId: SourcePersonId,
        fullName: String,
        statsPlayerId: StatsPlayerId
    ) {
        val updateSql = "UPDATE people SET ca_id = ? WHERE source_person_id = ?"
        val rowsUpdated = connection.prepareStatement(updateSql).use { statement ->
            statement.setInt(1, statsPlayerId.value)
            statement.setString(2, sourcePersonId.value)
            statement.executeUpdate()
        }
        if (rowsUpdated == 0) {
            val (sortNamePart, otherNamePart) = getNameParts(fullName)
            val insertSql = """
                INSERT INTO people (source_person_id, full_name, sort_name_part, other_name_part, ca_id)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(insertSql).use { statement ->
                statement.setString(1, sourcePersonId.value)
                statement.setString(2, fullName)
                statement.setString(3, sortNamePart)
                statement.setString(4, otherNamePart)
                statement.setInt(5, statsPlayerId.value)
                statement.executeUpdate()
            }
        }
    }
}

package com.knowledgespike.ballbyball.parsecricsheet

import java.sql.Connection
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository interface for managing player identity mappings between Cricsheet IDs and stats player IDs.
 */
interface PlayerIdentityRepository {
    /**
     * Loads all existing player mappings from the database.
     */
    fun loadAllPlayerMappings(): Map<String, StatsPlayerId>

    /**
     * Saves or updates a mapping between [sourcePersonId] and [statsPlayerId].
     */
    fun savePlayerMapping(sourcePersonId: String, fullName: String, statsPlayerId: StatsPlayerId)
}

/**
 * In-memory thread-safe cache for player ID mappings.
 */
interface PlayerCache {
    operator fun get(sourcePersonId: String): StatsPlayerId?
    operator fun set(sourcePersonId: String, statsPlayerId: StatsPlayerId)
    fun putAll(mappings: Map<String, StatsPlayerId>)
    val size: Int
}

class ThreadSafePlayerCache(
    initialMappings: Map<String, StatsPlayerId> = emptyMap()
) : PlayerCache {
    private val cache = ConcurrentHashMap<String, StatsPlayerId>(initialMappings)

    override operator fun get(sourcePersonId: String): StatsPlayerId? = cache[sourcePersonId]

    override operator fun set(sourcePersonId: String, statsPlayerId: StatsPlayerId) {
        cache[sourcePersonId] = statsPlayerId
    }

    override fun putAll(mappings: Map<String, StatsPlayerId>) {
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

    override fun loadAllPlayerMappings(): Map<String, StatsPlayerId> {
        val mappings = mutableMapOf<String, StatsPlayerId>()
        val sql = "SELECT source_person_id, ca_id FROM people WHERE ca_id > 0"
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    val sourcePersonId = rs.getString(1)
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
        sourcePersonId: String,
        fullName: String,
        statsPlayerId: StatsPlayerId
    ) {
        val updateSql = "UPDATE people SET ca_id = ? WHERE source_person_id = ?"
        val rowsUpdated = connection.prepareStatement(updateSql).use { statement ->
            statement.setInt(1, statsPlayerId.value)
            statement.setString(2, sourcePersonId)
            statement.executeUpdate()
        }
        if (rowsUpdated == 0) {
            val (sortNamePart, otherNamePart) = getNameParts(fullName)
            val insertSql = """
                INSERT INTO people (source_person_id, full_name, sort_name_part, other_name_part, ca_id)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
            connection.prepareStatement(insertSql).use { statement ->
                statement.setString(1, sourcePersonId)
                statement.setString(2, fullName)
                statement.setString(3, sortNamePart)
                statement.setString(4, otherNamePart)
                statement.setInt(5, statsPlayerId.value)
                statement.executeUpdate()
            }
        }
    }

    private fun getNameParts(personName: String): Pair<String, String> {
        val fullName = if (personName.contains("(")) personName.substringBefore("(").trim() else personName
        if (!fullName.contains(" ")) return fullName.replace("'", "") to ""
        val parts = fullName.split(" ")
        return when {
            parts[0].uppercase(Locale.ROOT) == parts[0] -> {
                parts.drop(1).joinToString("").replace("'", "") to parts[0].trim()
            }
            parts[0] == "Lord" -> {
                parts.last().replace("'", "") to parts.drop(1).joinToString(" ")
            }
            parts[0] in setOf("Sir", "Earl", "Duke", "Nawab") && parts.size > 2 && parts[1] == "of" -> {
                parts.drop(2).first().replace("'", "") to parts.dropLast(1).joinToString(" ")
            }
            else -> fullName.replace(" ", "").replace("'", "") to ""
        }
    }
}

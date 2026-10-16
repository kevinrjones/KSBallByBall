package com.knowledgespike.ballbyball.parsecricsheet

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.identity.SourceCaId
import java.sql.Connection
import java.sql.PreparedStatement
import java.time.LocalDate
import java.util.Locale

/**
 * Represents an error related to the validation of a stats team ID.
 *
 * This sealed class is used to encapsulate specific errors that occur when a
 * stats team ID does not meet validity requirements.
 *
 * @property message A descriptive message explaining the error.
 */
sealed class StatsTeamIdError(val message: String) {
    /**
     * Represents an error where the provided stats team ID is invalid.
     *
     * This class is used to indicate that the stats team ID must be a positive integer.
     * It extends from the base class `StatsTeamIdError`.
     *
     * @constructor Creates an instance of `Invalid` with the given invalid value.
     * @param value The invalid stats team ID that caused the error.
     */
    class Invalid(value: Int?) : StatsTeamIdError("Stats team ID must be a positive integer: '$value'")
}

/**
 * Represents a strongly-typed ID for a stats team using a value class.
 * Ensures that the ID is always a positive integer.
 */
@JvmInline
value class StatsTeamId private constructor(val value: Int) {
    /**
     * Companion object for the `StatsTeamId` value class.
     * Provides factory methods for creating `StatsTeamId` instances
     * while enforcing validation rules.
     */
    companion object {
        /**
         * Creates a new instance of StatsTeamId if the provided value is a valid positive integer.
         * If the value is null or not greater than zero, an `Invalid` error is raised.
         *
         * @param value The integer value to be converted into a StatsTeamId. Must be a positive integer.
         * @return A valid StatsTeamId instance encapsulating the provided value.
         */
        context(raise: Raise<StatsTeamIdError.Invalid>)
        operator fun invoke(value: Int?): StatsTeamId {
            if (value == null || value <= 0) raise.raise(StatsTeamIdError.Invalid(value))
            return StatsTeamId(value)
        }

        /**
         * Constructs an instance of Either containing either an error or a valid StatsTeamId,
         * based on the provided integer value.
         *
         * @param value The integer input to be converted into a StatsTeamId or produce an Invalid error.
         * @return Either an Invalid error or a valid StatsTeamId.
         */
        fun of(value: Int): Either<StatsTeamIdError.Invalid, StatsTeamId> = either { invoke(value) }

        /**
         * Converts the given integer `value` into a `StatsTeamId` instance.
         * The value must be a positive integer, otherwise an exception is thrown.
         *
         * @param value the integer value to convert to a `StatsTeamId`
         * @return the corresponding `StatsTeamId` instance
         * @throws IllegalArgumentException if the value is not a positive integer
         */
        fun from(value: Int): StatsTeamId = requireNotNull(of(value).getOrNull()) {
            "Stats team ID must be a positive integer"
        }
    }
}

/**
 * Repository interface for retrieving match statistics based on teams and criteria.
 */
interface StatsMatchRepository {
    /**
     * Retrieves the count of teams associated with the given stats team identifier.
     *
     * @param statsTeamId The unique identifier representing a specific stats team.
     * @return The number of teams linked to the provided stats team identifier.
     */
    fun statsTeamCount(statsTeamId: StatsTeamId): Int

    /**
     * Retrieves a list of source CA IDs based on the given match lookup criteria and team configurations.
     *
     * @param criteria The criteria containing details about the match, including teams and match date.
     * @param firstTeam The lookup configuration for the first team, which can include name or ID variations.
     * @param secondTeam The lookup configuration for the second team, which can include name or ID variations.
     * @return A list of source CA IDs that match the specified criteria and team configurations.
     */
    fun caIds(
        criteria: MatchLookupCriteria,
        firstTeam: StatsTeamLookup,
        secondTeam: StatsTeamLookup
    ): List<SourceCaId>
}

/**
 * Represents a candidate match for statistical analysis in the context of cricket matches.
 * This data class encapsulates information such as the index of the candidate match,
 * the date of the match, the source CA ID associated with the match, and a numeric metric.
 *
 * @property candidateIndex The index identifying the candidate match in a sequence or list.
 * @property matchDate The date on which the match occurred.
 * @property caId The unique source CA ID associated with the match, ensuring the validity of the identifier.
 * @property howMuch A numeric metric that provides details or measures related to the candidate match.
 */
data class StatsMatchCandidateMatch(
    val candidateIndex: Int,
    val matchDate: LocalDate,
    val caId: SourceCaId,
    val howMuch: Int
)

/**
 * Repository interface for retrieving match candidate statistics in bulk.
 */
interface BulkStatsMatchRepository {
    /**
     * Identifies and matches candidate stats based on a list of dates and provided match candidates.
     *
     * @param dates A list of dates to filter or identify matches for the candidates.
     * @param candidates A list of potential match candidates to evaluate against the dates.
     * @return A list of matched candidates containing details about the match, such as the candidate index, date, and additional information.
     */
    fun candidateMatches(
        dates: List<LocalDate>,
        candidates: List<StatsMatchCandidate>
    ): List<StatsMatchCandidateMatch>
}

/**
 * A repository implementation for retrieving and processing match statistics from a relational database
 * using JDBC. This class interacts with the database to fetch information about teams and matches based on
 * various criteria.
 *
 * @property connection A JDBC database connection used to execute queries.
 *
 * Implements:
 * - [StatsMatchRepository]: Provides methods to interact with match statistics at a granular level.
 * - [BulkStatsMatchRepository]: Provides methods to retrieve match details with multiple filtering criteria.
 */
class JdbcStatsMatchRepository(private val connection: Connection) : StatsMatchRepository, BulkStatsMatchRepository {
    /**
     * A mapping of team names and IDs used for indexing teams within the repository.
     *
     * This property is initialized by invoking the `loadTeams` method, which queries the database
     * for team-related information, such as local team IDs, stats team IDs, and team names.
     *
     * `teams` serves as an internal cache to optimize team lookups and is populated with the data
     * returned from the database query during initialization.
     *
     * The data structure returned by `loadTeams` includes:
     * - A map of team names (converted to lowercase) to their associated local team IDs.
     * - A map of stats team IDs to their associated local team IDs.
     */
    private val teams = loadTeams()

    /**
     * Calculates the count of teams associated with a specific statistics team.
     *
     * @param statsTeamId The identifier of the statistics team whose associated team count is to be retrieved.
     * @return The number of teams associated with the given statistics team.
     */
    override fun statsTeamCount(statsTeamId: StatsTeamId): Int = teams.idsByStatsTeamId[statsTeamId.value].orEmpty().size

    /**
     * Retrieves a list of unique stats match CA IDs based on the provided match lookup criteria
     * and team lookup details for the first and second teams.
     *
     * The method filters candidate matches based on match date and an optional margin, returning the
     * distinct CA IDs associated with the filtered matches.
     *
     * @param criteria The criteria containing details like match date and optional margin
     *        for filtering candidate matches.
     * @param firstTeam The lookup information for the first team, which can include a name,
     *        ID, set of IDs, or a combination of these.
     * @param secondTeam The lookup information for the second team, which can include a name,
     *        ID, set of IDs, or a combination of these.
     * @return A list of unique stats match CA IDs corresponding to matches that satisfy
     *         the provided criteria and team lookups.
     */
    override fun caIds(
        criteria: MatchLookupCriteria,
        firstTeam: StatsTeamLookup,
        secondTeam: StatsTeamLookup
    ): List<SourceCaId> = candidateMatches(
        listOf(criteria.matchDate),
        listOf(StatsMatchCandidate(firstTeam, secondTeam))
    ).asSequence()
        .filter { criteria.margin == null || it.howMuch == criteria.margin }
        .map(StatsMatchCandidateMatch::caId)
        .distinct()
        .toList()

    /**
     * Matches a list of candidates against a list of dates and returns a list of candidate matches
     * based on available statistical data.
     *
     * @param dates A list of distinct LocalDate values representing the possible match dates
     *              to query against.
     * @param candidates A list of StatsMatchCandidate objects representing the match candidates
     *                   to filter and match.
     * @return A list of StatsMatchCandidateMatch objects containing the matched candidate details,
     *         including the candidate index, match date, source CA ID, and matching score.
     */
    override fun candidateMatches(
        dates: List<LocalDate>,
        candidates: List<StatsMatchCandidate>
    ): List<StatsMatchCandidateMatch> {
        if (dates.isEmpty() || candidates.isEmpty()) return emptyList()
        val indexedCandidates = candidates.mapIndexedNotNull { index, candidate ->
            val firstTeamIds = teams.idsFor(candidate.firstTeam)
            val secondTeamIds = teams.idsFor(candidate.secondTeam)
            if (firstTeamIds.isEmpty() || secondTeamIds.isEmpty()) {
                null
            } else {
                IndexedTeamCandidate(index, firstTeamIds, secondTeamIds)
            }
        }
        if (indexedCandidates.isEmpty()) return emptyList()

        val distinctDates = dates.distinct().sorted()
        val sql = lookupSql(distinctDates.size, indexedCandidates)
        return connection.prepareStatement(sql).use { statement ->
            distinctDates.forEachIndexed { index, date ->
                statement.setString(index + 1, date.toString())
            }
            bindCandidates(statement, distinctDates.size + 1, indexedCandidates)
            statement.executeQuery().use { results ->
                buildList {
                    while (results.next()) {
                        val caId = requireNotNull(results.getString("CaId")) { "Stats match CA ID must not be null" }
                        val homeTeamId = results.getInt("HomeTeamId")
                        val awayTeamId = results.getInt("AwayTeamId")
                        val matchDate = requireNotNull(results.getString("MatchStartDate")) {
                            "Stats match start date must not be null"
                        }.let(LocalDate::parse)
                        val howMuch = results.getInt("HowMuch")
                        indexedCandidates.filter { candidate ->
                            candidate.matches(homeTeamId, awayTeamId)
                        }.forEach { candidate ->
                            add(StatsMatchCandidateMatch(candidate.index, matchDate, SourceCaId.from(caId), howMuch))
                        }
                    }
                }
            }
        }
    }

    /**
     * Constructs a SQL query for fetching matches based on the provided date count and candidate teams.
     *
     * @param dateCount The number of unique dates to include in the query, each represented by a placeholder.
     * @param candidates A list of team candidate pairs, where each candidate includes information about
     *                   the first and second team IDs that must be matched in the query.
     * @return A string containing the constructed SQL query.
     */
    private fun lookupSql(dateCount: Int, candidates: List<IndexedTeamCandidate>): String {
        val datePlaceholders = List(dateCount) { "?" }.joinToString(", ")
        val candidatePredicates = candidates.joinToString(" OR ") { candidate ->
            "((m.HomeTeamId IN (${candidate.firstTeamIds.placeholders()}) " +
                "AND m.AwayTeamId IN (${candidate.secondTeamIds.placeholders()})) OR " +
                "(m.HomeTeamId IN (${candidate.secondTeamIds.placeholders()}) " +
                "AND m.AwayTeamId IN (${candidate.firstTeamIds.placeholders()})))"
        }
        return """
        SELECT m.CaId, m.MatchStartDate, m.HowMuch, m.HomeTeamId, m.AwayTeamId
        FROM Matches m
        WHERE m.MatchStartDate IN ($datePlaceholders)
          AND ($candidatePredicates)
          AND m.MatchType NOT IN ('t', 'wt', 'o', 'wo', 'itt', 'witt')
        ORDER BY m.CaId, m.MatchStartDate
        """.trimIndent()
    }

    /**
     * Binds the given candidates' team IDs to placeholders in the provided SQL statement.
     *
     * Each team's IDs are sequentially set into the prepared statement starting from the specified
     * initial parameter index. The order of setting is determined by the structure of each candidate's
     * `firstTeamIds` and `secondTeamIds` lists.
     *
     * @param statement the prepared SQL statement where parameters are to be set
     * @param firstParameterIndex the initial parameter index for binding values
     * @param candidates the list of indexed team candidates containing team IDs to be bound
     */
    private fun bindCandidates(
        statement: PreparedStatement,
        firstParameterIndex: Int,
        candidates: List<IndexedTeamCandidate>
    ) {
        var parameterIndex = firstParameterIndex
        candidates.forEach { candidate ->
            candidate.firstTeamIds.forEach { statement.setInt(parameterIndex++, it) }
            candidate.secondTeamIds.forEach { statement.setInt(parameterIndex++, it) }
            candidate.secondTeamIds.forEach { statement.setInt(parameterIndex++, it) }
            candidate.firstTeamIds.forEach { statement.setInt(parameterIndex++, it) }
        }
    }

    /**
     * Loads team information from the database and organizes it into a `TeamIndex`.
     * This method queries a predefined SQL statement to retrieve team data and processes the results
     * into mappings by team name (case-insensitive) and stats team ID.
     *
     * @return a `TeamIndex` containing mappings of team information:
     * - `idsByName` maps normalized lowercase team names to lists of local team IDs.
     * - `idsByStatsTeamId` maps stats team IDs to lists of local team IDs.
     */
    private fun loadTeams(): TeamIndex = connection.prepareStatement(TEAMS_SQL).use { statement ->
        statement.executeQuery().use { results ->
            val idsByName = mutableMapOf<String, MutableList<Int>>()
            val idsByStatsTeamId = mutableMapOf<Int, MutableList<Int>>()
            while (results.next()) {
                val localTeamId = results.getInt("Id")
                val statsTeamId = results.getInt("TeamId")
                val name = requireNotNull(results.getString("Name")) { "Stats team name must not be null" }
                idsByName.getOrPut(name.lowercase(Locale.ROOT), ::mutableListOf).add(localTeamId)
                idsByStatsTeamId.getOrPut(statsTeamId, ::mutableListOf).add(localTeamId)
            }
            TeamIndex(
                idsByName = idsByName.mapValues { (_, ids) -> ids.toList() },
                idsByStatsTeamId = idsByStatsTeamId.mapValues { (_, ids) -> ids.toList() }
            )
        }
    }

    /**
     * Companion object holding constants and shared definitions for the enclosing class.
     */
    private companion object {
        /**
         * A constant SQL query string used to retrieve information about teams.
         *
         * The query selects the `Id`, `TeamId`, and `Name` columns from the `Teams` table.
         */
        const val TEAMS_SQL = "SELECT Id, TeamId, Name FROM Teams"
    }
}

/**
 * Represents a team candidate with an assigned index and two lists of team IDs indicating
 * potential team matchups.
 *
 * @property index The unique index associated with this team candidate.
 * @property firstTeamIds A list of team IDs representing the first group of potential teams.
 * @property secondTeamIds A list of team IDs representing the second group of potential teams.
 */
private data class IndexedTeamCandidate(
    val index: Int,
    val firstTeamIds: List<Int>,
    val secondTeamIds: List<Int>
) {
    /**
     * Determines if the given home and away team IDs match the criteria for belonging to the same pair
     * of first and second team IDs.
     *
     * @param homeTeamId the ID of the home team to check.
     * @param awayTeamId the ID of the away team to check.
     * @return true if the home team ID is in the list of first team IDs and the away team ID
     *         is in the list of second team IDs, or if the home team ID is in the list of
     *         second team IDs and the away team ID is in the list of first team IDs; false otherwise.
     */
    fun matches(homeTeamId: Int, awayTeamId: Int): Boolean =
        (homeTeamId in firstTeamIds && awayTeamId in secondTeamIds) ||
            (homeTeamId in secondTeamIds && awayTeamId in firstTeamIds)
}

/**
 * Represents an indexed mapping of team data, allowing lookups of team IDs by either name or stats team ID.
 *
 * @property idsByName A map where the key is a team name (as a string) and the value is a list of corresponding team IDs.
 * @property idsByStatsTeamId A map where the key is a stats team ID (as an integer) and the value is a list of corresponding team IDs.
 */
private data class TeamIndex(
    val idsByName: Map<String, List<Int>>,
    val idsByStatsTeamId: Map<Int, List<Int>>
) {
    /**
     * Retrieves a distinct and sorted list of IDs based on the provided team lookup criteria.
     *
     * @param team The lookup criteria for retrieving the IDs, which can specify a team by name, ID,
     *             a set of IDs, or any combination thereof.
     * @return A distinct and sorted list of integers matching the provided lookup criteria.
     */
    fun idsFor(team: StatsTeamLookup): List<Int> = when (team) {
        is StatsTeamLookup.Name -> idsByName[team.value.lowercase(Locale.ROOT)].orEmpty()
        is StatsTeamLookup.Id -> idsByStatsTeamId[team.value.value].orEmpty()
        is StatsTeamLookup.IdSet -> team.values.flatMap { idsByStatsTeamId[it.value].orEmpty() }
        is StatsTeamLookup.AnyOf -> team.values.flatMap(::idsFor)
    }.distinct().sorted()
}

/**
 * Creates a string where each integer in the list is replaced with a "?" placeholder.
 * The placeholders are separated by a comma.
 *
 * @return A string consisting of "?" placeholders separated by commas, representing the size of the list.
 */
private fun List<Int>.placeholders(): String = joinToString(", ") { "?" }

/**
 * Represents a sealed interface for looking up statistics for teams using various criteria.
 */
sealed interface StatsTeamLookup {
    /**
     * Represents a data class for a name with a single property.
     *
     * This class is primarily used within the context of `StatsTeamLookup`
     * and encapsulates a value representing a name as a string.
     *
     * @property value The string representation of the name.
     */
    data class Name(val value: String) : StatsTeamLookup

    /**
     * Represents an identifier for a StatsTeam entity.
     *
     * This data class wraps a `StatsTeamId` and implements the `StatsTeamLookup` interface.
     * It is used to encapsulate and manage identifiers within the StatsTeam domain.
     *
     * @property value The underlying `StatsTeamId` associated with this identifier.
     */
    data class Id(val value: StatsTeamId) : StatsTeamLookup

    /**
     * A data class representing a set of unique statistical team identifiers.
     *
     * This class ensures that the provided list of statistical team IDs is not empty upon initialization.
     * It implements the StatsTeamLookup interface to support operations tied to statistical team lookups.
     *
     * @constructor Validates and initializes the set of team identifiers.
     * @param values A non-empty list of statistical team identifiers.
     * @throws IllegalArgumentException if the provided list is empty.
     */
    data class IdSet(val values: List<StatsTeamId>) : StatsTeamLookup {
        init {
            require(values.isNotEmpty()) { "Stats team ID candidates must not be empty" }
        }
    }

    /**
     * A data class that represents a combination of multiple `StatsTeamLookup` instances.
     * This class ensures that there are at least two values in the combination.
     *
     * @property values A list of `StatsTeamLookup` instances. Must contain at least two items.
     * @throws IllegalArgumentException if the number of items in `values` is less than two.
     */
    data class AnyOf(val values: List<StatsTeamLookup>) : StatsTeamLookup {
        init {
            require(values.size >= 2) { "Combined stats team candidates must contain at least two values" }
        }

    }
}
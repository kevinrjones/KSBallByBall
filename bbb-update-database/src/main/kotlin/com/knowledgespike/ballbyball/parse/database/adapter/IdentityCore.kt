package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import java.time.LocalDate

/**
 * The IdentityCore class is an internal implementation of the MatchIdentityStore interface
 * responsible for managing various identity mappings and transactions related to entities
 * such as people, teams, grounds, matches, and other cricket-related components in the system.
 */
internal class IdentityCore : MatchIdentityStore {
    /**
     * A map that maintains transactional state for tracking associations between person identifiers
     * and their corresponding unique keys within the context of the IdentityCore class.
     *
     * This collection is used to map string-based source IDs (e.g., external identifiers for people)
     * to long-based internal keys managed within the system. It supports transactional features,
     * allowing changes to be tracked and either committed or rolled back.
     *
     * Key Characteristics:
     * - The `people` map uses a `TransactionMap` implementation to provide transactional semantics.
     * - Enables efficient lookups and updates for associating external identifiers with internal keys.
     * - Handles scenarios where entries can be added, modified, or removed with rollback support if needed.
     */
    private val people = TransactionMap<String, Long>()
    /**
     * Stores and manages the set of teams within the identity core context.
     *
     * This is implemented as a transaction-aware map, allowing changes to be tracked
     * and rolled back if needed. The keys in this map represent team identifiers,
     * while the values are instances of the `Team` data class. It provides functionality
     * to maintain consistency during operations that involve multiple updates or require
     * transactional integrity.
     */
    private val teams = TransactionMap<String, Team>()
    /**
     * Represents a transactional map used to manage the association between ground names (keys)
     * and their corresponding ground details (values) within the IdentityCore system.
     *
     * This map supports transactional operations, allowing changes to be committed or rolled
     * back as part of a broader transaction management system.
     *
     * Key:
     * - A string representing the name of a ground.
     *
     * Value:
     * - A `Ground` object containing details about the ground, which includes its unique ID and name.
     *
     * Primary Use:
     * - To store and retrieve ground information within the context of identity tracking and
     *   transactional state changes.
     */
    private val grounds = TransactionMap<String, Ground>()
    /**
     * A map that associates dates with unique integer keys. This map is used to
     * maintain a bidirectional relationship between LocalDate instances and
     * their corresponding integer identifiers.
     *
     * The `dates` map supports transactional operations, allowing changes to be
     * committed or rolled back as needed. Each entry can be added, modified, or
     * removed within a transactional context using the underlying functionality
     * of `TransactionMap`.
     *
     * Key: Represents a date (`LocalDate`).
     * Value: Represents an integer identifier (`Int`) associated with the date.
     */
    private val dates = TransactionMap<LocalDate, Int>()
    /**
     * A mapping of canonical match identifiers to their corresponding match keys.
     *
     * This map is used to store and manage relationships between `CanonicalMatchId` values and
     * their associated `Long` keys. Changes to the map can be tracked and managed in a transactional
     * manner using the underlying `TransactionMap` implementation to support rollback and commit
     * operations when necessary.
     */
    private val matches = TransactionMap<CanonicalMatchId, Long>()
    /**
     * A transactional map that maintains a relationship between canonical match identifiers
     * and their corresponding public match identifiers.
     *
     * This map allows tracking and managing public identifiers assigned to matches, supporting
     * transactional operations like commit and rollback to ensure consistency in updates.
     *
     * The keys in this map are of type [CanonicalMatchId], uniquely identifying matches at a
     * canonical level, while the values are of type [PublicMatchId], representing the publicly
     * visible identifiers for these matches.
     */
    private val matchPublicIds = TransactionMap<CanonicalMatchId, PublicMatchId>()
    /**
     * A transaction-aware map that associates `PublicMatchId` with `Long` values, used to manage
     * relationships between public match identifiers and their corresponding internal match keys.
     *
     * This map supports transactional operations, including the ability to begin, commit, and rollback
     * changes, ensuring data consistency and enabling recovery from incomplete updates.
     */
    private val publicMatches = TransactionMap<PublicMatchId, Long>()
    /**
     * Allocates and manages identifiers for source CA (Canonical Assignment) entities in the IdentityCore system.
     * This allocator is responsible for generating, maintaining, and tracking source CA IDs to ensure uniqueness
     * and consistency within the context of match and identity management.
     */
    private val sourceCaIdAllocator = SourceCaIdAllocator()
    /**
     * A transactional map that maintains mappings between match keys and sets of source identifiers.
     *
     * This map is used to track associations for a match and its corresponding sources, allowing
     * operations to be performed atomically within a transaction. The keys represent unique match
     * identifiers, while the values are sets of strings representing source references.
     *
     * The `TransactionMap` implementation ensures that changes to the map can be committed or
     * rolled back, providing robust transaction control for operations requiring consistency.
     */
    private val sourceReferences = TransactionMap<Long, Set<String>>()
    /**
     * Stores data related to individual innings within cricket matches.
     *
     * A map that associates a pair of keys, where:
     * - The first element of the pair is the match ID represented as a `Long`.
     * - The second element of the pair is the innings number represented as an `Int`.
     * with `InningsEntity`.
     *
     * This collection is transactional, allowing for controlled handling of changes
     * to the innings records with added support for rollback and commit operations.
     */
    private val innings = TransactionMap<Pair<Long, Int>, InningsEntity>()
    /**
     * A transactional map that tracks the declaration status of innings in cricket matches.
     * The key is a pair consisting of the match ID (Long) and the innings number (Int).
     * The value is a Boolean indicating whether the innings has been declared.
     *
     * This map helps in managing and persisting the state of innings declarations
     * across transactions, ensuring consistent behavior even during rollbacks or commits.
     */
    private val inningsDeclared = TransactionMap<Pair<Long, Int>, Boolean>()
    /**
     * Maps deliveries within a match context to their unique keys.
     *
     * The key is represented as a `Triple` consisting of:
     * - `Long`: Match ID
     * - `Long`: Inning ID
     * - `Int`: Delivery order within the inning
     *
     * The value associated with each key is a `Long` representing the delivery's unique identifier.
     *
     * Utilized for managing transactional updates and rollbacks for delivery-related data in the
     * system while ensuring data consistency during operations. Backed by a `TransactionMap`
     * for handling these transactional capabilities.
     */
    private val deliveries = TransactionMap<Triple<Long, Long, Int>, Long>()
    /**
     * Represents a transactional set of associations between matches and people involved in them.
     * Each entry is a triple containing:
     * - The unique match identifier (Long),
     * - The unique person identifier (Long),
     * - A role code describing the person's role in the context of the match (String).
     *
     * Utilized to track the relationships between matches and individuals based on their roles,
     * such as players, umpires, or officials. The backing implementation ensures that
     * changes to this set are transactional, providing commit and rollback capabilities.
     */
    private val matchPeople = TransactionSet<Triple<Long, Long, String>>()
    /**
     * Represents a set of fielders associated with specific deliveries and wickets in a cricket match.
     *
     * Each entry in the set is a Triple<Long, Long, Long>, where:
     * - The first Long represents the delivery ID.
     * - The second Long represents the wicket ID.
     * - The third Long represents the fielder's person ID.
     *
     * This set is transactional, allowing changes to be made within a transaction
     * that can be committed or rolled back.
     *
     * Used to manage and track the fielders involved in deliveries and their association
     * with wickets, ensuring consistency during transactional operations.
     */
    private val deliveryFielders = TransactionSet<Triple<Long, Long, Long>>()
    /**
     * Tracks the next available unique identifier for teams within the context of the IdentityCore system.
     * This variable is initialized to 1 and is incremented each time a new team identifier is allocated.
     */
    private var nextTeamSourceId = 1L
    /**
     * Tracks the next available unique identifier for ground-related entities.
     * This value is incremented each time a new ground entity is registered to ensure unique identification.
     */
    private var nextGroundSourceId = 1L
    /**
     * Tracks the next available identifier for ball sources within the system.
     *
     * This variable is used to allocate unique identifiers for balls,
     * ensuring that each ball source has a distinct and sequential identifier.
     * It is initialized to 1 and is incremented as new ball sources are added.
     */
    private var nextBallSourceId = 1L
    /**
     * Tracks the next available unique identifier for a wicket source in the system.
     * This variable is incremented each time a new wicket source ID is allocated.
     * It is used to ensure unique identification of wicket sources.
     */
    private var nextWicketSourceId = 1L
    /**
     * Tracks the next available unique identifier for a person entity within the IdentityCore class.
     * This value is incremented each time a new person is added and serves as a basis for generating unique keys for person records.
     */
    private var nextPersonKey = 1L
    /**
     * Tracks the next unique key to be assigned to a team within the system.
     * This variable is used to generate and manage unique identifiers for teams
     * during the lifecycle of the application.
     *
     * The initial value is set to `1L`, and it is incremented whenever a new
     * team key is allocated.
     */
    private var nextTeamKey = 1L
    /**
     * Represents the next unique key to be allocated for a new ground entity.
     * This value is used to ensure uniqueness across all ground entities within the system.
     * It is incremented for each new ground that gets assigned a key.
     */
    private var nextGroundKey = 1L
    /**
     * Tracks the next unique identifier for matches in the system.
     *
     * This variable is used internally to allocate a new unique key for each match
     * being added to the data model. It ensures that each match is assigned a unique
     * and sequential numeric identifier. The value starts at 1 and is incremented
     * as new keys are generated.
     */
    private var nextMatchKey = 1L
    /**
     * Represents a unique key used to identify the person in the next match.
     * This variable is initialized with a default value and can be updated
     * as needed to ensure uniqueness for each subsequent match-related operation.
     */
    private var nextMatchPersonKey = 1L
    /**
     * Represents the next unique key to be assigned to an innings within the system.
     * This variable is used as a sequence identifier to generate unique keys
     * for newly created innings entities.
     *
     * It ensures that each innings receives a distinct identifier through
     * incrementing operations, thereby maintaining consistency and uniqueness
     * across all innings records.
     */
    private var nextInningsKey = 1L
    /**
     * Tracks the next unique key to be assigned for deliveries in the system.
     * This variable is used to generate sequential, unique identifiers
     * for deliveries during the management of cricket match data.
     */
    private var nextDeliveryKey = 1L
    /**
     * Tracks the next unique identifier for a wicket in the cricket match context.
     * This variable is used to allocate sequential and unique keys for wicket-related
     * entities or events within the system.
     */
    private var nextWicketKey = 1L

    /**
     * Searches for a person key associated with the specified source identifier.
     *
     * @param sourceId The identifier for the person to be searched.
     * @return The person key if found, or null if no matching key exists.
     */
    override fun findPersonKey(sourceId: String): Long? = people[sourceId]

    /**
     * Associates a person identified by a source ID with a specific key.
     *
     * @param sourceId A unique identifier for the person from the source system.
     * @param key The unique key to be associated with the person.
     */
    override fun rememberPerson(sourceId: String, key: Long) {
        people[sourceId] = key
    }

    /**
     * Finds a team by its name.
     *
     * @param name the name of the team to be retrieved
     * @return the team associated with the given name, or null if no team is found
     */
    override fun findTeam(name: String): Team? = teams[name]

    /**
     * Stores the given team in the identity store.
     *
     * @param team The team to be remembered, identified by its unique name and ID.
     */
    override fun rememberTeam(team: Team) {
        teams[team.name] = team
    }

    /**
     * Retrieves a Ground object corresponding to the specified name, if it exists.
     *
     * @param name The name of the ground to find.
     * @return The Ground object with the specified name, or null if not found.
     */
    override fun findGround(name: String): Ground? = grounds[name]

    /**
     * Stores the provided ground in the grounds map using its name as the key.
     *
     * @param ground The Ground instance to be remembered. Its name will be used as a key in the map.
     */
    override fun rememberGround(ground: Ground) {
        grounds[ground.name] = ground
    }

    /**
     * Finds the key associated with the given date in the `dates` map.
     *
     * @param date the date for which the corresponding key is to be retrieved.
     * @return the key associated with the given date, or null if no mapping exists for the date.
     */
    override fun findDateKey(date: LocalDate): Int? = dates[date]

    /**
     * Associates the given date with the provided key in the internal storage.
     *
     * @param date The date to be remembered.
     * @param key The key to associate with the given date.
     */
    override fun rememberDate(date: LocalDate, key: Int) {
        dates[date] = key
    }

    /**
     * Retrieves the match key associated with the given canonical match identifier.
     *
     * @param canonicalMatchId The canonical identifier of the match whose key needs to be found.
     * @return The match key as a [Long], or null if no match key is found for the given identifier.
     */
    override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = matches[canonicalMatchId]

    /**
     * Associates a given key with the specified canonical match identifier.
     *
     * @param canonicalMatchId The unique identifier for a canonical match.
     * @param key The key to be associated with the specified canonical match identifier.
     */
    override fun rememberMatchKey(canonicalMatchId: CanonicalMatchId, key: Long) {
        matches[canonicalMatchId] = key
    }

    /**
     * Finds the public match identifier corresponding to the given canonical match identifier.
     *
     * @param canonicalMatchId the unique canonical identifier of the match for which the
     *                         public match identifier is being retrieved.
     * @return the public match identifier associated with the given canonical match identifier,
     *         or null if no matching public match identifier exists.
     */
    override fun findMatchPublicId(canonicalMatchId: CanonicalMatchId): PublicMatchId? =
        matchPublicIds[canonicalMatchId]

    /**
     * Retrieves the owner of a public match based on the given public match ID.
     *
     * @param publicMatchId The unique identifier of the public match.
     * @return The ID of the owner of the public match, or null if no owner is found.
     */
    override fun findPublicMatchOwner(publicMatchId: PublicMatchId): Long? = publicMatches[publicMatchId]

    /**
     * Associates a public match identifier with its corresponding match key.
     *
     * @param publicMatchId The unique identifier for the public match. Must be a valid instance of [PublicMatchId].
     * @param matchKey The key representing the internal match identifier as a Long value.
     */
    fun rememberPublicMatchOwner(publicMatchId: PublicMatchId, matchKey: Long) {
        publicMatches[publicMatchId] = matchKey
    }

    /**
     * Finds the source CA ID associated with a given canonical match ID.
     *
     * @param canonicalMatchId The canonical identification of the match for which the source CA ID is to be retrieved.
     * @return The source CA ID associated with the provided canonical match ID, or null if no association exists.
     */
    override fun findMatchSourceCaId(canonicalMatchId: CanonicalMatchId): String? =
        sourceCaIdAllocator.sourceCaId(canonicalMatchId)

    /**
     * Determines the ownership of a given source CA ID by identifying all the registered and pending owners.
     *
     * @param sourceCaId The source CA ID for which ownership is to be determined.
     * @return A `SourceCaIdOwnership` instance containing both registered and pending owners for the given source CA ID.
     */
    override fun sourceCaOwnership(sourceCaId: String): SourceCaIdOwnership {
        val registeredOwners = listOfNotNull(sourceCaIdAllocator.sourceCaOwner(sourceCaId))
        val pendingOwners = sourceCaIdAllocator.pendingAssignments()
            .filterValues { it == sourceCaId }
            .keys
        return SourceCaIdOwnership((registeredOwners + pendingOwners).distinct())
    }

    /**
     * Associates a canonical match ID with a match key and a public match ID.
     * Updates internal mappings to maintain the relationships between the provided IDs.
     *
     * @param canonicalMatchId The unique identifier for the match within the system.
     * @param key The internal key associated with the match.
     * @param publicMatchId The public-facing identifier for the match.
     */
    override fun rememberMatch(canonicalMatchId: CanonicalMatchId, key: Long, publicMatchId: PublicMatchId) {
        matches[canonicalMatchId] = key
        setMatchPublicId(canonicalMatchId, publicMatchId)
    }

    /**
     * Updates the mapping of a canonical match ID to a public match ID.
     * If a public match ID is already associated with the given canonical match ID, it will be removed.
     * If the provided public match ID is not null, it will establish a new association between the canonical match ID
     * and the public match ID, and link the public match ID to its corresponding match.
     *
     * @param canonicalMatchId The canonical match ID that uniquely identifies the match.
     * @param publicMatchId The public match ID to associate with the canonical match ID, or null to remove the association.
     */
    override fun setMatchPublicId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId?) {
        matchPublicIds.remove(canonicalMatchId)?.let { previous ->
            publicMatches.remove(previous)
        }
        if (publicMatchId != null) {
            matchPublicIds[canonicalMatchId] = publicMatchId
            matches[canonicalMatchId]?.let { matchKey -> publicMatches[publicMatchId] = matchKey }
        }
    }

    /**
     * Provides access to the `SourceCaIdAllocator`, a component responsible for managing
     * the allocation of identifiers for SourceCa entities.
     *
     * @return An instance of `SourceCaIdAllocator` used for identifier allocation.
     */
    override fun sourceCaIdAllocator(): SourceCaIdAllocator = sourceCaIdAllocator

    /**
     * Checks if the source references associated with a given match key have changed.
     *
     * This method compares the existing source references for the specified match key with the provided
     * list of source references by generating a set of fingerprints for the new sources and comparing it
     * with the stored fingerprints. If no existing source references are found or if there are changes,
     * the method returns true. Otherwise, it returns false.
     *
     * @param matchKey The unique identifier for a match to locate its associated source references.
     * @param sources The list of new source references to be compared against the existing data.
     * @return True if the source references have changed or do not exist, false otherwise.
     */
    override fun sourceReferencesChanged(matchKey: Long, sources: List<SourceReference>): Boolean =
        sourceReferences[matchKey]?.let { existing ->
            existing.isEmpty() || existing != sources.map(::fingerprint).toSet()
        } ?: true

    /**
     * Associates a list of source references with a match key by storing their fingerprints as a set.
     *
     * @param matchKey The unique key identifying the match to associate the source references with.
     * @param sources The list of source references to associate with the given match key.
     */
    override fun rememberSourceReferences(matchKey: Long, sources: List<SourceReference>) {
        sourceReferences[matchKey] = sources.map(::fingerprint).toSet()
    }

    /**
     * Retrieves an `InningsEntity` associated with the given match ID and innings number.
     *
     * @param matchId The unique identifier of the match.
     * @param inningsNumber The specific innings number within the match.
     * @return The `InningsEntity` corresponding to the provided match ID and innings number,
     * or `null` if no such entity exists.
     */
    override fun findInnings(matchId: Long, inningsNumber: Int): InningsEntity? =
        innings[matchId to inningsNumber]

    /**
     * Retrieves whether a specific innings in a match was declared.
     *
     * @param matchId The unique identifier of the match.
     * @param inningsNumber The number corresponding to the innings in the match.
     * @return A Boolean indicating if the innings was declared, or null if the information is unavailable.
     */
    override fun findInningsDeclared(matchId: Long, inningsNumber: Int): Boolean? =
        inningsDeclared[matchId to inningsNumber]

    /**
     * Stores information about a specific innings in a match.
     *
     * @param matchId The unique identifier of the match.
     * @param inningsNumber The number representing the innings within the match (e.g., 1 for first innings).
     * @param innings The details of the innings to be stored.
     * @param declared A flag indicating whether the innings was declared closed.
     */
    override fun rememberInnings(matchId: Long, inningsNumber: Int, innings: InningsEntity, declared: Boolean) {
        val lookup = matchId to inningsNumber
        this.innings[lookup] = innings
        inningsDeclared[lookup] = declared
    }

    /**
     * Updates the declaration status of a specific innings in a match.
     *
     * @param matchId The unique identifier of the match.
     * @param inningsNumber The number of the innings within the match.
     * @param declared A boolean indicating whether the innings is declared (true) or not (false).
     */
    override fun setInningsDeclared(matchId: Long, inningsNumber: Int, declared: Boolean) {
        inningsDeclared[matchId to inningsNumber] = declared
    }

    /**
     * Retrieves the unique delivery key for the specified match, innings, and innings order.
     *
     * @param matchId The identifier of the match.
     * @param inningsId The identifier of the innings within the match.
     * @param inningsOrder The order of the delivery within the innings.
     * @return The unique delivery key if found, or null if no matching key exists.
     */
    override fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long? =
        deliveries[Triple(matchId, inningsId, inningsOrder)]

    /**
     * Stores a mapping for a delivery in the match context.
     *
     * @param matchId The unique identifier of the match.
     * @param inningsId The unique identifier of the innings within the match.
     * @param inningsOrder The order number of the innings (e.g., first innings, second innings).
     * @param deliveryKey The unique identifier for the delivery to be remembered.
     */
    override fun rememberDelivery(matchId: Long, inningsId: Long, inningsOrder: Int, deliveryKey: Long) {
        deliveries[Triple(matchId, inningsId, inningsOrder)] = deliveryKey
    }

    /**
     * Adds a person to a match with the specified role.
     *
     * @param matchId the unique identifier of the match
     * @param personId the unique identifier of the person
     * @param roleCode the role assigned to the person in the match
     * @return `true` if the person was successfully added to the match, `false` otherwise
     */
    override fun addMatchPerson(matchId: Long, personId: Long, roleCode: String): Boolean =
        matchPeople.add(Triple(matchId, personId, roleCode))

    /**
     * Adds a delivery fielder by associating a delivery ID, wicket ID, and person ID.
     *
     * @param deliveryId The unique identifier of the delivery.
     * @param wicketId The unique identifier of the wicket.
     * @param personId The unique identifier of the person acting as the fielder.
     * @return `true` if the fielder was successfully added; `false` otherwise.
     */
    override fun addDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long): Boolean =
        deliveryFielders.add(Triple(deliveryId, wicketId, personId))

    /**
     * Allocates a unique identity key based on the provided sequence type.
     *
     * @param sequence The type of sequence for which a new identity key is being generated.
     *                 Possible values include PERSON, TEAM, TEAM_SOURCE, GROUND, GROUND_SOURCE,
     *                 MATCH, MATCH_PERSON, INNINGS, DELIVERY, BALL_SOURCE, WICKET, WICKET_SOURCE.
     * @return A new instance of [IdentityKey] containing a unique identifier for the specified sequence.
     */
    override fun allocateKey(sequence: IdentitySequence): IdentityKey = IdentityKey(
        value = when (sequence) {
            IdentitySequence.PERSON -> nextPersonKey++
            IdentitySequence.TEAM -> nextTeamKey++
            IdentitySequence.TEAM_SOURCE -> nextTeamSourceId++
            IdentitySequence.GROUND -> nextGroundKey++
            IdentitySequence.GROUND_SOURCE -> nextGroundSourceId++
            IdentitySequence.MATCH -> nextMatchKey++
            IdentitySequence.MATCH_PERSON -> nextMatchPersonKey++
            IdentitySequence.INNINGS -> nextInningsKey++
            IdentitySequence.DELIVERY -> nextDeliveryKey++
            IdentitySequence.BALL_SOURCE -> nextBallSourceId++
            IdentitySequence.WICKET -> nextWicketKey++
            IdentitySequence.WICKET_SOURCE -> nextWicketSourceId++
        }
    )

    /**
     * Creates and returns a snapshot of the current state of the identity store,
     * capturing all key and source ID values.
     *
     * The snapshot contains the identifying information for various entities,
     * such as teams, grounds, balls, wickets, persons, and matches.
     * Transactions are initiated in the process to ensure state consistency.
     *
     * @return an IdentityStoreSnapshot instance containing the current state of all keys and source IDs.
     */
    override fun snapshot(): IdentityStoreSnapshot {
        beginTransactions()
        return Snapshot(
            nextTeamSourceId = nextTeamSourceId,
            nextGroundSourceId = nextGroundSourceId,
            nextBallSourceId = nextBallSourceId,
            nextWicketSourceId = nextWicketSourceId,
            nextPersonKey = nextPersonKey,
            nextTeamKey = nextTeamKey,
            nextGroundKey = nextGroundKey,
            nextMatchKey = nextMatchKey,
            nextMatchPersonKey = nextMatchPersonKey,
            nextInningsKey = nextInningsKey,
            nextDeliveryKey = nextDeliveryKey,
            nextWicketKey = nextWicketKey
        )
    }

    /**
     * Restores the state of the identity store from the provided snapshot.
     *
     * @param snapshot The snapshot object that contains the state to be restored. Must be an instance of `Snapshot`.
     * @throws IllegalArgumentException if the provided snapshot is not of type `Snapshot`.
     */
    override fun restore(snapshot: IdentityStoreSnapshot) {
        require(snapshot is Snapshot) { "Snapshot was created by a different identity store" }
        people.rollback()
        teams.rollback()
        grounds.rollback()
        dates.rollback()
        matches.rollback()
        matchPublicIds.rollback()
        publicMatches.rollback()
        sourceCaIdAllocator.rollback()
        sourceReferences.rollback()
        innings.rollback()
        inningsDeclared.rollback()
        deliveries.rollback()
        matchPeople.rollback()
        deliveryFielders.rollback()
        nextTeamSourceId = snapshot.nextTeamSourceId
        nextGroundSourceId = snapshot.nextGroundSourceId
        nextBallSourceId = snapshot.nextBallSourceId
        nextWicketSourceId = snapshot.nextWicketSourceId
        nextPersonKey = snapshot.nextPersonKey
        nextTeamKey = snapshot.nextTeamKey
        nextGroundKey = snapshot.nextGroundKey
        nextMatchKey = snapshot.nextMatchKey
        nextMatchPersonKey = snapshot.nextMatchPersonKey
        nextInningsKey = snapshot.nextInningsKey
        nextDeliveryKey = snapshot.nextDeliveryKey
        nextWicketKey = snapshot.nextWicketKey
    }

    /**
     * Commits all staged changes to the respective data repositories and finalizes
     * the allocation process for source context identifiers. This method ensures that
     * all entities and associations are persisted and updated as required.
     *
     * The following components are committed as part of this process:
     * - People and their respective data.
     * - Teams and their respective data.
     * - Grounds information.
     * - Match-related dates.
     * - Matches and their public identifiers.
     * - References to public matches.
     * - Source CA ID allocation is finalized.
     * - Source references are committed.
     * - Innings and innings declarations.
     * - Deliveries data.
     * - Match-people associations.
     * - Delivery-fielders associations.
     */
    override fun commit() {
        people.commit()
        teams.commit()
        grounds.commit()
        dates.commit()
        matches.commit()
        matchPublicIds.commit()
        publicMatches.commit()
        sourceCaIdAllocator.finish()
        sourceReferences.commit()
        innings.commit()
        inningsDeclared.commit()
        deliveries.commit()
        matchPeople.commit()
        deliveryFielders.commit()
    }

    /**
     * Initiates a series of database transactions by starting the transaction process
     * for multiple entities. This method is responsible for ensuring that all specified
     * transactional components are prepared to record changes as part of a single unit
     * of work, which can ultimately be committed or rolled back together.
     *
     * The method includes transactions for multiple components:
     * - People
     * - Teams
     * - Grounds
     * - Dates
     * - Matches
     * - Match public identifiers
     * - Public matches
     * - Source CA ID allocator
     * - Source references
     * - Innings
     * - Innings declared
     * - Deliveries
     * - Match people
     * - Delivery fielders
     *
     * This uniform initiation ensures the consistency of the application's state
     * across all the above-mentioned components.
     */
    private fun beginTransactions() {
        people.beginTransaction()
        teams.beginTransaction()
        grounds.beginTransaction()
        dates.beginTransaction()
        matches.beginTransaction()
        matchPublicIds.beginTransaction()
        publicMatches.beginTransaction()
        sourceCaIdAllocator.begin()
        sourceReferences.beginTransaction()
        innings.beginTransaction()
        inningsDeclared.beginTransaction()
        deliveries.beginTransaction()
        matchPeople.beginTransaction()
        deliveryFielders.beginTransaction()
    }

    /**
     * Represents a snapshot of the current state of identifiers used within the system.
     *
     * This data class is intended to store the incremental keys and source ids
     * for various entities such as teams, grounds, balls, wickets, and persons.
     *
     * The snapshot is utilized to ensure the consistency and synchronization of
     * entity identifiers, serving as a record of the latest state in the system.
     *
     * @constructor Creates a new Snapshot instance with specific values for all identifier fields.
     */
    private data class Snapshot(
        val nextTeamSourceId: Long,
        val nextGroundSourceId: Long,
        val nextBallSourceId: Long,
        val nextWicketSourceId: Long,
        val nextPersonKey: Long,
        val nextTeamKey: Long,
        val nextGroundKey: Long,
        val nextMatchKey: Long,
        val nextMatchPersonKey: Long,
        val nextInningsKey: Long,
        val nextDeliveryKey: Long,
        val nextWicketKey: Long
    ) : IdentityStoreSnapshot
}
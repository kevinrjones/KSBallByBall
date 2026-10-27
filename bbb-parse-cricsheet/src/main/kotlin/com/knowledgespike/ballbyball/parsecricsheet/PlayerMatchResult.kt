package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourcePersonId

/**
 * Represents the result of a player's participation in a match.
 *
 * This class encapsulates identifying information of a player from the source system,
 * their associated name, and optionally the corresponding player ID in the statistical system.
 *
 * @property sourcePersonId The unique identifier of the person in the source system.
 * @property sourceName The name of the player in the source system.
 * @property statsPlayerId The optional identifier of the player in the stats system, which maps to the corresponding player record if available.
 */
data class PlayerMatchResult(
    val sourcePersonId: SourcePersonId,
    val sourceName: String,
    val statsPlayerId: StatsPlayerId?
)

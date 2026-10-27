package com.knowledgespike.ballbyball.parsecricsheet

/**
 * Represents a player from the roster, including their unique identifier and various name components.
 *
 * @property playerId The unique identifier for the player.
 * @property fullName The full name of the player.
 * @property knownAs An optional alternate or commonly known name of the player.
 * @property surname An optional surname of the player.
 * @property expandedFullName An optional expanded full name of the player.
 */
data class StatsRosterPlayer(
    val playerId: StatsPlayerId,
    val fullName: String,
    val knownAs: String? = null,
    val surname: String? = null,
    val expandedFullName: String? = null
)

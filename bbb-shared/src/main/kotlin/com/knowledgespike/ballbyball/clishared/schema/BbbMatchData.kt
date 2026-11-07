package com.knowledgespike.ballbyball.clishared.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class BbbMatchData(
    val match: Info,
    val innings: List<Innings>
)

@Serializable
data class Info(
    val ballsPerOver: Int,
    val sourceCaId: String? = null,
    val bowlOut: List<BowlOut>? = null,
    val city: String? = null,
    val dates: List<String>,
    val event: Event? = null,
    val gender: String,
    val matchType: String,
    val matchTypeNumber: Int? = null,
    val missing: JsonElement? = null,
    val officials: Officials? = null,
    val outcome: Outcome,
    val overs: Int? = null,
    val playerOfMatch: List<String>? = null,
    val players: JsonObject,
    val registry: PlayersRegistry,
    val season: String,
    val superSubs: JsonObject? = null,
    val teamType: String,
    val teams: List<String>,
    val toss: Toss,
    val venue: String? = null
)

@Serializable
data class BowlOut(
    val bowler: String,
    val outcome: String
)

@Serializable
data class Event(
    val name: String,
    val matchNumber: Int? = null,
    val group: String? = null,
    val stage: String? = null
)

@Serializable
data class Officials(
    val matchReferees: List<String>? = null,
    val reserveUmpires: List<String>? = null,
    val tvUmpires: List<String>? = null,
    val umpires: List<String>? = null
)

@Serializable
data class Outcome(
    val by: By? = null,
    val bowlOut: String? = null,
    val eliminator: String? = null,
    val method: String? = null,
    val result: String? = null,
    val winner: String? = null
)

@Serializable
data class By(
    val innings: Int? = null,
    val runs: Int? = null,
    val wickets: Int? = null
)

@Serializable
data class PlayersRegistry(
    val people: Map<String, String>,
    val peopleCaId: Map<String, Int>? = null
)

@Serializable
data class Toss(
    val uncontested: Boolean? = null,
    val decision: String,
    val winner: String
)

@Serializable
data class Innings(
    val team: String,
    val overs: List<Over>? = null,
    val absentHurt: List<String>? = null,
    val penaltyRuns: PenaltyRuns? = null,
    val declared: Boolean? = null,
    val forfeited: Boolean? = null,
    val powerplays: List<PowerPlays>? = null,
    val miscountedOvers: JsonObject? = null,
    val target: Target? = null,
    val superOver: Boolean? = null
)

@Serializable
data class Over(
    val over: Int,
    val deliveries: List<Delivery>
)

@Serializable
data class PenaltyRuns(
    val pre: Int? = null,
    val post: Int? = null
)

@Serializable
data class Delivery(
    val batter: String,
    val bowler: String,
    val extras: Extras? = null,
    val nonStriker: String,
    val replacements: Replacements? = null,
    val review: Review? = null,
    val runs: Runs,
    val wickets: List<Wickets>? = null
)

@Serializable
data class PowerPlays(
    val from: String,
    val to: String,
    val type: String
)

@Serializable
data class Target(
    val overs: String? = null,
    val runs: Int? = null
)

@Serializable
data class Extras(
    val byes: Int? = null,
    val legbyes: Int? = null,
    val noballs: Int? = null,
    val penalty: Int? = null,
    val wides: Int? = null
)

@Serializable
data class Replacements(
    val match: List<Match>? = null,
    val role: List<Role>? = null
)

@Serializable
data class Match(
    val cameIn: String,
    val wentOut: String,
    val reason: String,
    val team: String
)

@Serializable
data class Role(
    val cameIn: String,
    val wentOut: String? = null,
    val reason: String,
    val role: String
)

@Serializable
data class Review(
    val batter: String,
    val by: String,
    val decision: String,
    val umpire: String? = null,
    val umpiresCall: Boolean? = null
)

@Serializable
data class Runs(
    val batter: Int,
    val extras: Int,
    val nonBoundary: Boolean? = null,
    val total: Int
)

@Serializable
data class Wickets(
    val fielders: List<Player>? = null,
    val kind: String,
    val playerOut: String
)

@Serializable
data class Player(
    val name: String? = null,
    val substitute: Boolean? = null
)
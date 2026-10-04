package com.knowledgespike.ballbyball.api.feature.matches.domain.model

import com.knowledgespike.ballbyball.contracts.ScoresheetCompleteness
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.Season

data class MatchScoresheetContext(
    val publicMatchId: PublicMatchId,
    val fileName: String,
    val matchType: MatchType?,
    val season: Season?,
    val competition: String?,
    val date: String?,
    val team1: String?,
    val team2: String?,
    val ground: String?,
    val result: String?
)

data class MatchScoresheetWicket(
    val wicketKey: Long,
    val kind: String?,
    val fielders: List<String>
)

data class MatchScoresheetDelivery(
    val deliveryKey: Long,
    val sourceBallId: Int,
    val inningsOrder: Int,
    val overNumber: Int,
    val ballNumber: Int,
    val ballInOver: Int,
    val batter: String?,
    val nonStriker: String?,
    val bowler: String?,
    val batterRuns: Int,
    val extraRuns: Int,
    val totalRuns: Int,
    val noBalls: Int,
    val wides: Int,
    val byes: Int,
    val legByes: Int,
    val nonBoundary: Int?,
    val powerplay: Int,
    val wicketCount: Int,
    val wickets: List<MatchScoresheetWicket>
)

data class MatchScoresheetInnings(
    val inningsNumber: Int,
    val battingTeam: String?,
    val bowlingTeam: String?,
    val declared: Boolean,
    val deliveries: List<MatchScoresheetDelivery>
)

data class MatchScoresheetPage(
    val context: MatchScoresheetContext,
    val completeness: ScoresheetCompleteness,
    val missingData: List<String>,
    val innings: List<MatchScoresheetInnings>
)
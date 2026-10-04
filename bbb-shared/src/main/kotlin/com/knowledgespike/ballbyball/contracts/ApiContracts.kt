package com.knowledgespike.ballbyball.contracts

import com.knowledgespike.ballbyball.types.values.ExactMatch
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.ballbyball.types.values.MatchResultFilter
import com.knowledgespike.ballbyball.types.values.MatchTypeFilter
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.PageNumber
import com.knowledgespike.ballbyball.types.values.PageSize
import com.knowledgespike.ballbyball.types.values.SearchDate
import com.knowledgespike.ballbyball.types.values.SearchTeam
import com.knowledgespike.ballbyball.types.values.Season
import com.knowledgespike.ballbyball.types.values.VenueFilter
import kotlinx.serialization.Serializable

@Serializable
data class ApiHealth(
    val status: String,
    val database: Boolean
)

@Serializable
data class HeartbeatResponse(
    val message: String
)

@Serializable
data class MatchSummary(
    val publicMatchId: PublicMatchId,
    val fileName: String,
    val matchType: MatchType,
    val season: Season,
    val competition: String? = null,
    val date: String? = null,
    val team1: String? = null,
    val score1: String? = null,
    val overs1: String? = null,
    val isTeam1Winner: Boolean = false,
    val team2: String? = null,
    val score2: String? = null,
    val overs2: String? = null,
    val isTeam2Winner: Boolean = false,
    val result: String? = null,
    val format: String? = null
) {
    companion object {
        fun of(
            publicMatchId: Long,
            fileName: String,
            matchType: String,
            season: String,
            competition: String? = null,
            date: String? = null,
            team1: String? = null,
            score1: String? = null,
            overs1: String? = null,
            isTeam1Winner: Boolean = false,
            team2: String? = null,
            score2: String? = null,
            overs2: String? = null,
            isTeam2Winner: Boolean = false,
            result: String? = null,
            format: String? = null
        ): MatchSummary = MatchSummary(
            PublicMatchId.from(publicMatchId),
            fileName,
            MatchType.from(matchType),
            Season.from(season),
            competition,
            date,
            team1,
            score1,
            overs1,
            isTeam1Winner,
            team2,
            score2,
            overs2,
            isTeam2Winner,
            result,
            format
        )
    }
}

@Serializable
data class RecentMatchesResponse(
    val matches: List<MatchSummary>
)

@Serializable
data class MatchSearchRequest(
    val team: SearchTeam,
    val teamExactMatch: ExactMatch,
    val opponents: SearchTeam,
    val opponentsExactMatch: ExactMatch,
    val venue: VenueFilter,
    val startDate: SearchDate?,
    val endDate: SearchDate?,
    val matchType: MatchTypeFilter,
    val matchResult: MatchResultFilter,
    val page: PageNumber = PageNumber.from(PageNumber.DEFAULT_VALUE),
    val pageSize: PageSize = PageSize.from(PageSize.DEFAULT_VALUE)
)

@Serializable
data class MatchSearchResult(
    val publicMatchId: PublicMatchId,
    val fileName: String,
    val matchType: MatchType?,
    val season: Season?,
    val competition: String? = null,
    val date: String? = null,
    val team1: String? = null,
    val team2: String? = null,
    val ground: String? = null,
    val result: String? = null
)

@Serializable
data class MatchSearchResponse(
    val matches: List<MatchSearchResult>,
    val pagination: MatchSearchPagination
)

@Serializable
data class MatchSearchPagination(
    val page: PageNumber,
    val pageSize: PageSize,
    val totalResults: Int,
    val hasNext: Boolean,
    val nextPage: PageNumber?
)

@Serializable
enum class ScoresheetCompleteness {
    COMPLETE,
    INCOMPLETE,
    EMPTY
}

@Serializable
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

@Serializable
data class ScoresheetWicket(
    val wicketKey: Long,
    val kind: String?,
    val fielders: List<String>
)

/**
 * A wicket is associated with its delivery. The warehouse contract does not
 * expose a separate dismissed-player field, so consumers must attribute a
 * wicket to the delivery's `batter`.
 */
@Serializable
data class ScoresheetDelivery(
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
    val wickets: List<ScoresheetWicket>
)

@Serializable
data class ScoresheetInnings(
    val inningsNumber: Int,
    val battingTeam: String?,
    val bowlingTeam: String?,
    val declared: Boolean,
    val deliveries: List<ScoresheetDelivery>
)

@Serializable
data class MatchScoresheetResponse(
    val context: MatchScoresheetContext,
    val completeness: ScoresheetCompleteness,
    val missingData: List<String>,
    val innings: List<ScoresheetInnings>
)

@Serializable
data class ApplicationMetadata(
    val dataLastUpdated: String,
    val applicationVersion: String
)

@Serializable
data class ApiError(
    val code: String,
    val message: String
)
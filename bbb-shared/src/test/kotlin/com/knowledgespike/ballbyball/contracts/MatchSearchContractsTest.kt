package com.knowledgespike.ballbyball.contracts

import arrow.core.Either
import com.knowledgespike.ballbyball.types.values.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

class MatchSearchContractsTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `card search input carries team filters and defaults page values`() {
        val team = SearchTeam.fromRaw("  South Africa  ") as Either.Right
        val request = MatchSearchRequest(
            team = team.value,
            teamExactMatch = ExactMatch.from(true),
            opponents = SearchTeam.from("India"),
            opponentsExactMatch = ExactMatch.from(false),
            venue = VenueFilter.from(VenueFilter.ALL),
            startDate = SearchDate.from("2024-01-01"),
            endDate = null,
            matchType = MatchTypeFilter.from(MatchTypeFilter.ALL),
            matchResult = MatchResultFilter.from(MatchResultFilter.ALL)
        )

        expectThat(request.team.value).isEqualTo("South Africa")
        expectThat(request.teamExactMatch.value).isEqualTo(true)
        expectThat(request.opponents.value).isEqualTo("India")
        expectThat(request.page.value).isEqualTo(PageNumber.DEFAULT_VALUE)
        expectThat(request.pageSize.value).isEqualTo(PageSize.DEFAULT_VALUE)
    }

    @Test
    fun `search input rejects blank malformed and over limit values`() {
        expectThat(SearchTeam.fromRaw("   ").isLeft()).isTrue()
        expectThat(PageNumber.fromRaw("0").isLeft()).isTrue()
        expectThat(PageNumber.fromRaw("10001").isLeft()).isTrue()
        expectThat(PageSize.fromRaw("51").isLeft()).isTrue()
        expectThat(PageSize.fromRaw("not-a-number").isLeft()).isTrue()
        expectThat(SearchDate.fromRaw("2024-13-01").isLeft()).isTrue()
        expectThat(SearchDateRange.fromRaw("2025-01-01", "2024-01-01").isLeft()).isTrue()
        expectThat(MatchTypeFilter.fromRaw("unsupported").isLeft()).isTrue()
        expectThat(VenueFilter.fromRaw("3").isLeft()).isTrue()
        expectThat(MatchResultFilter.fromRaw("12").isLeft()).isTrue()
    }

    @Test
    fun `search response serializes value classes as primitive values`() {
        val response = MatchSearchResponse(
            matches = listOf(
                MatchSearchResult(
                    publicMatchId = PublicMatchId.from(1_000_000_009),
                    fileName = "match.json",
                    matchType = null,
                    season = null,
                    team1 = "South Africa",
                    team2 = "India"
                )
            ),
            pagination = MatchSearchPagination(
                page = PageNumber.from(2),
                pageSize = PageSize.from(1),
                totalResults = 2,
                hasNext = false,
                nextPage = null
            )
        )

        val encoded = json.encodeToString(response)

        expectThat(encoded).isEqualTo(
            """{"matches":[{"publicMatchId":1000000009,"fileName":"match.json","matchType":null,"season":null,"competition":null,"date":null,"team1":"South Africa","team2":"India","ground":null,"result":null}],"pagination":{"page":2,"pageSize":1,"totalResults":2,"hasNext":false,"nextPage":null}}"""
        )
    }

    @Test
    fun `shared parser accumulates invalid values and rejects unsupported neutral venue`() {
        val result = parseMatchSearchRequest { parameter ->
            mapOf(
                "team" to "x",
                "teamExactMatch" to "not-a-boolean",
                "opponents" to "y",
                "opponentsExactMatch" to "false",
                "venue" to "4",
                "startDate" to "2025-02-01",
                "endDate" to "2025-01-01",
                "matchType" to "unsupported",
                "matchResult" to "99",
                "page" to "10001",
                "pageSize" to "51"
            )[parameter]
        }

        expectThat(result.isLeft()).isTrue()
        expectThat(result.leftOrNull()?.size).isEqualTo(9)
    }

    @Test
    fun `scoresheet parser validates the path key`() {
        val result = parseMatchScoresheetRequest { parameter ->
            mapOf("publicMatchId" to "not-a-key")[parameter]
        }

        expectThat(result.isLeft()).isTrue()
        expectThat(result.leftOrNull()?.size).isEqualTo(1)
    }

    @Test
    fun `scoresheet response serializes nullable context and nested wicket associations`() {
        val response = MatchScoresheetResponse(
            context = MatchScoresheetContext(
                publicMatchId = PublicMatchId.from(1_000_000_009),
                fileName = "match.json",
                matchType = null,
                season = null,
                competition = null,
                date = null,
                team1 = "South Africa",
                team2 = "India",
                ground = null,
                result = null
            ),
            completeness = ScoresheetCompleteness.INCOMPLETE,
            missingData = listOf("deliveries"),
            innings = listOf(
                ScoresheetInnings(
                    inningsNumber = 1,
                    battingTeam = "South Africa",
                    bowlingTeam = "India",
                    declared = false,
                    deliveries = listOf(
                        ScoresheetDelivery(
                            deliveryKey = 100,
                            sourceBallId = 1,
                            inningsOrder = 1,
                            overNumber = 0,
                            ballNumber = 1,
                            ballInOver = 1,
                            batter = "Batter",
                            nonStriker = "Non-striker",
                            bowler = "Bowler",
                            batterRuns = 0,
                            extraRuns = 0,
                            totalRuns = 0,
                            noBalls = 0,
                            wides = 0,
                            byes = 0,
                            legByes = 0,
                            nonBoundary = null,
                            powerplay = 0,
                            wicketCount = 1,
                            wickets = listOf(ScoresheetWicket(1, "caught", listOf("Fielder")))
                        )
                    )
                )
            )
        )

        val encoded = json.encodeToString(response)

        expectThat(encoded).contains("\"completeness\":\"INCOMPLETE\"")
        expectThat(encoded).contains("\"fielders\":[\"Fielder\"]")
        expectThat(encoded).contains("\"date\":null")
        expectThat(encoded.contains("pagination")).isEqualTo(false)
    }
}
package com.knowledgespike.ballbyball.clishared.schema

import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class TeamNamePolicyTest {
    @Test
    fun `given a female match when naming an ordinary team then append women`() {
        expectThat(TeamNamePolicy.nameForMatch("North Stars", sampleMatch("Other Cup")))
            .isEqualTo("North Stars Women")
    }

    @Test
    fun `given an exception competition or team when naming then preserve the team name`() {
        val competitionException = TeamNamePolicy.nameForMatch(
            "North Stars",
            sampleMatch("Charlotte Edwards Cup")
        )
        val teamException = TeamNamePolicy.nameForMatch("The Blaze", sampleMatch("Other Cup"))

        expectThat(competitionException).isEqualTo("North Stars")
        expectThat(teamException).isEqualTo("The Blaze")
    }

    private fun sampleMatch(eventName: String) = Info(
        ballsPerOver = 6,
        dates = listOf("2024-01-01"),
        event = Event(eventName),
        gender = "female",
        matchType = "T20",
        outcome = Outcome(),
        players = JsonObject(emptyMap()),
        registry = PlayersRegistry(emptyMap()),
        season = "2024",
        teamType = "club",
        teams = listOf("North Stars", "South Stars"),
        toss = Toss(decision = "bat", winner = "North Stars")
    )
}
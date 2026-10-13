package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.clishared.schema.Delivery
import com.knowledgespike.ballbyball.clishared.schema.Extras
import com.knowledgespike.ballbyball.clishared.schema.Runs
import com.knowledgespike.ballbyball.clishared.schema.Wickets
import com.knowledgespike.ballbyball.parse.database.adapter.DeliveryRecord
import com.knowledgespike.ballbyball.parse.database.adapter.MatchWriteDecision
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.ballbyball.parse.database.adapter.RelationalWriteSupport
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.cricketarchive.InvalidStateException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.LocalDateTime
import java.util.UUID

class RelationalWriteSupportTest {
    @Test
    fun `given canonical match identity when matching public id is found then existing key is reused`() {
        val canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
        val publicMatchId = PublicMatchId.from(1_234_567_890L)

        val decision = RelationalWriteSupport.matchWriteDecision(
            canonicalMatchId = canonicalMatchId,
            publicMatchId = publicMatchId,
            existingMatchKey = 17L,
            existingPublicMatchId = publicMatchId.value
        )

        expectThat(decision).isEqualTo(MatchWriteDecision.Reuse(17L))
    }

    @Test
    fun `given a public id owned by another canonical match when deciding a write then collision is rejected`() {
        val canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))

        assertThrows<InvalidStateException> {
            RelationalWriteSupport.matchWriteDecision(
                canonicalMatchId = canonicalMatchId,
                publicMatchId = PublicMatchId.from(1_234_567_890L),
                existingMatchKey = null,
                publicIdOwnedByAnotherMatch = true
            )
        }
    }

    @Test
    fun `given match record when converted to relational values then shared column order is preserved`() {
        val match = matchRecord()
        val createdAt = LocalDateTime.parse("2024-02-03T04:05:06")

        val values = RelationalWriteSupport.matchValues(17L, match, createdAt)

        expectThat(values).isEqualTo(
            listOf(
                17L,
                "1890a7a8-f76d-5f36-89f7-39b0319044b0",
                1_234_567_890L,
                "ca-12345",
                "match.json",
                2,
                "odi",
                "Cup",
                "2024-02-03",
                "2024",
                "2024",
                20240203,
                6,
                createdAt,
                21L,
                22L,
                23L,
                24L,
                "bat",
                "runs",
                25L,
                26L,
                0,
                0,
                1
            )
        )
    }

    @Test
    fun `given delivery record when converted to relational values then shared column order is preserved`() {
        val delivery = DeliveryRecord(
            matchKey = 17L,
            matchDateKey = 20240203,
            inningsKey = 31L,
            battingTeamKey = 21L,
            bowlingTeamKey = 22L,
            batterKey = 41L,
            nonStrikerKey = 42L,
            bowlerKey = 43L,
            overNumber = 4,
            ballNumber = 2,
            ballInOver = 2,
            inningsOrder = 20,
            delivery = Delivery(
                batter = "Batter",
                bowler = "Bowler",
                extras = Extras(noballs = 1, wides = 2, byes = 3, legbyes = 4),
                nonStriker = "Runner",
                runs = Runs(batter = 5, extras = 6, nonBoundary = true, total = 11),
                wickets = listOf(Wickets(kind = "bowled", playerOut = "Out"))
            ),
            powerplay = 1
        )

        val values = RelationalWriteSupport.deliveryValues(51L, 52L, delivery)

        val expected = listOf<Any?>(
            51L,
            52L,
            17L,
            20240203,
            31L,
            21L,
            22L,
            41L,
            42L,
            43L,
            4,
            2,
            2,
            20,
            5,
            6,
            11,
            1,
            2,
            3,
            4,
            1,
            1,
            1
        )
        expectThat(values).isEqualTo(expected)
    }

    private fun matchRecord() = MatchRecord(
        canonicalMatchId = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0")),
        publicMatchId = PublicMatchId.from(1_234_567_890L),
        sourceCaId = SourceCaId.from("ca-12345"),
        fileName = "match.json",
        matchInSeries = 2,
        matchType = "odi",
        eventName = "Cup",
        matchDateText = "2024-02-03",
        season = "2024",
        matchStartYear = "2024",
        matchStartDateKey = 20240203,
        ballsPerOver = 6,
        team1Key = 21L,
        team2Key = 22L,
        groundKey = 23L,
        tossTeamKey = 24L,
        tossDecision = "bat",
        victoryType = "runs",
        winnerTeamKey = 25L,
        loserTeamKey = 26L
    )
}
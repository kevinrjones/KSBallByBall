package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.parsecricsheet.source.By
import com.knowledgespike.ballbyball.parsecricsheet.source.CricSheet
import com.knowledgespike.ballbyball.parsecricsheet.source.Delivery
import com.knowledgespike.ballbyball.parsecricsheet.source.Event
import com.knowledgespike.ballbyball.parsecricsheet.source.Extras
import com.knowledgespike.ballbyball.parsecricsheet.source.Info
import com.knowledgespike.ballbyball.parsecricsheet.source.Innings
import com.knowledgespike.ballbyball.parsecricsheet.source.Match
import com.knowledgespike.ballbyball.parsecricsheet.source.Meta
import com.knowledgespike.ballbyball.parsecricsheet.source.Officials
import com.knowledgespike.ballbyball.parsecricsheet.source.Outcome
import com.knowledgespike.ballbyball.parsecricsheet.source.Over
import com.knowledgespike.ballbyball.parsecricsheet.source.PenaltyRuns
import com.knowledgespike.ballbyball.parsecricsheet.source.Player
import com.knowledgespike.ballbyball.parsecricsheet.source.PlayersRegistry
import com.knowledgespike.ballbyball.parsecricsheet.source.PowerPlays
import com.knowledgespike.ballbyball.parsecricsheet.source.Replacements
import com.knowledgespike.ballbyball.parsecricsheet.source.Review
import com.knowledgespike.ballbyball.parsecricsheet.source.Role
import com.knowledgespike.ballbyball.parsecricsheet.source.Runs
import com.knowledgespike.ballbyball.parsecricsheet.source.Target
import com.knowledgespike.ballbyball.parsecricsheet.source.Toss
import com.knowledgespike.ballbyball.parsecricsheet.source.Wickets
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class CricSheetConverterTest {
    private val converter = CricSheetConverter()

    @Test
    fun `given male and female match types when converted then warehouse prefixes are applied`() {
        expectThat(converter.convert(sample(gender = "male", matchType = "T20")).match.matchType)
            .isEqualTo("tt")
        expectThat(converter.convert(sample(gender = "female", matchType = "T20")).match.matchType)
            .isEqualTo("wtt")
    }

    @Test
    fun `given nested source data when converted then info innings and delivery fields are mapped`() {
        val source = sample()
        val converted = converter.convert(source)
        val delivery = converted.innings.single().overs?.single()?.deliveries?.single()

        expectThat(converted.match.ballsPerOver).isEqualTo(source.info.ballsPerOver)
        expectThat(converted.match.event?.matchNumber).isEqualTo(source.info.event?.matchNumber)
        expectThat(converted.match.officials?.matchReferees).isEqualTo(source.info.officials?.matchReferees)
        expectThat(converted.match.outcome.by?.runs).isEqualTo(source.info.outcome.by?.runs)
        expectThat(converted.match.players).isEqualTo(source.info.players)
        expectThat(converted.match.registry.people).isEqualTo(source.info.registry.people)
        expectThat(converted.innings.single().penaltyRuns?.pre).isEqualTo(5)
        expectThat(converted.innings.single().miscountedOvers).isEqualTo(source.innings.single().miscountedOvers)
        expectThat(delivery?.nonStriker).isEqualTo("Non Striker")
        expectThat(delivery?.extras?.wides).isEqualTo(1)
        expectThat(delivery?.replacements?.match?.single()?.cameIn).isEqualTo("Incoming Player")
        expectThat(delivery?.replacements?.match?.single()?.wentOut).isEqualTo("Outgoing Player")
        expectThat(delivery?.replacements?.role?.single()?.cameIn).isEqualTo("Substitute")
        expectThat(delivery?.review?.umpiresCall).isEqualTo(true)
        expectThat(delivery?.runs?.nonBoundary).isEqualTo(true)
        expectThat(delivery?.wickets?.single()?.fielders?.single()?.substitute).isEqualTo(true)
    }

    @Test
    fun `given an unknown root field when raw source is decoded then conversion succeeds`() {
        val source = Json.encodeToString(CricSheet.serializer(), sample())
        val sourceWithUnknownField = source.replaceFirst("{", "{\"future_field\":true,")

        val envelope = converter.convert(sourceWithUnknownField.encodeToByteArray(), "record-123")

        expectThat(envelope.match.match.matchType).isEqualTo("o")
    }

    @Test
    fun `given unknown source match type when converted then it is rejected`() {
        assertThrows<IllegalArgumentException> {
            converter.convert(sample(matchType = "unknown"))
        }
    }

    private fun sample(
        gender: String = "male",
        matchType: String = "ODI"
    ) = CricSheet(
        meta = Meta(dataVersion = "1.1.0", created = "2024-01-01T00:00:00Z", revision = 1),
        info = Info(
            ballsPerOver = 6,
            city = "London",
            dates = listOf("2024-01-01"),
            event = Event(name = "Cup", matchNumber = 12, group = "A", stage = "Final"),
            gender = gender,
            matchType = matchType,
            matchTypeNumber = 7,
            officials = Officials(matchReferees = listOf("Referee"), umpires = listOf("Umpire")),
            outcome = Outcome(by = By(innings = 1, runs = 5, wickets = 2), result = "runs", winner = "Side A"),
            overs = 50,
            playerOfMatch = listOf("Player of Match"),
            players = JsonObject(mapOf("Side A" to JsonArray(listOf(JsonPrimitive("Batter"))))),
            registry = PlayersRegistry(mapOf("player-id" to "Batter")),
            season = "2024",
            superSubs = JsonObject(mapOf("Side A" to JsonPrimitive("Substitute"))),
            teamType = "international",
            teams = listOf("Side A", "Side B"),
            toss = Toss(uncontested = false, decision = "bat", winner = "Side A"),
            venue = "Ground"
        ),
        innings = listOf(
            Innings(
                team = "Side A",
                overs = listOf(
                    Over(
                        over = 0,
                        deliveries = listOf(
                            Delivery(
                                batter = "Batter",
                                bowler = "Bowler",
                                extras = Extras(byes = 2, wides = 1),
                                nonStriker = "Non Striker",
                                replacements = Replacements(
                                    match = listOf(
                                        Match(
                                            cameIn = "Incoming Player",
                                            wentOut = "Outgoing Player",
                                            reason = "concussion",
                                            team = "Side A"
                                        )
                                    ),
                                    role = listOf(
                                        Role(
                                            cameIn = "Substitute",
                                            wentOut = "Replaced Substitute",
                                            reason = "tactical",
                                            role = "fielder"
                                        )
                                    )
                                ),
                                review = Review(
                                    batter = "Batter",
                                    by = "Side A",
                                    decision = "upheld",
                                    umpire = "Umpire",
                                    umpiresCall = true
                                ),
                                runs = Runs(batter = 1, extras = 2, nonBoundary = true, total = 3),
                                wickets = listOf(
                                    Wickets(
                                        fielders = listOf(Player(name = "Fielder", substitute = true)),
                                        kind = "caught",
                                        playerOut = "Batter"
                                    )
                                )
                            )
                        )
                    )
                ),
                absentHurt = listOf("Absent Player"),
                penaltyRuns = PenaltyRuns(pre = 5, post = 2),
                declared = false,
                forfeited = false,
                powerplays = listOf(PowerPlays(from = "0.1", to = "10.0", type = "mandatory")),
                miscountedOvers = JsonObject(mapOf("3" to JsonPrimitive(2))),
                target = Target(overs = "20", runs = 150),
                superOver = false
            )
        )
    )
}
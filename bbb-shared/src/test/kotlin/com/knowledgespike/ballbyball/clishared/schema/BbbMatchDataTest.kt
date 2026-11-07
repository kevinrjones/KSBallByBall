package com.knowledgespike.ballbyball.clishared.schema

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse

class BbbMatchDataTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `shared schema serializes Kotlin-compatible names and round trips`() {
        val original = sampleMatchData()

        val serialized = json.encodeToString(original)
        val decoded = json.decodeFromString<BbbMatchData>(serialized)

        expectThat(serialized).contains("\"matchType\":\"tt\"")
        expectThat(serialized).contains("\"nonStriker\":\"Batter Two\"")
        expectThat(serialized.contains("data_version")).isFalse()
        expectThat(serialized.contains("\"meta\"")).isEqualTo(false)
        expectThat(serialized.contains("\"info\"")).isEqualTo(false)
        expectThat(serialized).contains("\"match\"")
        expectThat(decoded).isEqualTo(original)
    }

    @Test
    fun `shared schema serializes peopleCaId in registry and round trips`() {
        val original = sampleMatchData().let { data ->
            data.copy(
                match = data.match.copy(
                    registry = PlayersRegistry(
                        people = mapOf("Batter One" to "person-1"),
                        peopleCaId = mapOf("Batter One" to 101)
                    )
                )
            )
        }

        val serialized = json.encodeToString(original)
        val decoded = json.decodeFromString<BbbMatchData>(serialized)

        expectThat(serialized).contains("\"peopleCaId\":{\"Batter One\":101}")
        expectThat(decoded).isEqualTo(original)
    }

    private fun sampleMatchData() = BbbMatchData(
        match = Info(
            ballsPerOver = 6,
            dates = listOf("2024-01-01"),
            gender = "male",
            matchType = "tt",
            outcome = Outcome(result = "draw"),
            players = JsonObject(
                mapOf("Team A" to JsonArray(listOf(JsonPrimitive("Batter One"), JsonPrimitive("Batter Two"))))
            ),
            registry = PlayersRegistry(people = mapOf("Batter One" to "person-1")),
            season = "2024",
            teamType = "international",
            teams = listOf("Team A", "Team B"),
            toss = Toss(decision = "bat", winner = "Team A")
        ),
        innings = listOf(
            Innings(
                team = "Team A",
                overs = listOf(
                    Over(
                        over = 0,
                        deliveries = listOf(
                            Delivery(
                                batter = "Batter One",
                                bowler = "Bowler One",
                                nonStriker = "Batter Two",
                                runs = Runs(batter = 1, extras = 0, total = 1)
                            )
                        )
                    )
                )
            )
        )
    )
}
package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

class PublicMatchIdTest {
    private val json = Json

    @Test
    fun `accepts the inclusive ten digit bounds`() {
        expectThat(PublicMatchId.of(1_000_000_000L).getOrNull()?.value).isEqualTo(1_000_000_000L)
        expectThat(PublicMatchId.of(9_999_999_999L).getOrNull()?.value).isEqualTo(9_999_999_999L)
    }

    @Test
    fun `rejects values outside the ten digit range`() {
        expectThat(PublicMatchId.of(999_999_999L) is Either.Left).isTrue()
        expectThat(PublicMatchId.of(10_000_000_000L) is Either.Left).isTrue()
    }

    @Test
    fun `rejects malformed textual values`() {
        expectThat(PublicMatchId.fromRaw(null) is Either.Left).isTrue()
        expectThat(PublicMatchId.fromRaw("100000000") is Either.Left).isTrue()
        expectThat(PublicMatchId.fromRaw("10000000000") is Either.Left).isTrue()
        expectThat(PublicMatchId.fromRaw("1_000_000_000") is Either.Left).isTrue()
        expectThat(PublicMatchId.fromRaw("0100000000") is Either.Left).isTrue()
    }

    @Test
    fun `serializes as the underlying number`() {
        val publicMatchId = PublicMatchId.from(1_234_567_890L)

        expectThat(json.encodeToString(publicMatchId)).isEqualTo("1234567890")
        expectThat(json.decodeFromString<PublicMatchId>("1234567890")).isEqualTo(publicMatchId)
    }
}

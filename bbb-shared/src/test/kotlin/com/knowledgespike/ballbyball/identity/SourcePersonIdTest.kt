package com.knowledgespike.ballbyball.identity

import arrow.core.Either
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

class SourcePersonIdTest {
    @Test
    fun `accepts valid identifiers within max length`() {
        val validIds = listOf("12345", "a", "A1-B2_C3", "a".repeat(50))

        validIds.forEach { rawId ->
            val result = SourcePersonId.of(rawId)
            expectThat(result is Either.Right).isTrue()
            expectThat(result.getOrNull()?.value).isEqualTo(rawId)
        }
    }

    @Test
    fun `rejects invalid identifiers`() {
        val invalidIds = listOf(
            null,
            "",
            "   ",
            "a".repeat(51)
        )

        invalidIds.forEach { rawId ->
            expectThat(SourcePersonId.of(rawId) is Either.Left).isTrue()
        }
    }
}

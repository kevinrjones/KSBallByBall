package com.knowledgespike.ballbyball.identity

import arrow.core.Either
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

class SourceCaIdTest {
    @Test
    fun `accepts valid identifiers within max length`() {
        val validIds = listOf("12345", "a", "A1-B2_C3", "12345678901234567890")

        validIds.forEach { rawId ->
            val result = SourceCaId.of(rawId)
            expectThat(result is Either.Right).isTrue()
            expectThat(result.getOrNull()?.value).isEqualTo(rawId)
        }
    }

    @Test
    fun `rejects invalid identifiers`() {
        val invalidIds = listOf(
            null,
            "",
            "123456789012345678901",
            "12 34",
            " 1234",
            "1234\n",
            "\t1234"
        )

        invalidIds.forEach { rawId ->
            expectThat(SourceCaId.of(rawId) is Either.Left).isTrue()
        }
    }
}

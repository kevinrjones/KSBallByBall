package com.knowledgespike.ballbyball.identity

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class SourcePersonIdTest {
    @Test
    fun `given a valid source person identifier when created then preserve its string value`() {
        val sourcePersonId = SourcePersonId.of("8903c73e").getOrNull()

        expectThat(sourcePersonId?.value).isEqualTo("8903c73e")
    }

    @Test
    fun `given blank or overlong source person identifiers when created then reject them`() {
        val blank = SourcePersonId.of(" ")
        val overlong = SourcePersonId.of("x".repeat(SourcePersonId.MAX_LENGTH + 1))

        expectThat(blank.getOrNull()).isEqualTo(null)
        expectThat(overlong.getOrNull()).isEqualTo(null)
    }
}

package com.knowledgespike.ballbyball.identity

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class SourceCaIdTest {
    @Test
    fun `given a valid source CA identifier when created then preserve its string value`() {
        val sourceCaId = SourceCaId.of("ca-12345").getOrNull()

        expectThat(sourceCaId?.value).isEqualTo("ca-12345")
    }

    @Test
    fun `given blank or overlong source CA identifiers when created then reject them`() {
        val blank = SourceCaId.of(" ")
        val overlong = SourceCaId.of("x".repeat(SourceCaId.MAX_LENGTH + 1))

        expectThat(blank.getOrNull()).isEqualTo(null)
        expectThat(overlong.getOrNull()).isEqualTo(null)
    }
}
package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class StatsTeamIdTest {
    @Test
    fun `given a positive id when created then retain the validated value`() {
        val id = StatsTeamId.of(42).getOrNull()

        expectThat(id?.value).isEqualTo(42)
    }

    @Test
    fun `given a non-positive id when created then reject it`() {
        expectThat(StatsTeamId.of(0).getOrNull()).isEqualTo(null)
        expectThat(StatsTeamId.of(-1).getOrNull()).isEqualTo(null)
    }
}
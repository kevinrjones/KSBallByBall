package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.LocalDate

class StatsMatchDateHeuristicTest {
    @Test
    fun `given a primary date then candidate dates are plus or minus one day then plus or minus two days`() {
        val primaryDate = LocalDate.parse("2024-02-01")

        expectThat(StatsMatchDateHeuristic.candidateDates(primaryDate)).isEqualTo(
            listOf(
                LocalDate.parse("2024-01-31"),
                LocalDate.parse("2024-02-02"),
                LocalDate.parse("2024-01-30"),
                LocalDate.parse("2024-02-03")
            )
        )
    }
}
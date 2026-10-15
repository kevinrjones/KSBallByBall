package com.knowledgespike.ballbyball.parsecricsheet

import java.time.LocalDate

object StatsMatchDateHeuristic {
    fun candidateDates(primary: LocalDate): List<LocalDate> =
        listOf(
            primary.minusDays(1),
            primary.plusDays(1),
            primary.minusDays(2),
            primary.plusDays(2)
        )
}
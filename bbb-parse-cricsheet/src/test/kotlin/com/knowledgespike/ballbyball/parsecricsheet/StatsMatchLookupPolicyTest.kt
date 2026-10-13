package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class StatsMatchLookupPolicyTest {
    private val policy = StatsMatchLookupPolicy()

    @Test
    fun `exact lookup uses both team names`() {
        val exactCandidate = policy.exactCandidate(
            firstTeamName = "Team A",
            secondTeamName = "Team B"
        )

        expectThat(exactCandidate).isEqualTo(
            matchCandidate(StatsTeamLookup.Name("Team A"), StatsTeamLookup.Name("Team B"))
        )
    }

    @Test
    fun `alias candidate retains exact names and groups each team's ids`() {
        val aliasCandidate = policy.aliasCandidate(
            firstTeamName = "Team A",
            secondTeamName = "Team B",
            firstAliases = listOf(
                StatsTeamLookup.Id(StatsTeamId.from(10)),
                StatsTeamLookup.Id(StatsTeamId.from(11))
            ),
            secondAliases = listOf(
                StatsTeamLookup.Id(StatsTeamId.from(20)),
                StatsTeamLookup.Id(StatsTeamId.from(21))
            )
        )

        expectThat(aliasCandidate).isEqualTo(
            matchCandidate(
                StatsTeamLookup.AnyOf(
                    listOf(
                        StatsTeamLookup.Name("Team A"),
                        StatsTeamLookup.IdSet(listOf(StatsTeamId.from(10), StatsTeamId.from(11)))
                    )
                ),
                StatsTeamLookup.AnyOf(
                    listOf(
                        StatsTeamLookup.Name("Team B"),
                        StatsTeamLookup.IdSet(listOf(StatsTeamId.from(20), StatsTeamId.from(21)))
                    )
                )
            )
        )
    }

    @Test
    fun `single alias on one team keeps the exact name for the other team`() {
        val aliasCandidate = policy.aliasCandidate(
            firstTeamName = "Team A",
            secondTeamName = "Team B",
            firstAliases = listOf(StatsTeamLookup.Id(StatsTeamId.from(10))),
            secondAliases = emptyList()
        )

        expectThat(aliasCandidate).isEqualTo(
            matchCandidate(
                StatsTeamLookup.AnyOf(
                    listOf(StatsTeamLookup.Name("Team A"), StatsTeamLookup.Id(StatsTeamId.from(10)))
                ),
                StatsTeamLookup.Name("Team B")
            )
        )
    }

    @Test
    fun `given no aliases when requesting an alias candidate then no second query is planned`() {
        expectThat(
            policy.aliasCandidate("Team A", "Team B", emptyList(), emptyList())
        ).isEqualTo(null)
    }

    private fun matchCandidate(firstTeam: StatsTeamLookup, secondTeam: StatsTeamLookup) =
        StatsMatchCandidate(firstTeam, secondTeam)
}
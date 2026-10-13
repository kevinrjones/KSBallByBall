package com.knowledgespike.ballbyball.parsecricsheet

data class StatsMatchCandidate(
    val firstTeam: StatsTeamLookup,
    val secondTeam: StatsTeamLookup
)

class StatsMatchLookupPolicy {
    fun exactCandidate(firstTeamName: String, secondTeamName: String): StatsMatchCandidate =
        StatsMatchCandidate(StatsTeamLookup.Name(firstTeamName), StatsTeamLookup.Name(secondTeamName))

    fun aliasCandidate(
        firstTeamName: String,
        secondTeamName: String,
        firstAliases: List<StatsTeamLookup.Id>,
        secondAliases: List<StatsTeamLookup.Id>
    ): StatsMatchCandidate? {
        if (firstAliases.isEmpty() && secondAliases.isEmpty()) return null
        return StatsMatchCandidate(
            teamLookup(firstTeamName, firstAliases),
            teamLookup(secondTeamName, secondAliases)
        )
    }

    private fun teamLookup(teamName: String, aliases: List<StatsTeamLookup.Id>): StatsTeamLookup {
        val ids = aliases.map(StatsTeamLookup.Id::value).distinct().sortedBy(StatsTeamId::value)
        if (ids.isEmpty()) return StatsTeamLookup.Name(teamName)
        val aliasLookup = if (ids.size == 1) StatsTeamLookup.Id(ids.single()) else StatsTeamLookup.IdSet(ids)
        return StatsTeamLookup.AnyOf(listOf(StatsTeamLookup.Name(teamName), aliasLookup))
    }
}
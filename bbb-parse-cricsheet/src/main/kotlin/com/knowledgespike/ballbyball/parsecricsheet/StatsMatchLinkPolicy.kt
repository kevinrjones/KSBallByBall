package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.clishared.schema.Info
import com.knowledgespike.ballbyball.clishared.policy.PolicyResourceLoader

object StatsMatchLinkPolicy {
    private val allSeasonExclusions = PolicyResourceLoader.loadLines(
        "policies/stats-all-season-exclusions.txt"
    ).toSet()
    private val seasonExclusions = PolicyResourceLoader.loadLines(
        "policies/stats-season-exclusions.txt"
    ).map(::seasonExclusion).toSet()

    fun shouldLink(match: Info): Boolean =
        match.event?.name !in allSeasonExclusions &&
            (match.event?.name to match.season) !in seasonExclusions

    private fun seasonExclusion(line: String): Pair<String, String> {
        val fields = line.split('\t')
        require(fields.size == 2) { "Invalid stats season exclusion: $line" }
        return fields[0] to fields[1]
    }
}
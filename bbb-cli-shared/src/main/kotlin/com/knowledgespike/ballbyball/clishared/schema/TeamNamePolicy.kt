package com.knowledgespike.ballbyball.clishared.schema

import com.knowledgespike.ballbyball.clishared.policy.PolicyResourceLoader

object TeamNamePolicy {
    private val femaleCompetitionExceptions =
        PolicyResourceLoader.loadLines("policies/female-competition-exceptions.txt").toSet()
    private val femaleTeamExceptions = PolicyResourceLoader.loadLines("policies/female-team-exceptions.txt").toSet()

    fun nameForMatch(teamName: String, match: Info): String =
        if (shouldAppendWomen(teamName, match)) "$teamName Women" else teamName

    private fun shouldAppendWomen(teamName: String, match: Info): Boolean =
        match.gender.equals("female", ignoreCase = true) &&
            match.event?.name !in femaleCompetitionExceptions &&
            teamName !in femaleTeamExceptions &&
            !teamName.endsWith(" Women")
}
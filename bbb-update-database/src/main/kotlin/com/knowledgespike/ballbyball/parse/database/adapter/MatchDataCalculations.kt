package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.schema.Outcome
import com.knowledgespike.ballbyball.clishared.schema.PowerPlays
import java.util.Locale

internal fun getNameParts(personName: String): Pair<String, String> {
    val fullName = if (personName.contains("(")) personName.substringBefore("(").trim() else personName
    if (!fullName.contains(" ")) return fullName.replace("'", "") to ""

    val parts = fullName.split(" ")
    val (sortNamePart, otherNamePart) = when {
        parts[0].uppercase(Locale.ROOT) == parts[0] -> {
            parts.drop(1).joinToString("").replace("'", "") to parts[0].trim()
        }

        parts[0] == "Lord" -> {
            parts.last().replace("'", "") to parts.drop(1).joinToString(" ")
        }

        parts[0] in setOf("Sir", "Earl", "Duke", "Nawab") && parts[1] == "of" -> {
            parts.drop(2).first().replace("'", "") to parts.dropLast(1).joinToString(" ")
        }

        else -> fullName.replace(" ", "").replace("'", "") to ""
    }
    return sortNamePart to otherNamePart
}

internal fun getMargin(outcome: Outcome): Int = outcome.by?.let { by ->
    by.runs ?: by.wickets ?: by.innings ?: 0
} ?: 0

internal fun getVictoryType(outcome: Outcome): String {
    outcome.result?.let { return it }
    val by = outcome.by ?: return "unknown"
    return when {
        by.innings != null -> "innings"
        by.wickets != null -> "wickets"
        by.runs != null -> "runs"
        else -> "unknown"
    }
}

internal fun calculatePowerplays(powerplays: List<PowerPlays>?, ballsPerOver: Int): List<PowerplayDetails> =
    powerplays.orEmpty().mapIndexed { index, powerplay ->
        PowerplayDetails(
            powerplayNumber = index + 1,
            from = calculateBallFromOver(powerplay.from, ballsPerOver),
            to = calculateBallFromOver(powerplay.to, ballsPerOver),
            type = powerplay.type
        )
    }

internal fun calculateBallFromOver(over: String, ballsPerOver: Int): Int {
    val parts = over.split(".")
    return parts[0].toInt() * ballsPerOver + parts[1].toInt()
}

internal data class PowerplayDetails(val powerplayNumber: Int, val from: Int, val to: Int, val type: String)
package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.schema.Outcome
import com.knowledgespike.ballbyball.clishared.schema.PowerPlays

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
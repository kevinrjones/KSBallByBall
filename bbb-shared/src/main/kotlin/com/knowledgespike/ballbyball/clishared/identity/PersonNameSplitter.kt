package com.knowledgespike.ballbyball.clishared.identity

import java.util.Locale

/**
 * Splits a person's display name into (sortNamePart, otherNamePart).
 * Handles titles ("Lord", "Sir", "Earl", "Duke", "Nawab of"), role suffixes like "(c)" / "(sub)",
 * and initials / uppercase forenames.
 */
fun getNameParts(personName: String): Pair<String, String> {
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

        parts[0] in setOf("Sir", "Earl", "Duke", "Nawab") && parts.size > 2 && parts[1] == "of" -> {
            parts.drop(2).first().replace("'", "") to parts.dropLast(1).joinToString(" ")
        }

        else -> fullName.replace(" ", "").replace("'", "") to ""
    }
    return sortNamePart to otherNamePart
}

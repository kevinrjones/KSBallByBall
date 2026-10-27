package com.knowledgespike.ballbyball.parsecricsheet

/**
 * Utility functions for calculating string distances and similarity metrics.
 */
object TextDistance {

    /**
     * Computes the Levenshtein distance between two input strings.
     * The Levenshtein distance is a metric for measuring the differences between two sequences, defined as
     * the minimum number of single-character edits (insertions, deletions, or substitutions) required
     * to change one string into the other.
     *
     * @param a The first input string.
     * @param b The second input string.
     * @return The computed Levenshtein distance as an integer.
     */
    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prevRow = IntArray(b.length + 1) { it }
        var currRow = IntArray(b.length + 1)

        for (i in a.indices) {
            currRow[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                currRow[j + 1] = minOf(
                    currRow[j] + 1,
                    prevRow[j + 1] + 1,
                    prevRow[j] + cost
                )
            }
            val temp = prevRow
            prevRow = currRow
            currRow = temp
        }
        return prevRow[b.length]
    }

    /**
     * Calculates the similarity ratio between two strings based on the Levenshtein distance.
     *
     * @param a the first string to compare
     * @param b the second string to compare
     * @return a Double value representing the similarity ratio between the two strings.
     *         The value ranges from 0.0 (completely different strings) to 1.0 (identical strings).
     */
    fun similarityRatio(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dist = levenshteinDistance(a, b)
        return (maxLen - dist).toDouble() / maxLen
    }
}

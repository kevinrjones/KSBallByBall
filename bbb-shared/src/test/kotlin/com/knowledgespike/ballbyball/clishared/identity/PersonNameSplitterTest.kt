package com.knowledgespike.ballbyball.clishared.identity

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.util.Locale

class PersonNameSplitterTest {
    @Test
    fun `given simple player name without spaces then returns sort name and empty other name`() {
        val (sort, other) = getNameParts("Kohli")
        expectThat(sort).isEqualTo("Kohli")
        expectThat(other).isEqualTo("")
    }

    @Test
    fun `given uppercase forename initials then splits into surname and initials`() {
        val (sort, other) = getNameParts("V Kohli")
        expectThat(sort).isEqualTo("Kohli")
        expectThat(other).isEqualTo("V")

        val (sort2, other2) = getNameParts("RG Sharma")
        expectThat(sort2).isEqualTo("Sharma")
        expectThat(other2).isEqualTo("RG")
    }

    @Test
    fun `given titles such as Lord or Sir of then splits accordingly`() {
        val (lordSort, lordOther) = getNameParts("Lord Martin Hawke")
        expectThat(lordSort).isEqualTo("Hawke")
        expectThat(lordOther).isEqualTo("Martin Hawke")

        val (nawabSort, nawabOther) = getNameParts("Nawab of Pataudi")
        expectThat(nawabSort).isEqualTo("Pataudi")
        expectThat(nawabOther).isEqualTo("Nawab of")
    }

    @Test
    fun `given parenthesized role suffix then trims role suffix before splitting`() {
        val (sort, other) = getNameParts("Bob Willis (sub)")
        expectThat(sort).isEqualTo("BobWillis")
        expectThat(other).isEqualTo("")

        val (initialSort, initialOther) = getNameParts("GS Sobers (c)")
        expectThat(initialOther).isEqualTo("GS")
        expectThat(initialSort).isEqualTo("Sobers")
    }

    @Test
    fun `given turkish locale when uppercase prefix checked then behaves consistently`() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            expectThat(getNameParts("INDIA Smith")).isEqualTo("Smith" to "INDIA")
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}

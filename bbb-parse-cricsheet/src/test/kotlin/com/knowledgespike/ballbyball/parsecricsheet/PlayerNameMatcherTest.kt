package com.knowledgespike.ballbyball.parsecricsheet

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class PlayerNameMatcherTest {

    private val viratKohli = StatsRosterPlayer(
        playerId = StatsPlayerId.from(1),
        fullName = "Virat Kohli",
        knownAs = "Virat Kohli",
        surname = "Kohli"
    )

    private val rohitSharma = StatsRosterPlayer(
        playerId = StatsPlayerId.from(2),
        fullName = "Rohit Gurunath Sharma",
        knownAs = "Rohit Sharma",
        surname = "Sharma"
    )

    private val steveSmith = StatsRosterPlayer(
        playerId = StatsPlayerId.from(3),
        fullName = "Steven Peter Devereux Smith",
        knownAs = "Steve Smith",
        surname = "Smith"
    )

    private val abDeVilliers = StatsRosterPlayer(
        playerId = StatsPlayerId.from(4),
        fullName = "Abraham Benjamin de Villiers",
        knownAs = "AB de Villiers",
        surname = "de Villiers"
    )

    private val fDuPlessis = StatsRosterPlayer(
        playerId = StatsPlayerId.from(5),
        fullName = "Francois du Plessis",
        knownAs = "Faf du Plessis",
        surname = "du Plessis"
    )

    private val jeanPaulDuminy = StatsRosterPlayer(
        playerId = StatsPlayerId.from(6),
        fullName = "Jean-Paul Duminy",
        knownAs = "JP Duminy",
        surname = "Duminy"
    )

    private val mitchellMarsh = StatsRosterPlayer(
        playerId = StatsPlayerId.from(7),
        fullName = "Mitchell Ross Marsh",
        knownAs = "Mitch Marsh",
        surname = "Marsh"
    )

    private val shaunMarsh = StatsRosterPlayer(
        playerId = StatsPlayerId.from(8),
        fullName = "Shaun Edward Marsh",
        knownAs = "Shaun Marsh",
        surname = "Marsh"
    )

    private val testRoster = listOf(
        viratKohli,
        rohitSharma,
        steveSmith,
        abDeVilliers,
        fDuPlessis,
        jeanPaulDuminy,
        mitchellMarsh,
        shaunMarsh
    )

    @Test
    fun `given exact full name when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("Virat Kohli", testRoster)

        expectThat(result).isNotNull().isEqualTo(viratKohli)
    }

    @Test
    fun `given exact knownAs name when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("Steve Smith", testRoster)

        expectThat(result).isNotNull().isEqualTo(steveSmith)
    }

    @Test
    fun `given case differences and punctuation when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("  v.   kohli.  ", testRoster)

        expectThat(result).isNotNull().isEqualTo(viratKohli)
    }

    @Test
    fun `given initials with dots and full surname when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("R.G. Sharma", testRoster)

        expectThat(result).isNotNull().isEqualTo(rohitSharma)
    }

    @Test
    fun `given single initial and full surname when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("R Sharma", testRoster)

        expectThat(result).isNotNull().isEqualTo(rohitSharma)
    }

    @Test
    fun `given multi-part initials without dots when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("SPD Smith", testRoster)

        expectThat(result).isNotNull().isEqualTo(steveSmith)
    }

    @Test
    fun `given accented characters when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("François du Plessis", testRoster)

        expectThat(result).isNotNull().isEqualTo(fDuPlessis)
    }

    @Test
    fun `given compound surname and initials when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("AB de Villiers", testRoster)

        expectThat(result).isNotNull().isEqualTo(abDeVilliers)
    }

    @Test
    fun `given hyphenated name and initials when matching then matches correctly`() {
        val result = PlayerNameMatcher.matchPlayer("JP Duminy", testRoster)

        expectThat(result).isNotNull().isEqualTo(jeanPaulDuminy)
    }

    @Test
    fun `given distinct initials for players sharing surname when matching then matches unique player`() {
        val mitch = PlayerNameMatcher.matchPlayer("MR Marsh", testRoster)
        val shaun = PlayerNameMatcher.matchPlayer("SE Marsh", testRoster)

        expectThat(mitch).isNotNull().isEqualTo(mitchellMarsh)
        expectThat(shaun).isNotNull().isEqualTo(shaunMarsh)
    }

    @Test
    fun `given ambiguous initial for players sharing surname when matching then returns null`() {
        // If there's Michael Marsh and Mitchell Marsh both matching 'M Marsh'
        val michaelMarsh = StatsRosterPlayer(
            playerId = StatsPlayerId.from(9),
            fullName = "Michael Marsh",
            surname = "Marsh"
        )
        val rosterWithAmbiguity = testRoster + michaelMarsh

        val result = PlayerNameMatcher.matchPlayer("M Marsh", rosterWithAmbiguity)

        expectThat(result).isNull()
    }

    @Test
    fun `given unmatched player name when matching then returns null`() {
        val result = PlayerNameMatcher.matchPlayer("Ben Stokes", testRoster)

        expectThat(result).isNull()
    }

    @Test
    fun `given blank name or empty roster when matching then returns null`() {
        expectThat(PlayerNameMatcher.matchPlayer("", testRoster)).isNull()
        expectThat(PlayerNameMatcher.matchPlayer("   ", testRoster)).isNull()
        expectThat(PlayerNameMatcher.matchPlayer("Virat Kohli", emptyList())).isNull()
    }
}

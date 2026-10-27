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

    private val aAliBaig = StatsRosterPlayer(
        playerId = StatsPlayerId.from(9),
        fullName = "A Ali Baig",
        knownAs = "A Ali Baig",
        surname = "Baig"
    )

    private val ranjanMadugalle = StatsRosterPlayer(
        playerId = StatsPlayerId.from(10),
        fullName = "Ranjan S Madugalle",
        knownAs = "RS Madugalle",
        surname = "Madugalle"
    )

    private val ananthapadmanabhan = StatsRosterPlayer(
        playerId = StatsPlayerId.from(11),
        fullName = "KN Ananthapadmanabhan",
        knownAs = "KN Ananthapadmanabhan",
        surname = "Ananthapadmanabhan"
    )

    private val aVaradharajan = StatsRosterPlayer(
        playerId = StatsPlayerId.from(12),
        fullName = "A Varadharajan",
        knownAs = "A Varadharajan",
        surname = "Varadharajan"
    )

    private val mohammadShami = StatsRosterPlayer(
        playerId = StatsPlayerId.from(13),
        fullName = "Mohammad Shami",
        knownAs = "Mohammad Shami",
        surname = "Shami"
    )

    private val rBishnoy = StatsRosterPlayer(
        playerId = StatsPlayerId.from(14),
        fullName = "R Bishnoy",
        knownAs = "Ravi Bishnoy",
        surname = "Bishnoy"
    )

    private val uKasim = StatsRosterPlayer(
        playerId = StatsPlayerId.from(15),
        fullName = "U Kasim",
        knownAs = "Usman Kasim",
        surname = "Kasim"
    )

    private val aNortje = StatsRosterPlayer(
        playerId = StatsPlayerId.from(16),
        fullName = "Anrich Arno Nortje",
        knownAs = "A Nortje",
        surname = "Nortje"
    )

    private val aChowdhury = StatsRosterPlayer(
        playerId = StatsPlayerId.from(17),
        fullName = "A Chowdhury",
        knownAs = "A Chowdhury",
        surname = "Chowdhury"
    )

    private val testRoster = listOf(
        viratKohli,
        rohitSharma,
        steveSmith,
        abDeVilliers,
        fDuPlessis,
        jeanPaulDuminy,
        mitchellMarsh,
        shaunMarsh,
        aAliBaig,
        ranjanMadugalle,
        ananthapadmanabhan,
        aVaradharajan,
        mohammadShami,
        rBishnoy,
        uKasim,
        aNortje,
        aChowdhury
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
    fun `given compact initials when matching against partial name then matches correctly`() {
        val aaBaigMatch = PlayerNameMatcher.matchPlayer("AA Baig", testRoster)
        expectThat(aaBaigMatch).isNotNull().isEqualTo(aAliBaig)

        val aBaigMatch = PlayerNameMatcher.matchPlayer("A Baig", testRoster)
        expectThat(aBaigMatch).isNotNull().isEqualTo(aAliBaig)

        val abbasAliBaigMatch = PlayerNameMatcher.matchPlayer("Abbas Ali Baig", testRoster)
        expectThat(abbasAliBaigMatch).isNotNull().isEqualTo(aAliBaig)

        val aliBaigMatch = PlayerNameMatcher.matchPlayer("Ali Baig", testRoster)
        expectThat(aliBaigMatch).isNotNull().isEqualTo(aAliBaig)
    }

    @Test
    fun `given umpire or referee name variations when matching then matches correctly`() {
        val refereeMatch = PlayerNameMatcher.matchPlayer("RS Madugalle", testRoster)
        expectThat(refereeMatch).isNotNull().isEqualTo(ranjanMadugalle)

        val fullRefereeMatch = PlayerNameMatcher.matchPlayer("Ranjan Madugalle", testRoster)
        expectThat(fullRefereeMatch).isNotNull().isEqualTo(ranjanMadugalle)

        val reserveUmpireMatch = PlayerNameMatcher.matchPlayer("K.N. Ananthapadmanabhan", testRoster)
        expectThat(reserveUmpireMatch).isNotNull().isEqualTo(ananthapadmanabhan)
    }

    @Test
    fun `given spelling or transliteration variation for Aaditya Varadarajan when matching then matches A Varadharajan`() {
        val resultFull = PlayerNameMatcher.matchPlayer("Aaditya Varadarajan", testRoster)
        expectThat(resultFull).isNotNull().isEqualTo(aVaradharajan)

        val resultInitial = PlayerNameMatcher.matchPlayer("A. Varadarajan", testRoster)
        expectThat(resultInitial).isNotNull().isEqualTo(aVaradharajan)

        val resultSurnameOnly = PlayerNameMatcher.matchPlayer("Varadarajan", testRoster)
        expectThat(resultSurnameOnly).isNotNull().isEqualTo(aVaradharajan)
    }

    @Test
    fun `given inverted or surname-first candidate name when matching then matches correctly`() {
        val invertedRoster = listOf(
            StatsRosterPlayer(
                playerId = StatsPlayerId.from(12),
                fullName = "Varadharajan, A",
                surname = "Varadharajan"
            )
        )
        val result = PlayerNameMatcher.matchPlayer("Aaditya Varadarajan", invertedRoster)
        expectThat(result).isNotNull()
        expectThat(result?.playerId?.value).isEqualTo(12)

        val singleTokenRoster = listOf(
            StatsRosterPlayer(
                playerId = StatsPlayerId.from(12),
                fullName = "Varadharajan",
                surname = "Varadharajan"
            )
        )
        val singleTokenResult = PlayerNameMatcher.matchPlayer("Aaditya Varadarajan", singleTokenRoster)
        expectThat(singleTokenResult).isNotNull()
        expectThat(singleTokenResult?.playerId?.value).isEqualTo(12)
    }

    @Test
    fun `given common transliteration variants when matching then matches correctly`() {
        val shamiMatch = PlayerNameMatcher.matchPlayer("Mohammed Shami", testRoster)
        expectThat(shamiMatch).isNotNull().isEqualTo(mohammadShami)

        val bishnoiMatch = PlayerNameMatcher.matchPlayer("Ravi Bishnoi", testRoster)
        expectThat(bishnoiMatch).isNotNull().isEqualTo(rBishnoy)

        val qasimMatch = PlayerNameMatcher.matchPlayer("Usman Qasim", testRoster)
        expectThat(qasimMatch).isNotNull().isEqualTo(uKasim)

        val nortjeMatch = PlayerNameMatcher.matchPlayer("Anrich Nortje", testRoster)
        expectThat(nortjeMatch).isNotNull().isEqualTo(aNortje)

        val choudharyMatch = PlayerNameMatcher.matchPlayer("A Choudhary", testRoster)
        expectThat(choudharyMatch).isNotNull().isEqualTo(aChowdhury)
    }

    @Test
    fun `given short surname difference when matching then strictly rejects to avoid false positives`() {
        val royRoster = listOf(
            StatsRosterPlayer(
                playerId = StatsPlayerId.from(50),
                fullName = "A Ray",
                surname = "Ray"
            )
        )
        val result = PlayerNameMatcher.matchPlayer("A Roy", royRoster)
        expectThat(result).isNull()
    }

    @Test
    fun `given initials mismatch with fuzzy surname when matching then strictly rejects`() {
        val result = PlayerNameMatcher.matchPlayer("K Varadarajan", testRoster)
        expectThat(result).isNull()
    }

    @Test
    fun `given multiple ambiguous fuzzy candidates in roster when matching then returns null`() {
        val ambiguousRoster = listOf(
            StatsRosterPlayer(
                playerId = StatsPlayerId.from(60),
                fullName = "A Varadharajan",
                surname = "Varadharajan"
            ),
            StatsRosterPlayer(
                playerId = StatsPlayerId.from(61),
                fullName = "A Varadarajen",
                surname = "Varadarajen"
            )
        )
        val result = PlayerNameMatcher.matchPlayer("Aaditya Varadarajan", ambiguousRoster)
        expectThat(result).isNull()
    }

    @Test
    fun `given pure string distance calculations when evaluated then computes correctly`() {
        expectThat(PlayerNameMatcher.levenshteinDistance("", "")).isEqualTo(0)
        expectThat(PlayerNameMatcher.levenshteinDistance("abc", "")).isEqualTo(3)
        expectThat(PlayerNameMatcher.levenshteinDistance("", "xyz")).isEqualTo(3)
        expectThat(PlayerNameMatcher.levenshteinDistance("varadarajan", "varadharajan")).isEqualTo(1)
        expectThat(PlayerNameMatcher.levenshteinDistance("shami", "shami")).isEqualTo(0)

        expectThat(PlayerNameMatcher.similarityRatio("", "")).isEqualTo(1.0)
        expectThat(PlayerNameMatcher.similarityRatio("varadarajan", "varadharajan")).isEqualTo(11.0 / 12.0)

        expectThat(PlayerNameMatcher.normalizeTransliteration("Varadharajan")).isEqualTo("varadarajan")
        expectThat(PlayerNameMatcher.normalizeTransliteration("Qasim")).isEqualTo("kasim")
        expectThat(PlayerNameMatcher.normalizeTransliteration("Bishnoy")).isEqualTo("bisnoi")
        expectThat(PlayerNameMatcher.normalizeTransliteration("Mohammed")).isEqualTo("mohamed")

        expectThat(PlayerNameMatcher.isSurnameFuzzyMatch("varadarajan", "varadharajan")).isEqualTo(true)
        expectThat(PlayerNameMatcher.isSurnameFuzzyMatch("roy", "ray")).isEqualTo(false)
        expectThat(PlayerNameMatcher.isSurnamePhoneticMatch("qasim", "kasim")).isEqualTo(true)
    }

    @Test
    fun `given Mehedi Maruf and candidate Mehedi Hasan when matchCloseEnough evaluated then matches correctly`() {
        val mehediHasan = StatsRosterPlayer(
            playerId = StatsPlayerId.from(201),
            fullName = "Mehedi Hasan",
            knownAs = "Mehedi Hasan",
            surname = "Hasan"
        )
        val roster = listOf(mehediHasan)

        val result = PlayerNameMatcher.matchCloseEnough("Mehedi Maruf", roster)
        expectThat(result).isNotNull().isEqualTo(mehediHasan)
    }

    @Test
    fun `given Mehedi Maruf and candidate Mehdi Hasan with MehdiHasan sort name when matchCloseEnough evaluated then matches correctly`() {
        val mehdiHasan = StatsRosterPlayer(
            playerId = StatsPlayerId.from(94254),
            fullName = "Mehdi Hasan",
            knownAs = null,
            surname = "MehdiHasan"
        )
        val roster = listOf(mehdiHasan)

        val result = PlayerNameMatcher.matchCloseEnough("Mehedi Maruf", roster)
        expectThat(result).isNotNull().isEqualTo(mehdiHasan)
    }

    @Test
    fun `given camelCase sort parts when normalized then splits into individual words`() {
        expectThat(PlayerNameMatcher.normalize("MehdiHasan")).isEqualTo("mehdi hasan")
        expectThat(PlayerNameMatcher.normalize("ShakibAlHasan")).isEqualTo("shakib al hasan")
        expectThat(PlayerNameMatcher.normalize("tenDoeschate")).isEqualTo("ten doeschate")
        expectThat(PlayerNameMatcher.normalize("MashrafeMortaza")).isEqualTo("mashrafe mortaza")
    }

    @Test
    fun `given D Shepherd and candidate DJ Shepard when matchCloseEnough evaluated then matches correctly`() {
        val djShepard = StatsRosterPlayer(
            playerId = StatsPlayerId.from(202),
            fullName = "DJ Shepard",
            knownAs = "DJ Shepard",
            surname = "Shepard"
        )
        val roster = listOf(djShepard)

        val result = PlayerNameMatcher.matchCloseEnough("D Shepherd", roster)
        expectThat(result).isNotNull().isEqualTo(djShepard)
    }

    @Test
    fun `given completely different player when matchCloseEnough evaluated then returns null`() {
        val djShepard = StatsRosterPlayer(
            playerId = StatsPlayerId.from(202),
            fullName = "DJ Shepard",
            knownAs = "DJ Shepard",
            surname = "Shepard"
        )
        val roster = listOf(djShepard)

        val result = PlayerNameMatcher.matchCloseEnough("Virat Kohli", roster)
        expectThat(result).isNull()
    }

    @Test
    fun `given short surname mismatch when matchCloseEnough evaluated then rejects`() {
        val ray = StatsRosterPlayer(
            playerId = StatsPlayerId.from(203),
            fullName = "A Ray",
            surname = "Ray"
        )
        val roster = listOf(ray)

        val result = PlayerNameMatcher.matchCloseEnough("A Roy", roster)
        expectThat(result).isNull()
    }

    @Test
    fun `given initial mismatch when matchCloseEnough evaluated then rejects`() {
        val aVaradharajan = StatsRosterPlayer(
            playerId = StatsPlayerId.from(204),
            fullName = "A Varadharajan",
            surname = "Varadharajan"
        )
        val roster = listOf(aVaradharajan)

        val result = PlayerNameMatcher.matchCloseEnough("K Varadarajan", roster)
        expectThat(result).isNull()
    }

    @Test
    fun `given multi-initial candidate when matching source single initial with same surname then matches correctly`() {
        val tplndPerera = StatsRosterPlayer(
            playerId = StatsPlayerId.from(301),
            fullName = "TPLND Perera",
            surname = "Perera"
        )
        val dlsCroospulle = StatsRosterPlayer(
            playerId = StatsPlayerId.from(302),
            fullName = "DLS Croospulle",
            surname = "Croospulle"
        )
        val isMclelland = StatsRosterPlayer(
            playerId = StatsPlayerId.from(303),
            fullName = "IS McLelland",
            surname = "McLelland"
        )
        val lmdMadushanka = StatsRosterPlayer(
            playerId = StatsPlayerId.from(304),
            fullName = "LMD Madushanka",
            surname = "Madushanka"
        )
        val mkstDeLivera = StatsRosterPlayer(
            playerId = StatsPlayerId.from(305),
            fullName = "MKST de Livera",
            surname = "de Livera"
        )
        val agdsjBandara = StatsRosterPlayer(
            playerId = StatsPlayerId.from(306),
            fullName = "AGDSJ Bandara",
            surname = "Bandara"
        )
        val stNdwandwa = StatsRosterPlayer(
            playerId = StatsPlayerId.from(307),
            fullName = "ST Ndwandwa",
            surname = "Ndwandwa"
        )

        val roster = listOf(tplndPerera, dlsCroospulle, isMclelland, lmdMadushanka, mkstDeLivera, agdsjBandara, stNdwandwa)

        expectThat(PlayerNameMatcher.matchPlayer("N Perera", roster)).isNotNull().isEqualTo(tplndPerera)
        expectThat(PlayerNameMatcher.matchPlayer("L Croospulle", roster)).isNotNull().isEqualTo(dlsCroospulle)
        expectThat(PlayerNameMatcher.matchPlayer("S McLelland", roster)).isNotNull().isEqualTo(isMclelland)
        expectThat(PlayerNameMatcher.matchPlayer("D Madushanka", roster)).isNotNull().isEqualTo(lmdMadushanka)
        expectThat(PlayerNameMatcher.matchPlayer("S de Livera", roster)).isNotNull().isEqualTo(mkstDeLivera)
        expectThat(PlayerNameMatcher.matchPlayer("Dinusha Bandara", roster)).isNotNull().isEqualTo(agdsjBandara)
        expectThat(PlayerNameMatcher.matchPlayer("Tsepo Ndwandwa", roster)).isNotNull().isEqualTo(stNdwandwa)
    }

    @Test
    fun `given Qazi Onik and candidate Kazi Anik when matchCloseEnough evaluated then matches correctly`() {
        val kaziAnik = StatsRosterPlayer(
            playerId = StatsPlayerId.from(310),
            fullName = "Kazi Anik",
            surname = "KaziAnik"
        )
        val roster = listOf(kaziAnik)

        val result = PlayerNameMatcher.matchCloseEnough("Qazi Onik", roster)
        expectThat(result).isNotNull().isEqualTo(kaziAnik)
    }

    @Test
    fun `given substitute fielders or unique surnames with different initials when matchCloseEnough evaluated then matches correctly`() {
        val hjMorton = StatsRosterPlayer(
            playerId = StatsPlayerId.from(320),
            fullName = "HJ Morton",
            surname = "Morton"
        )
        val gsVirdi = StatsRosterPlayer(
            playerId = StatsPlayerId.from(321),
            fullName = "GS Virdi",
            surname = "Virdi"
        )
        val tahirBaig = StatsRosterPlayer(
            playerId = StatsPlayerId.from(322),
            fullName = "Tahir Baig",
            surname = "TahirBaig"
        )
        val meherobHasan = StatsRosterPlayer(
            playerId = StatsPlayerId.from(323),
            fullName = "Meherob Hasan",
            surname = "MeherobHasan"
        )

        expectThat(PlayerNameMatcher.matchCloseEnough("D Morton", listOf(hjMorton))).isNotNull().isEqualTo(hjMorton)
        expectThat(PlayerNameMatcher.matchCloseEnough("A Virdi", listOf(gsVirdi))).isNotNull().isEqualTo(gsVirdi)
        expectThat(PlayerNameMatcher.matchCloseEnough("Mirza Baig", listOf(tahirBaig))).isNotNull().isEqualTo(tahirBaig)
        expectThat(PlayerNameMatcher.matchCloseEnough("SM Meherob", listOf(meherobHasan))).isNotNull().isEqualTo(meherobHasan)
    }

    @Test
    fun `given player with expanded full name when matching across tiers then resolves accurately`() {
        val malinga = StatsRosterPlayer(
            playerId = StatsPlayerId.from(330),
            fullName = "KKEM Dharmasena",
            surname = "Dharmasena",
            expandedFullName = "Kiribathgala Kankanamalage Eshan Malinga Dharmasena"
        )
        val vidyut = StatsRosterPlayer(
            playerId = StatsPlayerId.from(331),
            fullName = "V Sivaramakrishnan",
            surname = "Sivaramakrishnan",
            expandedFullName = "Vidyut Sivaramakrishnan"
        )
        val ahsanMalik = StatsRosterPlayer(
            playerId = StatsPlayerId.from(332),
            fullName = "MAA Jamil",
            surname = "Jamil",
            expandedFullName = "Malik Ahsan Ahmad Jamil"
        )
        val jagannathan = StatsRosterPlayer(
            playerId = StatsPlayerId.from(333),
            fullName = "SJ Kannan",
            surname = "Kannan",
            expandedFullName = "Kannan Sudarsanam Jagannathan"
        )
        val ushettige = StatsRosterPlayer(
            playerId = StatsPlayerId.from(334),
            fullName = "UDP Madawa",
            surname = "Madawa",
            expandedFullName = "Ushettige Don Pasindu Madawa"
        )
        val gimhan = StatsRosterPlayer(
            playerId = StatsPlayerId.from(335),
            fullName = "SG Liyanage",
            surname = "Liyanage",
            expandedFullName = "Sithara Gimhan Liyanage"
        )
        val lakshan = StatsRosterPlayer(
            playerId = StatsPlayerId.from(336),
            fullName = "WUL Kurera",
            surname = "Kurera",
            expandedFullName = "Warnakulasooriya Umesh Lakshan Kurera"
        )
        val keerthana = StatsRosterPlayer(
            playerId = StatsPlayerId.from(337),
            fullName = "KS Balakrishnan",
            surname = "Balakrishnan",
            expandedFullName = "Keerthana Sathyamoorthy Balakrishnan"
        )
        val homendro = StatsRosterPlayer(
            playerId = StatsPlayerId.from(338),
            fullName = "KH Meitei",
            surname = "Meitei",
            expandedFullName = "Kabrambam Homendro Meitei"
        )
        val prafullomani = StatsRosterPlayer(
            playerId = StatsPlayerId.from(339),
            fullName = "PP Singh",
            surname = "Singh",
            expandedFullName = "Pukhrambam Prafullomani Singh"
        )
        val haliamKwon = StatsRosterPlayer(
            playerId = StatsPlayerId.from(340),
            fullName = "K Ha Lim",
            surname = "Lim",
            expandedFullName = "Kwon Ha Lim"
        )

        val roster = listOf(
            malinga, vidyut, ahsanMalik, jagannathan, ushettige,
            gimhan, lakshan, keerthana, homendro, prafullomani, haliamKwon
        )

        expectThat(PlayerNameMatcher.matchCloseEnough("E Malinga", roster)).isNotNull().isEqualTo(malinga)
        expectThat(PlayerNameMatcher.matchCloseEnough("S Vidyut", roster)).isNotNull().isEqualTo(vidyut)
        expectThat(PlayerNameMatcher.matchCloseEnough("Ahsan Malik", roster)).isNotNull().isEqualTo(ahsanMalik)
        expectThat(PlayerNameMatcher.matchPlayer("KS Jagannathan", roster)).isNotNull().isEqualTo(jagannathan)
        expectThat(PlayerNameMatcher.matchCloseEnough("P Ushettige", roster)).isNotNull().isEqualTo(ushettige)
        expectThat(PlayerNameMatcher.matchCloseEnough("S Gimhan", roster)).isNotNull().isEqualTo(gimhan)
        expectThat(PlayerNameMatcher.matchCloseEnough("U Lakshan", roster)).isNotNull().isEqualTo(lakshan)
        expectThat(PlayerNameMatcher.matchCloseEnough("SB Keerthana", roster)).isNotNull().isEqualTo(keerthana)
        expectThat(PlayerNameMatcher.matchCloseEnough("Homendro Kabrambam", roster)).isNotNull().isEqualTo(homendro)
        expectThat(PlayerNameMatcher.matchCloseEnough("P Prafullomani", roster)).isNotNull().isEqualTo(prafullomani)
        expectThat(PlayerNameMatcher.matchCloseEnough("Haliam Kwon", roster)).isNotNull().isEqualTo(haliamKwon)
    }

    @Test
    fun `given source surname is a middle expanded-name token when matching then resolves uniquely`() {
        val ruvishanPerera = StatsRosterPlayer(
            playerId = StatsPlayerId.from(341),
            fullName = "GSR Perera",
            surname = "Perera",
            expandedFullName = "Gammanage Sachintha Ruvishan Perera"
        )
        expectThat(PlayerNameMatcher.matchPlayer("Ruvishan Perera", listOf(ruvishanPerera)))
            .isNotNull().isEqualTo(ruvishanPerera)
    }

    @Test
    fun `given abbreviated source name matches consecutive expanded-name tokens when matching then resolves intended player`() {
        val thilshanWijesinghe = StatsRosterPlayer(
            playerId = StatsPlayerId.from(345),
            fullName = "WADT Wijesinghe",
            surname = "Wijesinghe",
            expandedFullName = "Wijesinghe Archchige Dilshan Tharinda Wijesinghe"
        )
        val thusithWickrama = StatsRosterPlayer(
            playerId = StatsPlayerId.from(346),
            fullName = "WKTD Wickrama",
            surname = "Wickrama",
            expandedFullName = "Wickrama Kaluthotage Thusith Dilshan Wickrama"
        )

        expectThat(PlayerNameMatcher.matchPlayer("T Dilshan", listOf(thilshanWijesinghe, thusithWickrama)))
            .isEqualTo(thusithWickrama)
    }

    @Test
    fun `given reordered source name with contracted expanded-name token when matching then resolves uniquely`() {
        val yebinKa = StatsRosterPlayer(
            playerId = StatsPlayerId.from(344),
            fullName = "K Ye Bin",
            surname = "YeBin",
            expandedFullName = "Ka Ye Bin"
        )

        expectThat(PlayerNameMatcher.matchPlayer("Yebin Ka", listOf(yebinKa)))
            .isNotNull().isEqualTo(yebinKa)
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

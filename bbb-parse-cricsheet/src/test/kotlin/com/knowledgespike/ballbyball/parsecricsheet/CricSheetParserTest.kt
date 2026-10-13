package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import com.knowledgespike.ballbyball.identity.SourceCaId
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.stream.Stream

class CricSheetParserTest {
    private val json = Json { ignoreUnknownKeys = false }
    private val noOpMatchLinker = MatchLinker { StatsLinkResult.Skipped }

    @Test
    fun `given an input directory without json files then parsing fails`() {
        val root = Files.createTempDirectory("parse-cricsheet-empty")
        Files.createDirectories(root.resolve("input"))

        val result = CricSheetParser(matchLinker = noOpMatchLinker)
            .parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.succeeded).isEqualTo(false)
        expectThat(result.failures.single().message).isEqualTo("No Cricsheet JSON files found")
    }

    @Test
    fun `given cricsheet input when parsed then output is mirrored with normalized names and match type`() {
        val root = Files.createTempDirectory("parse-cricsheet")
        val input = root.resolve("raw/archive")
        val output = root.resolve("normalized")
        Files.createDirectories(input)
        Files.writeString(input.resolve("12345.json"), sourceDocument())

        val result = CricSheetParser(matchLinker = noOpMatchLinker).parse(root.resolve("raw"), output)
        val normalized = Files.readString(output.resolve("archive/12345.json"))
        val envelope = json.decodeFromString<CanonicalMatchEnvelope>(normalized)

        expectThat(result.succeeded).isEqualTo(true)
        expectThat(normalized).contains("\"matchType\":\"wtt\"")
        expectThat(normalized).contains("\"match\"")
        expectThat(normalized.contains("\"meta\"")).isEqualTo(false)
        expectThat(normalized.contains("\"info\"")).isEqualTo(false)
        expectThat(normalized).contains("\"nonStriker\":\"Batter Two\"")
        expectThat(normalized).contains("\"cameIn\":\"Substitute\"")
        expectThat(normalized).contains("\"superSubs\":{\"Team A\":\"Substitute\"}")
        expectThat(envelope.sources.single().providerRecordKey).isEqualTo("12345")
        expectThat(envelope.sources.single().sourceRecordId.value.toString())
            .isEqualTo("c61f62bd-7b02-5c3f-ae44-572d533b5877")
    }

    @Test
    fun `given a stats match link when parsed then stats id is written to the envelope`() {
        val root = Files.createTempDirectory("parse-cricsheet-link")
        val input = root.resolve("input")
        val output = root.resolve("output")
        Files.createDirectories(input)
        Files.writeString(input.resolve("12345.json"), sourceDocument())

        val result = CricSheetParser(matchLinker = MatchLinker { StatsLinkResult.Linked(SourceCaId.from("ca-12345")) })
            .parse(input, output)
        val envelope = json.decodeFromString<CanonicalMatchEnvelope>(Files.readString(output.resolve("12345.json")))

        expectThat(result.succeeded).isEqualTo(true)
        expectThat(envelope.match.match.sourceCaId).isEqualTo("ca-12345")
    }

    @Test
    fun `given a stats lookup throws then normalized output is retained and success replaces it`() {
        val root = Files.createTempDirectory("parse-cricsheet-routing")
        val input = root.resolve("input")
        val parsed = root.resolve("parsed")
        Files.createDirectories(input)
        Files.writeString(input.resolve("12345.json"), sourceDocument())

        val failed = CricSheetParser(matchLinker = MatchLinker { error("lookup failed") })
            .parse(input, parsed)
        expectThat(failed.succeeded).isEqualTo(false)
        expectThat(Files.exists(parsed.resolve("12345.json"))).isEqualTo(true)
        val unlinked = json.decodeFromString<CanonicalMatchEnvelope>(Files.readString(parsed.resolve("12345.json")))
        expectThat(unlinked.match.match.sourceCaId).isEqualTo(null)

        val success = CricSheetParser(matchLinker = MatchLinker { StatsLinkResult.Linked(SourceCaId.from("ca-12345")) })
            .parse(input, parsed)

        expectThat(success.succeeded).isEqualTo(true)
        expectThat(Files.exists(parsed.resolve("12345.json"))).isEqualTo(true)
    }

    @Test
    fun `given equal input and output directories then parsing fails before creating outputs`() {
        val root = Files.createTempDirectory("parse-cricsheet-overlap")
        val input = root.resolve("input")
        Files.createDirectories(input)

        assertThrows<IllegalArgumentException> {
            CricSheetParser(matchLinker = noOpMatchLinker).parse(input, input)
        }

        expectThat(Files.exists(input.resolve("12345.json"))).isEqualTo(false)
    }

    @Test
    fun `given nested input and output directories then parsing fails before creating outputs`() {
        val root = Files.createTempDirectory("parse-cricsheet-nested-overlap")
        val input = root.resolve("input")
        val output = input.resolve("parsed")
        Files.createDirectories(input)

        assertThrows<IllegalArgumentException> {
            CricSheetParser(matchLinker = noOpMatchLinker).parse(input, output)
        }

        expectThat(Files.exists(output)).isEqualTo(false)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allSeasonExclusions")
    fun `given an all-season exclusion then stats lookup is skipped`(eventName: String) {
        assertStatsLookupSkipped(eventName)
    }

    @ParameterizedTest(name = "{0} in {1}")
    @MethodSource("seasonExclusions")
    fun `given a season exclusion then stats lookup is skipped`(eventName: String, season: String) {
        assertStatsLookupSkipped(eventName, season)
    }

    @Test
    fun `given a season exclusion in another season then stats lookup is attempted`() {
        assertStatsLookupAttempted("East Asia Cup", "2017/18")
    }


    @Test
    fun `given unsupported match type when parsed then failure is reported and other files continue`() {
        val root = Files.createTempDirectory("parse-cricsheet-failure")
        Files.createDirectories(root.resolve("input"))
        Files.writeString(root.resolve("input/100.json"), sourceDocument(matchType = "Unknown"))
        Files.writeString(root.resolve("input/101.json"), sourceDocument(matchType = "T20"))
        val result = CricSheetParser(matchLinker = noOpMatchLinker)
            .parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.failures.map { it.input.fileName.toString() }).isEqualTo(listOf("100.json"))
        expectThat(Files.exists(root.resolve("output/101.json"))).isEqualTo(true)
        expectThat(Files.exists(root.resolve("output/100.json"))).isEqualTo(false)
    }

    @Test
    fun `given a missing stats match when parsed then unlinked output is saved and later files continue`() {
        val root = Files.createTempDirectory("parse-cricsheet-missing-match")
        val input = root.resolve("input")
        val parsed = root.resolve("parsed")
        Files.createDirectories(input)
        Files.writeString(input.resolve("100.json"), sourceDocument(eventName = "Synthetic League"))
        Files.writeString(input.resolve("101.json"), sourceDocument(eventName = "Synthetic League"))
        var calls = 0

        val result = CricSheetParser(
            matchLinker = MatchLinker {
                if (calls++ == 0) {
                    StatsLinkResult.NotFound(
                        MatchLookupCriteria(
                            sourceFirstTeam = "Team A",
                            sourceSecondTeam = "Team B",
                            statsFirstTeam = "Team A",
                            statsSecondTeam = "Team B",
                            matchDate = LocalDate.parse("2024-01-01"),
                            margin = 0
                        )
                    )
                } else {
                    StatsLinkResult.Linked(SourceCaId.from("ca-101"))
                }
            }
        ).parse(input, parsed)

        expectThat(calls).isEqualTo(2)
        expectThat(result.failures.map { it.input.fileName.toString() }).isEqualTo(listOf("100.json"))
        expectThat(result.failures.single().message)
            .isEqualTo("No stats match found for source \"Team A\" vs source \"Team B\" on 2024-01-01 with margin 0")
        expectThat(Files.exists(parsed.resolve("101.json"))).isEqualTo(true)
        expectThat(Files.exists(parsed.resolve("100.json"))).isEqualTo(true)
        val unlinked = json.decodeFromString<CanonicalMatchEnvelope>(Files.readString(parsed.resolve("100.json")))
        expectThat(unlinked.match.match.sourceCaId).isEqualTo(null)
    }

    @Test
    fun `given an ambiguous stats link when parsed then boundary failure retains candidate ids`() {
        val criteria = MatchLookupCriteria(
            sourceFirstTeam = "Team A",
            sourceSecondTeam = "Team B",
            statsFirstTeam = "Team A",
            statsSecondTeam = "Team B",
            matchDate = LocalDate.parse("2024-01-01"),
            margin = 0
        )

        val failure = parseLinkFailure(
            StatsLinkResult.Ambiguous(criteria, listOf(SourceCaId.from("ca-1"), SourceCaId.from("ca-2")))
        )

        expectThat(failure.message)
            .isEqualTo(
                "Multiple stats matches found for source \"Team A\" vs source \"Team B\" on 2024-01-01 " +
                    "with margin 0: [ca-1, ca-2]"
            )
    }

    @Test
    fun `given an invalid stats alias when parsed then boundary failure retains alias message`() {
        val failure = parseLinkFailure(StatsLinkResult.InvalidAlias("Alias target could not be resolved"))

        expectThat(failure.message).isEqualTo("Alias target could not be resolved")
    }

    @Test
    fun `moving a source file between parent directories does not change identity`() {
        val first = parseSingle("first/archive", "12345.json", sourceDocument())
        val moved = parseSingle("moved/elsewhere", "12345.json", sourceDocument())

        expectThat(first.sources.single().sourceRecordId).isEqualTo(moved.sources.single().sourceRecordId)
        expectThat(first.sources.single().rawContentDigest).isEqualTo(moved.sources.single().rawContentDigest)
        expectThat(first.canonicalMatchId).isEqualTo(moved.canonicalMatchId)
    }

    @Test
    fun `correcting source bytes changes digest but preserves source and canonical identities`() {
        val original = parseSingle("original", "12345.json", sourceDocument())
        val corrected = parseSingle("corrected", "12345.json", sourceDocument().replace("The Hundred", "Corrected Event"))

        expectThat(original.sources.single().sourceRecordId).isEqualTo(corrected.sources.single().sourceRecordId)
        expectThat(original.canonicalMatchId).isEqualTo(corrected.canonicalMatchId)
        expectThat(original.sources.single().rawContentDigest).isNotEqualTo(corrected.sources.single().rawContentDigest)
    }

    @Test
    fun `current Cricsheet provider key with letters and underscore is accepted`() {
        val first = parseSingle("archive/first", "wi_201706.json", sourceDocument())
        val moved = parseSingle("archive/moved", "wi_201706.json", sourceDocument())

        expectThat(first.sources.single().providerRecordKey).isEqualTo("wi_201706")
        expectThat(first.sources.single().sourceRecordId).isEqualTo(moved.sources.single().sourceRecordId)
        expectThat(first.canonicalMatchId).isEqualTo(moved.canonicalMatchId)
    }

    @Test
    fun `unsafe Cricsheet provider key is rejected while valid files continue`() {
        val root = Files.createTempDirectory("parse-cricsheet-key")
        Files.createDirectories(root.resolve("input"))
        Files.writeString(root.resolve("input/not a provider key.json"), sourceDocument())
        Files.writeString(root.resolve("input/12345.json"), sourceDocument())

        val result = CricSheetParser(matchLinker = noOpMatchLinker)
            .parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.failures.map { it.input.fileName.toString() }).isEqualTo(listOf("not a provider key.json"))
        expectThat(Files.exists(root.resolve("output/12345.json"))).isEqualTo(true)
    }

    private fun assertStatsLookupSkipped(eventName: String, season: String = "2024") {
        val root = Files.createTempDirectory("parse-cricsheet-policy")
        val input = root.resolve("input")
        val parsed = root.resolve("parsed")
        Files.createDirectories(input)
        Files.writeString(input.resolve("12345.json"), sourceDocument(eventName = eventName, season = season))

        val matchLinker = StatsMatchLinker(object : StatsMatchRepository {
            override fun statsTeamCount(statsTeamId: StatsTeamId): Int = error("Stats lookup should not be attempted")

            override fun caIds(
                criteria: MatchLookupCriteria,
                firstTeam: StatsTeamLookup,
                secondTeam: StatsTeamLookup
            ): List<SourceCaId> = error("Stats lookup should not be attempted")
        })
        val result = CricSheetParser(matchLinker = matchLinker).parse(input, parsed)
        val envelope = json.decodeFromString<CanonicalMatchEnvelope>(Files.readString(parsed.resolve("12345.json")))

        expectThat(result.succeeded).isEqualTo(true)
        expectThat(envelope.match.match.sourceCaId).isEqualTo(null)
    }

    private fun assertStatsLookupAttempted(eventName: String, season: String) {
        val root = Files.createTempDirectory("parse-cricsheet-policy-control")
        val input = root.resolve("input")
        val parsed = root.resolve("parsed")
        Files.createDirectories(input)
        Files.writeString(input.resolve("12345.json"), sourceDocument(eventName = eventName, season = season))

        val matchLinker = StatsMatchLinker(object : StatsMatchRepository {
            override fun statsTeamCount(statsTeamId: StatsTeamId): Int = error("Stats lookup attempted")

            override fun caIds(
                criteria: MatchLookupCriteria,
                firstTeam: StatsTeamLookup,
                secondTeam: StatsTeamLookup
            ): List<SourceCaId> = error("Stats lookup attempted")
        })
        val result = CricSheetParser(matchLinker = matchLinker).parse(input, parsed)

        expectThat(result.succeeded).isEqualTo(false)
        expectThat(result.failures.single().message).isEqualTo("Stats lookup attempted")
        expectThat(Files.exists(parsed.resolve("12345.json"))).isEqualTo(true)
        val envelope = json.decodeFromString<CanonicalMatchEnvelope>(Files.readString(parsed.resolve("12345.json")))
        expectThat(envelope.match.match.sourceCaId).isEqualTo(null)
    }

    private fun parseLinkFailure(linkResult: StatsLinkResult): ParseFailure {
        val root = Files.createTempDirectory("parse-cricsheet-link-failure")
        val input = root.resolve("input")
        Files.createDirectories(input)
        Files.writeString(input.resolve("12345.json"), sourceDocument())

        val parsed = root.resolve("parsed")
        val result = CricSheetParser(matchLinker = MatchLinker { linkResult }).parse(input, parsed)
        val envelope = json.decodeFromString<CanonicalMatchEnvelope>(Files.readString(parsed.resolve("12345.json")))

        expectThat(envelope.match.match.sourceCaId).isEqualTo(null)
        return result.failures.single()
    }

    private fun CricSheetParser.parse(inputDirectory: Path, outputDirectory: Path): ParseResult =
        parse(ParseCricsheetInput.fromDirectories(inputDirectory, outputDirectory))

    companion object {
        @JvmStatic
        fun allSeasonExclusions(): Stream<String> = Stream.of(
            "ICC World Cricket League Europe Region Division Two Twenty20",
            "Kenya T20 Quadrangular Series",
            "ICC World Twenty20 Asia Region Qualifier A",
            "ICC World Twenty20 Africa Region Qualifier A",
            "ICC World Twenty20 Africa Region Qualifier B",
            "ICC World Twenty20 East Asia-Pacific Region Qualifier A",
            "ICC World Twenty20 East Asia-Pacific Region Qualifier B",
            "ICC World Twenty20 Europe Region Qualifier A",
            "ICC World Twenty20 Europe Region Qualifier B",
            "ICC World Twenty20 Europe Region Qualifier C",
            "ICC World Twenty20 Americas Sub Regional Qualifier A",
            "ICC World Twenty20 Asia Region Qualifier B",
            "Stan Nagaiah Trophy",
            "Continental Cup",
            "Nepal Premier League"
        )

        @JvmStatic
        fun seasonExclusions(): Stream<Arguments> = Stream.of(
            Arguments.of("East Asia Cup", "2016/17"),
            Arguments.of("East Asia Cup", "2018"),
            Arguments.of("Asian Games Men's Cricket Competition", "2014/15"),
            Arguments.of("ICC World Twenty20 Africa Region Qualifier C", "2018/19"),
            Arguments.of("ICC World Cricket League Americas Region Division One Twenty20", "2010"),
            Arguments.of("ACC Twenty20 Cup", "2014/15"),
            Arguments.of("Major League Cricket", "2023")
        )
    }

    private fun parseSingle(parent: String, fileName: String, content: String): CanonicalMatchEnvelope {
        val root = Files.createTempDirectory("parse-cricsheet-identity")
        val input = root.resolve("input/$parent")
        Files.createDirectories(input)
        Files.writeString(input.resolve(fileName), content)

        val result = CricSheetParser(matchLinker = noOpMatchLinker)
            .parse(root.resolve("input"), root.resolve("output"))

        expectThat(result.succeeded).isEqualTo(true)
        return json.decodeFromString(Files.readString(root.resolve("output/$parent/$fileName")))
    }

    private fun sourceDocument(
        matchType: String = "T20",
        eventName: String = "The Hundred",
        season: String = "2024"
    ) = """
        {
          "meta": {"data_version": "1.0", "created": "2024-01-01", "revision": 1},
          "info": {
            "balls_per_over": 6,
            "dates": ["2024-01-01"],
            "event": {"name": "$eventName", "match_number": 1},
            "gender": "female",
            "match_type": "$matchType",
            "outcome": {"result": "draw"},
            "players": {"Team A": ["Batter One", "Batter Two"]},
            "registry": {"people": {"Batter One": "person-1"}},
            "season": "$season",
            "supersubs": {"Team A": "Substitute"},
            "team_type": "international",
            "teams": ["Team A", "Team B"],
            "toss": {"decision": "bat", "winner": "Team A"}
          },
          "innings": [{
            "team": "Team A",
            "overs": [{
              "over": 0,
              "deliveries": [{
                "batter": "Batter One",
                "bowler": "Bowler One",
                "non_striker": "Batter Two",
                "replacements": {"match": [{"in": "Substitute", "out": "Batter One", "reason": "tactical", "team": "Team A"}]},
                "runs": {"batter": 1, "extras": 0, "total": 1}
              }]
            }]
          }]
        }
    """.trimIndent()
}
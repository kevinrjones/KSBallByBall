package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.AliasSourceSystem
import com.knowledgespike.ballbyball.identity.SourceCaId
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import java.lang.reflect.Proxy
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDate

class StatsMatchLinkerTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val input = Path.of("input.json")

    @Test
    fun `given an exact match and an invalid alias then exact ca id is returned first`() {
        val lookups = mutableListOf<StatsMatchCandidate>()
        val statsRepository = object : StatsMatchRepository {
            override fun statsTeamCount(statsTeamId: StatsTeamId): Int = 0

            override fun caIds(
                criteria: MatchLookupCriteria,
                firstTeam: StatsTeamLookup,
                secondTeam: StatsTeamLookup
            ): List<SourceCaId> {
                lookups += StatsMatchCandidate(firstTeam, secondTeam)
                return if (firstTeam == StatsTeamLookup.Name("Team A") &&
                    secondTeam == StatsTeamLookup.Name("Team B")
                ) {
                    listOf(SourceCaId.from("ca-exact"))
                } else {
                    emptyList()
                }
            }
        }
        val invalidAliasResolver = StatsTeamAliasResolver { sourceTeamName ->
            error("Alias resolver should not be called for an exact match: $sourceTeamName")
        }
        val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
            sourceDocument()
        )

        val caId = StatsMatchLinker(statsRepository, invalidAliasResolver).sourceCaId(source.match)

        expectThat(caId).isEqualTo("ca-exact")
        expectThat(lookups).containsExactly(
            StatsMatchCandidate(
                StatsTeamLookup.Name("Team A"),
                StatsTeamLookup.Name("Team B")
            )
        )
    }

    @Test
    fun `given matching stats row then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-12345", 1, 2)
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-12345")
        }
    }

    @Test
    fun `given all stats lookup dates miss then the match table is queried once`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            var matchQueryCount = 0
            val countedConnection = Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java)
            ) { _, method, arguments ->
                if (method.name == "prepareStatement" && arguments?.getOrNull(0)?.toString()?.contains("FROM Matches m") == true) {
                    matchQueryCount++
                }
                method.invoke(statsConnection, *arguments.orEmpty())
            } as Connection
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(countedConnection, aliasConnection).link(source.match, input))
                .isEqualTo(StatsLinkResult.NotFound(MatchLookupCriteria.from(source.match)))
            expectThat(matchQueryCount).isEqualTo(1)
        }
    }

    @Test
    fun `given an exact match and a margin-matching alias then exact margin match is preferred`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertTeam(statsConnection, 3, 30, "Canonical A")
            insertMatch(statsConnection, "ca-exact-margin", 1, 2, howMuch = 100)
            insertMatch(statsConnection, "ca-alias-margin", 3, 2, howMuch = 100)
            insertAlias(aliasConnection, "Team A", 30)
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument().replace(
                    "\"outcome\": {\"result\": \"draw\"}",
                    "\"outcome\": {\"winner\": \"Team A\", \"by\": {\"runs\": 100}}"
                )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-exact-margin")
        }
    }

    @Test
    fun `given alias margin match and exact margin mismatch then alias margin match is preferred`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertTeam(statsConnection, 3, 30, "Canonical A")
            insertMatch(statsConnection, "ca-exact-plain", 1, 2, howMuch = 105)
            insertMatch(statsConnection, "ca-alias-margin", 3, 2, howMuch = 100)
            insertAlias(aliasConnection, "Team A", 30)
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument().replace(
                    "\"outcome\": {\"result\": \"draw\"}",
                    "\"outcome\": {\"winner\": \"Team A\", \"by\": {\"runs\": 100}}"
                )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-alias-margin")
        }
    }

    @Test
    fun `given stats match starts one day before source date then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-before", 1, 2, matchStartDate = "2024-01-31")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-before"))
                )
        }
    }

    @Test
    fun `given stats match starts one day after source date then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-after", 1, 2, matchStartDate = "2024-03-24")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-03-23")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-after"))
                )
        }
    }

    @Test
    fun `given stats match starts two days before source date then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-two-before", 1, 2, matchStartDate = "2024-01-30")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-two-before"))
                )
        }
    }

    @Test
    fun `given stats match starts two days after source date then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-two-after", 1, 2, matchStartDate = "2024-03-25")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-03-23")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-two-after"))
                )
        }
    }

    @Test
    fun `given one day and two day alternate stats rows then closer date is preferred`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-one-before", 1, 2, matchStartDate = "2024-01-31")
            insertMatch(statsConnection, "ca-two-before", 1, 2, matchStartDate = "2024-01-30")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-one-before"))
                )
        }
    }

    @Test
    fun `given exact date and alternate date stats rows then exact date is preferred`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-exact-date", 1, 2, matchStartDate = "2024-02-01")
            insertMatch(statsConnection, "ca-alternate-date", 1, 2, matchStartDate = "2024-01-31")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-exact-date"))
                )
        }
    }

    @Test
    fun `given alternate date margin matches then matching ca id is preferred`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-margin-match", 1, 2, howMuch = 100, matchStartDate = "2024-01-31")
            insertMatch(statsConnection, "ca-margin-mismatch", 1, 2, howMuch = 105, matchStartDate = "2024-01-31")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01").replace(
                    "\"outcome\": {\"result\": \"draw\"}",
                    "\"outcome\": {\"winner\": \"Team A\", \"by\": {\"runs\": 100}}"
                )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-margin-match"))
                )
        }
    }

    @Test
    fun `given alternate date has no matching margin then lookup retries without margin`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-plain-alternate", 1, 2, howMuch = 105, matchStartDate = "2024-01-31")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01").replace(
                    "\"outcome\": {\"result\": \"draw\"}",
                    "\"outcome\": {\"winner\": \"Team A\", \"by\": {\"runs\": 100}}"
                )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Linked(SourceCaId.from("ca-plain-alternate"))
                )
        }
    }

    @Test
    fun `given alternate date has multiple matching stats rows then lookup stops as ambiguous`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-before-a", 1, 2, matchStartDate = "2024-01-31")
            insertMatch(statsConnection, "ca-before-b", 1, 2, matchStartDate = "2024-01-31")
            insertMatch(statsConnection, "ca-after", 1, 2, matchStartDate = "2024-02-02")
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Ambiguous(
                        MatchLookupCriteria.from(source.match).copy(matchDate = LocalDate.parse("2024-01-31")),
                        listOf(SourceCaId.from("ca-before-a"), SourceCaId.from("ca-before-b"))
                    )
                )
        }
    }

    @Test
    fun `given no stats matches on primary or alternate dates then lookup keeps primary criteria`() {
        withDatabases { statsConnection, aliasConnection ->
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument("2024-02-01")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(StatsLinkResult.NotFound(MatchLookupCriteria.from(source.match)))
        }
    }

    @Test
    fun `given one aliased team and one exact team then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-partial", 1, 2)
            insertAlias(aliasConnection, "Team A", 10)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-partial")
        }
    }

    @Test
    fun `given multiple team alias ids then JDBC repository matches them in one query`() {
        withDatabases { statsConnection, _ ->
            insertTeam(statsConnection, 1, 10, "Canonical A")
            insertTeam(statsConnection, 2, 11, "Alternate A")
            insertTeam(statsConnection, 3, 20, "Team B")
            insertMatch(statsConnection, "ca-aset", 1, 3)
            val match = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            ).match

            val caIds = JdbcStatsMatchRepository(statsConnection).caIds(
                MatchLookupCriteria.from(match),
                StatsTeamLookup.IdSet(listOf(StatsTeamId.from(10), StatsTeamId.from(11))),
                StatsTeamLookup.Name("Team B")
            )

            expectThat(caIds).containsExactly(SourceCaId.from("ca-aset"))
        }
    }

    @Test
    fun `given a female match alias under its source team name then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical Australia Women")
            insertTeam(statsConnection, 2, 20, "Team B Women")
            insertMatch(statsConnection, "ca-female", 1, 2)
            insertAlias(aliasConnection, "Australia", 10)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
                    .replace("\"gender\": \"male\"", "\"gender\": \"female\"")
                    .replace("Team A", "Australia")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-female")
        }
    }

    @Test
    fun `given a female match alias under its stats team name then lookup misses with source and stats names`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical Australia Women")
            insertTeam(statsConnection, 2, 20, "Team B Women")
            insertAlias(aliasConnection, "Australia Women", 10)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
                    .replace("\"gender\": \"male\"", "\"gender\": \"female\"")
                    .replace("Team A", "Australia")
            )
            val result = StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input)

            expectThat(result).isEqualTo(StatsLinkResult.NotFound(MatchLookupCriteria.from(source.match)))
            expectThat((result as StatsLinkResult.NotFound).criteria.toString())
                .contains("source \"Australia\" (stats \"Australia Women\")")
                .contains("source \"Team B\" (stats \"Team B Women\")")
        }
    }

    @Test
    fun `given a margin mismatch then lookup retries without margin`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-margin", 1, 2, howMuch = 105)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument().replace(
                    "\"outcome\": {\"result\": \"draw\"}",
                    "\"outcome\": {\"winner\": \"Team A\", \"by\": {\"runs\": 100}}"
                )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-margin")
        }
    }

    @Test
    fun `given a missing margin and multiple matching stats rows then lookup fails as ambiguous`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-first", 1, 2, howMuch = 105)
            insertMatch(statsConnection, "ca-second", 1, 2, howMuch = 7)
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Ambiguous(
                        MatchLookupCriteria.from(source.match),
                        listOf(SourceCaId.from("ca-first"), SourceCaId.from("ca-second"))
                    )
                )
        }
    }

    @Test
    fun `given a female Charlotte Edwards Cup match then team names are not suffixed`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-cec", 1, 2)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
                    .replace("\"gender\": \"male\"", "\"gender\": \"female\"")
                    .replace(
                        "\"dates\": [\"2024-01-01\"],",
                        "\"dates\": [\"2024-01-01\"],\n            \"event\": {\"name\": \"Charlotte Edwards Cup\"},"
                    )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-cec")
        }
    }

    @Test
    fun `given a female Rachael Heyhoe Flint Trophy match then team names are not suffixed`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-rhft", 1, 2)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
                    .replace("\"gender\": \"male\"", "\"gender\": \"female\"")
                    .replace(
                        "\"dates\": [\"2024-01-01\"],",
                        "\"dates\": [\"2024-01-01\"],\n            \"event\": {\"name\": \"Rachael Heyhoe Flint Trophy\"},"
                    )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-rhft")
        }
    }

    @Test
    fun `given a female Women's T20 Challenge Match then team names are not suffixed`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-women", 1, 2)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
                    .replace("\"gender\": \"male\"", "\"gender\": \"female\"")
                    .replace(
                        "\"dates\": [\"2024-01-01\"],",
                        "\"dates\": [\"2024-01-01\"],\n            \"event\": {\"name\": \"Women's T20 Challenge Match\"},"
                    )
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-women")
        }
    }

    @Test
    fun `given a female match involving The Blaze then only The Blaze is not suffixed`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "The Blaze")
            insertTeam(statsConnection, 2, 20, "Team B Women")
            insertMatch(statsConnection, "ca-blaze", 1, 2)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
                    .replace("\"gender\": \"male\"", "\"gender\": \"female\"")
                    .replace("\"Team A\"", "\"The Blaze\"")
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-blaze")
        }
    }

    @Test
    fun `given alias stored under cricsheet provider namespace then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 45116, "Cumilla Warriors")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-prov", 1, 2)
            insertAlias(
                connection = aliasConnection,
                sourceTeamName = "Team A",
                statsTeamId = 45116,
                sourceSystem = AliasSourceSystem.CRICSHEET.value
            )

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-prov")
        }
    }

    @Test
    fun `given alias under cricsheet string key then default repository misses it`() {
        withDatabases { _, aliasConnection ->
            insertAlias(
                connection = aliasConnection,
                sourceTeamName = "Team A",
                statsTeamId = 45116,
                sourceSystem = "cricsheet"
            )
            val repository = JdbcTeamNameAliasRepository(aliasConnection)

            expectThat(repository.statsTeamIds("Team A")).isEqualTo(emptyList())

            insertAlias(aliasConnection, "Team A", 45116)

            expectThat(repository.statsTeamIds("Team A")).isEqualTo(listOf(StatsTeamId.from(45116)))
        }
    }

    @Test
    fun `given aliases for both teams then reverse team order still returns ca id`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical A")
            insertTeam(statsConnection, 2, 20, "Canonical B")
            insertMatch(statsConnection, "ca-both", 2, 1)
            insertAlias(aliasConnection, "Team A", 10)
            insertAlias(aliasConnection, "Team B", 20)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-both")
        }
    }

    @Test
    fun `given both aliases but one alias matches with the exact other team then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertTeam(statsConnection, 3, 30, "Other B")
            insertMatch(statsConnection, "ca-indiv", 1, 2)
            insertAlias(aliasConnection, "Team A", 10)
            insertAlias(aliasConnection, "Team B", 30)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-indiv")
        }
    }

    @Test
    fun `given both aliases but the second alias matches with the exact first team then ca id is returned`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Canonical B")
            insertTeam(statsConnection, 3, 30, "Other A")
            insertMatch(statsConnection, "ca-2indv", 1, 2)
            insertAlias(aliasConnection, "Team A", 30)
            insertAlias(aliasConnection, "Team B", 20)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-2indv")
        }
    }

    @Test
    fun `given duplicate aliases for one source team then every target is tried`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertTeam(statsConnection, 3, 30, "Other A")
            insertMatch(statsConnection, "ca-dupal", 3, 2)
            insertAlias(aliasConnection, "Team A", 10)
            insertAlias(aliasConnection, "Team A", 30)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).sourceCaId(source.match))
                .isEqualTo("ca-dupal")
        }
    }

    @Test
    fun `given aliases matching different stats rows then lookup fails as ambiguous`() {
        val statsRepository = object : StatsMatchRepository {
            override fun statsTeamCount(statsTeamId: StatsTeamId): Int = 1

            override fun caIds(
                criteria: MatchLookupCriteria,
                firstTeam: StatsTeamLookup,
                secondTeam: StatsTeamLookup
            ): List<SourceCaId> = if (
                firstTeam == StatsTeamLookup.AnyOf(
                    listOf(
                        StatsTeamLookup.Name("Team A"),
                        StatsTeamLookup.IdSet(listOf(StatsTeamId.from(10), StatsTeamId.from(30)))
                    )
                ) && secondTeam == StatsTeamLookup.Name("Team B")
            ) {
                listOf(SourceCaId.from("ca-a1"), SourceCaId.from("ca-a2"))
            } else {
                emptyList()
            }
        }
        val aliasResolver = StatsTeamAliasResolver { sourceTeamName ->
            if (sourceTeamName == "Team A") {
                listOf(StatsTeamLookup.Id(StatsTeamId.from(10)), StatsTeamLookup.Id(StatsTeamId.from(30)))
            } else {
                emptyList()
            }
        }
        val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
            sourceDocument()
        )

        expectThat(StatsMatchLinker(statsRepository, aliasResolver).link(source.match, input))
            .isEqualTo(
                StatsLinkResult.Ambiguous(
                    MatchLookupCriteria.from(source.match),
                    listOf(SourceCaId.from("ca-a1"), SourceCaId.from("ca-a2"))
                )
            )
    }

    @Test
    fun `given multiple matching stats rows then lookup fails as ambiguous`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Team A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-first", 1, 2)
            insertMatch(statsConnection, "ca-second", 1, 2)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Ambiguous(
                        MatchLookupCriteria.from(source.match),
                        listOf(SourceCaId.from("ca-first"), SourceCaId.from("ca-second"))
                    )
                )
        }
    }

    @Test
    fun `given multiple matching aliased stats rows then lookup fails as ambiguous`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical A")
            insertTeam(statsConnection, 2, 20, "Team B")
            insertMatch(statsConnection, "ca-alias1", 1, 2)
            insertMatch(statsConnection, "ca-alias2", 1, 2)
            insertAlias(aliasConnection, "Team A", 10)

            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.Ambiguous(
                        MatchLookupCriteria.from(source.match),
                        listOf(SourceCaId.from("ca-alias1"), SourceCaId.from("ca-alias2"))
                    )
                )
        }
    }

    @Test
    fun `given no matching stats row then lookup fails`() {
        withDatabases { statsConnection, aliasConnection ->
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(StatsLinkResult.NotFound(MatchLookupCriteria.from(source.match)))
        }
    }

    @Test
    fun `given an alias target absent from stats teams then lookup fails explicitly`() {
        withDatabases { statsConnection, aliasConnection ->
            insertAlias(aliasConnection, "Team A", 999)
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(
                    StatsLinkResult.InvalidAlias(
                        "Alias for Team A targets stats team 999, but no unique stats team was found"
                    )
                )
        }
    }

    @Test
    fun `given an alias target matching multiple stats teams then lookup fails explicitly`() {
        withDatabases { statsConnection, aliasConnection ->
            insertTeam(statsConnection, 1, 10, "Canonical A")
            insertTeam(statsConnection, 2, 10, "Duplicate A")
            insertAlias(aliasConnection, "Team A", 10)
            val source = json.decodeFromString<com.knowledgespike.ballbyball.clishared.schema.BbbMatchData>(
                sourceDocument()
            )

            expectThat(StatsMatchLinker.fromConnections(statsConnection, aliasConnection).link(source.match, input))
                .isEqualTo(StatsLinkResult.InvalidAlias("Alias for Team A targets stats team 10, but 2 stats teams were found"))
        }
    }

    private fun withDatabases(block: (java.sql.Connection, java.sql.Connection) -> Unit) {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { statsConnection ->
            DriverManager.getConnection("jdbc:sqlite::memory:").use { aliasConnection ->
                createStatsTables(statsConnection)
                createAliasTable(aliasConnection)
                block(statsConnection, aliasConnection)
            }
        }
    }

    private fun StatsMatchLinker.sourceCaId(match: com.knowledgespike.ballbyball.clishared.schema.Info): String =
        when (val result = link(match, input)) {
            is StatsLinkResult.Linked -> result.sourceCaId.value
            else -> error("Expected a linked result, got $result")
        }

    private fun createStatsTables(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE Teams (
                    Id INTEGER NOT NULL PRIMARY KEY,
                    TeamId INTEGER NOT NULL,
                    Name TEXT NOT NULL
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE Matches (
                    CaId TEXT NOT NULL,
                    HomeTeamId INTEGER NOT NULL,
                    AwayTeamId INTEGER NOT NULL,
                    MatchStartDate TEXT,
                    HowMuch INTEGER NOT NULL,
                    MatchType TEXT NOT NULL DEFAULT 'tt'
                )
                """.trimIndent()
            )
        }
    }

    private fun createAliasTable(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                CREATE TABLE team_name_aliases (
                    source_system TEXT NOT NULL,
                    source_team_name TEXT NOT NULL,
                    stats_team_id INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun insertTeam(connection: java.sql.Connection, id: Int, statsTeamId: Int, name: String) {
        connection.prepareStatement("INSERT INTO Teams (Id, TeamId, Name) VALUES (?, ?, ?)").use { statement ->
            statement.setInt(1, id)
            statement.setInt(2, statsTeamId)
            statement.setString(3, name)
            statement.executeUpdate()
        }
    }

    private fun insertMatch(
        connection: java.sql.Connection,
        caId: String,
        homeTeamId: Int,
        awayTeamId: Int,
        howMuch: Int = 0,
        matchStartDate: String = "2024-01-01"
    ) {
        connection.prepareStatement(
            "INSERT INTO Matches (CaId, HomeTeamId, AwayTeamId, MatchStartDate, HowMuch) VALUES (?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, caId)
            statement.setInt(2, homeTeamId)
            statement.setInt(3, awayTeamId)
            statement.setString(4, matchStartDate)
            statement.setInt(5, howMuch)
            statement.executeUpdate()
        }
    }

    private fun insertAlias(
        connection: java.sql.Connection,
        sourceTeamName: String,
        statsTeamId: Int,
        sourceSystem: String = AliasSourceSystem.CRICSHEET.value
    ) {
        connection.prepareStatement(
            "INSERT INTO team_name_aliases (source_system, source_team_name, stats_team_id) VALUES (?, ?, ?)"
        ).use { statement ->
            statement.setString(1, sourceSystem)
            statement.setString(2, sourceTeamName)
            statement.setInt(3, statsTeamId)
            statement.executeUpdate()
        }
    }

    private fun sourceDocument(date: String = "2024-01-01") = """
        {
          "match": {
            "ballsPerOver": 6,
            "dates": ["$date"],
            "gender": "male",
            "matchType": "tt",
            "outcome": {"result": "draw"},
            "players": {},
            "registry": {"people": {}},
            "season": "2024",
            "teamType": "international",
            "teams": ["Team A", "Team B"],
            "toss": {"decision": "bat", "winner": "Team A"}
          },
          "innings": []
        }
    """.trimIndent()
}
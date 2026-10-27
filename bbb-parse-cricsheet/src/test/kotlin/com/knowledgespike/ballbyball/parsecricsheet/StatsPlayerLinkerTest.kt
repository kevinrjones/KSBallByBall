package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
import com.knowledgespike.ballbyball.identity.SourcePersonId
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.util.concurrent.atomic.AtomicInteger

class StatsPlayerLinkerTest {

    @Test
    fun `given all match players are in cache when linking then stats repository is not queried`() {
        var statsQueryCount = 0
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> {
                statsQueryCount++
                return emptyList()
            }
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache(
            mapOf(
                SourcePersonId.from("person_1") to StatsPlayerId.from(101),
                SourcePersonId.from("person_2") to StatsPlayerId.from(102)
            )
        )
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val matchPlayers = mapOf("Virat Kohli" to "person_1", "Rohit Sharma" to "person_2")
        val results = linker.linkPlayers(SourceCaId.from("a123"), matchPlayers)

        expectThat(statsQueryCount).isEqualTo(0)
        expectThat(results).hasSize(2)
        expectThat(results[0].statsPlayerId?.value).isEqualTo(101)
        expectThat(results[1].statsPlayerId?.value).isEqualTo(102)
    }

    @Test
    fun `given uncached player when linking then queries stats repository and saves to cache and db`() {
        val statsQueryCount = AtomicInteger(0)
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> {
                statsQueryCount.incrementAndGet()
                return listOf(
                    StatsRosterPlayer(
                        playerId = StatsPlayerId.from(201),
                        fullName = "Steven Peter Devereux Smith",
                        knownAs = "Steve Smith",
                        surname = "Smith"
                    )
                )
            }
        }
        val savedMappings = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = savedMappings
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                savedMappings[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val matchPlayers = mapOf("Steve Smith" to "person_smith")
        val results = linker.linkPlayers(SourceCaId.from("a456"), matchPlayers)

        expectThat(statsQueryCount.get()).isEqualTo(1)
        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId?.value).isEqualTo(201)
        expectThat(cache[SourcePersonId.from("person_smith")]?.value).isEqualTo(201)
        expectThat(savedMappings[SourcePersonId.from("person_smith")]?.value).isEqualTo(201)

        // Second lookup for the same player across another match should hit cache
        val secondResults = linker.linkPlayers(SourceCaId.from("a457"), matchPlayers)
        expectThat(statsQueryCount.get()).isEqualTo(1) // Still 1 query, no additional queries!
        expectThat(secondResults[0].statsPlayerId?.value).isEqualTo(201)
    }

    @Test
    fun `given db persistence failure when linking then cache is not populated with phantom entry`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(201),
                    fullName = "Steven Peter Devereux Smith",
                    knownAs = "Steve Smith",
                    surname = "Smith"
                )
            )
        }
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = emptyMap()
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                error("DB connection dropped")
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val matchPlayers = mapOf("Steve Smith" to "person_smith")
        val results = linker.linkPlayers(SourceCaId.from("a456"), matchPlayers)

        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId?.value).isEqualTo(201)
        expectThat(cache[SourcePersonId.from("person_smith")]).isNull()
    }

    @Test
    fun `given unmatched player when linking then returns null playerId and does not crash`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(301),
                    fullName = "Jasprit Jasbirsingh Bumrah",
                    surname = "Bumrah"
                )
            )
        }
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = emptyMap()
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) = Unit
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val matchPlayers = mapOf("Unrelated Player" to "person_unknown")
        val results = linker.linkPlayers(SourceCaId.from("a789"), matchPlayers)

        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId).isNull()
    }

    @Test
    fun `given exact off-roster referee when linking then saves unique global match`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = emptyList()

            override fun exactMatchesForFullName(fullName: String): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(801),
                    fullName = "J Chikange"
                )
            )
        }
        val savedMappings = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = savedMappings
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                savedMappings[sourcePersonId] = statsPlayerId
            }
        }
        val personId = SourcePersonId.from("referee_chikange")
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val results = linker.linkPlayers(SourceCaId.from("tt1290805"), mapOf("J Chikange" to personId.value))

        expectThat(results.single().statsPlayerId?.value).isEqualTo(801)
        expectThat(savedMappings[personId]?.value).isEqualTo(801)
        expectThat(cache[personId]?.value).isEqualTo(801)
    }

    @Test
    fun `given ambiguous exact global matches when linking then leaves person unresolved`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = emptyList()

            override fun exactMatchesForFullName(fullName: String): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(StatsPlayerId.from(801), "J Chikange"),
                StatsRosterPlayer(StatsPlayerId.from(802), "J Chikange")
            )
        }
        val savedMappings = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = savedMappings
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                savedMappings[sourcePersonId] = statsPlayerId
            }
        }
        val personId = SourcePersonId.from("referee_chikange")
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val results = linker.linkPlayers(SourceCaId.from("tt1290805"), mapOf("J Chikange" to personId.value))

        expectThat(results.single().statsPlayerId).isNull()
        expectThat(savedMappings.containsKey(personId)).isEqualTo(false)
        expectThat(cache[personId]).isNull()
    }

    @Test
    fun `given match with umpires and referees when linking then matches and caches official IDs`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(401),
                    fullName = "A Ali Baig",
                    knownAs = "A Ali Baig",
                    surname = "Baig"
                ),
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(402),
                    fullName = "Ranjan S Madugalle",
                    knownAs = "RS Madugalle",
                    surname = "Madugalle"
                )
            )
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val officials = mapOf("AA Baig" to "umpire_1", "RS Madugalle" to "referee_1")
        val results = linker.linkPlayers(SourceCaId.from("match_officials_1"), officials)

        expectThat(results).hasSize(2)
        val baigResult = results.find { it.sourceName == "AA Baig" }
        val madugalleResult = results.find { it.sourceName == "RS Madugalle" }

        expectThat(baigResult?.statsPlayerId?.value).isEqualTo(401)
        expectThat(madugalleResult?.statsPlayerId?.value).isEqualTo(402)
        expectThat(cache[SourcePersonId.from("umpire_1")]?.value).isEqualTo(401)
        expectThat(cache[SourcePersonId.from("referee_1")]?.value).isEqualTo(402)
        expectThat(memoryDb[SourcePersonId.from("umpire_1")]?.value).isEqualTo(401)
        expectThat(memoryDb[SourcePersonId.from("referee_1")]?.value).isEqualTo(402)
    }

    @Test
    fun `given match with spelling variations when linking then resolves fuzzy match and caches in memory and DB`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(501),
                    fullName = "A Varadharajan",
                    knownAs = "A Varadharajan",
                    surname = "Varadharajan"
                )
            )
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val players = mapOf("Aaditya Varadarajan" to "person_varadarajan_1")
        val results = linker.linkPlayers(SourceCaId.from("a1355828"), players, "odis_json/1445038.json")

        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId?.value).isEqualTo(501)
        expectThat(cache[SourcePersonId.from("person_varadarajan_1")]?.value).isEqualTo(501)
        expectThat(memoryDb[SourcePersonId.from("person_varadarajan_1")]?.value).isEqualTo(501)
    }

    @Test
    fun `given match with inverted candidate name when linking then resolves match and caches in memory and DB`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(502),
                    fullName = "Varadharajan, A",
                    knownAs = "Varadharajan, A",
                    surname = "Varadharajan"
                )
            )
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val players = mapOf("Aaditya Varadarajan" to "person_varadarajan_2")
        val results = linker.linkPlayers(SourceCaId.from("a1355828"), players, "odis_json/1445038.json")

        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId?.value).isEqualTo(502)
        expectThat(cache[SourcePersonId.from("person_varadarajan_2")]?.value).isEqualTo(502)
        expectThat(memoryDb[SourcePersonId.from("person_varadarajan_2")]?.value).isEqualTo(502)
    }

    @Test
    fun `given substitute fielder in roster when linking then resolves match and caches in memory and DB`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(301),
                    fullName = "Kevin White",
                    knownAs = "Kevin White",
                    surname = "White"
                )
            )
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val players = mapOf("K White" to "d60a26db")
        val results = linker.linkPlayers(SourceCaId.from("tt993242"), players, "bbl_json/1023585.json")

        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId?.value).isEqualTo(301)
        expectThat(cache[SourcePersonId.from("d60a26db")]?.value).isEqualTo(301)
        expectThat(memoryDb[SourcePersonId.from("d60a26db")]?.value).isEqualTo(301)
    }

    @Test
    fun `given Mehedi Maruf when linking in match with Mehedi Hasan in roster then resolves via elimination and caches in memory and DB`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(101),
                    fullName = "Shakib Al Hasan",
                    knownAs = null,
                    surname = "ShakibAlHasan"
                ),
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(102),
                    fullName = "Mehdi Hasan",
                    knownAs = null,
                    surname = "MehdiHasan"
                )
            )
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val players = mapOf(
            "Shakib Al Hasan" to "p_shakib",
            "Mehedi Maruf" to "p_mehedi"
        )
        val results = linker.linkPlayers(SourceCaId.from("tt1011599"), players, "bpl_json/1063050.json")

        expectThat(results).hasSize(2)
        val shakibResult = results.find { it.sourcePersonId == SourcePersonId.from("p_shakib") }
        val mehediResult = results.find { it.sourcePersonId == SourcePersonId.from("p_mehedi") }

        expectThat(shakibResult?.statsPlayerId?.value).isEqualTo(101)
        expectThat(mehediResult?.statsPlayerId?.value).isEqualTo(102)

        expectThat(cache[SourcePersonId.from("p_mehedi")]?.value).isEqualTo(102)
        expectThat(memoryDb[SourcePersonId.from("p_mehedi")]?.value).isEqualTo(102)
    }

    @Test
    fun `given D Shepherd TV Umpire when linking in match with DJ Shepard in roster then resolves via elimination and caches in memory and DB`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = listOf(
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(201),
                    fullName = "GC Joshua",
                    knownAs = "GC Joshua",
                    surname = "Joshua"
                ),
                StatsRosterPlayer(
                    playerId = StatsPlayerId.from(202),
                    fullName = "DJ Shepard",
                    knownAs = "DJ Shepard",
                    surname = "Shepard"
                )
            )
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val players = mapOf(
            "GC Joshua" to "umpire_1",
            "D Shepherd" to "tv_umpire_1"
        )
        val results = linker.linkPlayers(SourceCaId.from("tt1042158"), players, "bbl_json/1114870.json")

        expectThat(results).hasSize(2)
        val joshuaResult = results.find { it.sourcePersonId == SourcePersonId.from("umpire_1") }
        val shepherdResult = results.find { it.sourcePersonId == SourcePersonId.from("tv_umpire_1") }

        expectThat(joshuaResult?.statsPlayerId?.value).isEqualTo(201)
        expectThat(shepherdResult?.statsPlayerId?.value).isEqualTo(202)

        expectThat(cache[SourcePersonId.from("tv_umpire_1")]?.value).isEqualTo(202)
        expectThat(memoryDb[SourcePersonId.from("tv_umpire_1")]?.value).isEqualTo(202)
    }

    @Test
    fun `given empty roster when linking with source file then returns null player IDs`() {
        val statsRepository = object : StatsPlayerRepository {
            override fun playersForMatch(matchCaId: SourceCaId): List<StatsRosterPlayer> = emptyList()
        }
        val memoryDb = mutableMapOf<SourcePersonId, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<SourcePersonId, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: SourcePersonId, fullName: String, statsPlayerId: StatsPlayerId) = Unit
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val players = mapOf("Aaditya Varadarajan" to "person_varadarajan_3")
        val results = linker.linkPlayers(SourceCaId.from("a1355828"), players, "odis_json/1445038.json")

        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId).isNull()
    }
}

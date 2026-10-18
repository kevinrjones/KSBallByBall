package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.SourceCaId
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
        val memoryDb = mutableMapOf<String, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<String, StatsPlayerId> = memoryDb
            override fun savePlayerMapping(sourcePersonId: String, fullName: String, statsPlayerId: StatsPlayerId) {
                memoryDb[sourcePersonId] = statsPlayerId
            }
        }
        val cache = ThreadSafePlayerCache(
            mapOf("person_1" to StatsPlayerId.from(101), "person_2" to StatsPlayerId.from(102))
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
        val savedMappings = mutableMapOf<String, StatsPlayerId>()
        val dbRepository = object : PlayerIdentityRepository {
            override fun loadAllPlayerMappings(): Map<String, StatsPlayerId> = savedMappings
            override fun savePlayerMapping(sourcePersonId: String, fullName: String, statsPlayerId: StatsPlayerId) {
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
        expectThat(cache["person_smith"]?.value).isEqualTo(201)
        expectThat(savedMappings["person_smith"]?.value).isEqualTo(201)

        // Second lookup for the same player across another match should hit cache
        val secondResults = linker.linkPlayers(SourceCaId.from("a457"), matchPlayers)
        expectThat(statsQueryCount.get()).isEqualTo(1) // Still 1 query, no additional queries!
        expectThat(secondResults[0].statsPlayerId?.value).isEqualTo(201)
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
            override fun loadAllPlayerMappings(): Map<String, StatsPlayerId> = emptyMap()
            override fun savePlayerMapping(sourcePersonId: String, fullName: String, statsPlayerId: StatsPlayerId) = Unit
        }
        val cache = ThreadSafePlayerCache()
        val linker = StatsPlayerLinker(statsRepository, dbRepository, cache)

        val matchPlayers = mapOf("Unrelated Player" to "person_unknown")
        val results = linker.linkPlayers(SourceCaId.from("a789"), matchPlayers)

        expectThat(results).hasSize(1)
        expectThat(results[0].statsPlayerId).isNull()
    }
}

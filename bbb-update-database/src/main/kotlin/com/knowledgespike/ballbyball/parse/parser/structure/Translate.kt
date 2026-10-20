package com.knowledgespike.ballbyball.parse.parser.structure

import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.cricketarchive.InvalidStateException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Provides utility functions to transform and map data from a CricSheet object
 * into person-based structures, including registry details, players, and officials.
 */
object Translate {

    /**
     * Retrieves a list of persons from the given CricSheet object.
     *
     * @param cricSheet The CricSheet object containing registry information.
     * @return A list of Person objects constructed from the registry data in the CricSheet.
     */
    fun getPeople(cricSheet: BbbMatchData): List<Person> {
        val caIds = cricSheet.match.registry.peopleCaId.orEmpty()
        return cricSheet.match.registry.people.map { (name, id) ->
            val caId = caIds[id] ?: 0
            Person(id, name, caId)
        }
    }

    /**
     * Retrieves the players for each team from the specified JSON object and CricSheet data.
     *
     * The method processes the `players` property from the provided JSON object and maps it
     * to information from the CricSheet registry to produce a mapping of team names to their respective players.
     * Each player's existence and uniqueness are validated against the registry in the CricSheet.
     *
     * @param people A JSON object containing team-player mappings; each team is associated with a list of player names.
     * @param cricSheet An instance of the CricSheet data structure containing metadata and player registry information.
     * @return A map where the key is the team name as a string, and the value is a list of `Person` objects representing the players in that team.
     * @throws InvalidStateException If a player's name in the JSON object does not correspond to exactly one person in the CricSheet registry.
     */
    fun getPlayers(people: JsonObject, cricSheet: BbbMatchData): Map<String, List<Person>> {
        val peopleInRegistry = cricSheet.match.registry.people
        val caIds = cricSheet.match.registry.peopleCaId.orEmpty()
        return people.mapValues { (_, players) ->
            players.jsonArray.map { person ->
                val name = person.jsonPrimitive.content
                val id = peopleInRegistry[name]
                    ?: throw InvalidStateException("Should be one matching person in the registry, actually 0")
                val caId = caIds[id] ?: 0
                Person(id, name, caId)
            }
        }
    }

    /**
     * Extracts a list of `Person` objects corresponding to the given officials' names from a CricSheet registry.
     *
     * @param officials A list of official names as strings. Can be null, in which case an empty list will be returned.
     * @param cricSheet A `CricSheet` object containing the registry of people.
     * @return A list of `Person` objects that match the official names provided. Throws an `InvalidStateException` if the registry
     * contains zero or more than one match for a given official name.
     */
    fun getOfficials(officials: List<String>?, cricSheet: BbbMatchData): List<Person> {
        val peopleInRegistry = cricSheet.match.registry.people
        val caIds = cricSheet.match.registry.peopleCaId.orEmpty()
        return officials?.map { name ->
            val id = peopleInRegistry[name]
                ?: throw InvalidStateException("Should be one matching person in the registry, actually 0")
            val caId = caIds[id] ?: 0
            Person(id, name, caId)
        } ?: emptyList()
    }
}
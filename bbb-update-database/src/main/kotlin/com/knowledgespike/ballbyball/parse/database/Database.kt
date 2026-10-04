package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.clishared.identity.CanonicalMatchEnvelope
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.DeterministicIdentity

import com.knowledgespike.cricketarchive.InvalidStateException
import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.clishared.schema.Delivery
import com.knowledgespike.ballbyball.clishared.schema.Outcome
import com.knowledgespike.ballbyball.clishared.schema.PowerPlays
import com.knowledgespike.ballbyball.parse.database.adapter.*
import com.knowledgespike.ballbyball.parse.parser.structure.Person
import com.knowledgespike.ballbyball.parse.parser.structure.Translate
import com.knowledgespike.ballbyball.clishared.schema.Wickets
import java.time.LocalDate
import java.util.*
import java.util.stream.Stream

class Database(private val outputAdapter: OutputAdapter) {
    private val log by LoggerDelegate()

    companion object {
        const val UNKNOWN_PLAYER_ID = "unknown"
        const val SUBSTITUTE_PLAYER_NAME = "[substitute]"
        private val WOMEN_TEAM_NAME_EXCEPTIONS = setOf(
            "Women's Cricket Super League",
            "Women's T20 Challenge"
        )
    }


    fun writeMatch(fileName: String, envelope: CanonicalMatchEnvelope) {
        outputAdapter.beginMatch()
        try {
            log.debug("Parsing match: {}", fileName)
            val publicMatchId = DeterministicIdentity.publicMatchId(envelope.canonicalMatchId)
            if (!shouldParse(envelope.canonicalMatchId)) {
                outputAdapter.ensurePublicMatchId(envelope.canonicalMatchId, publicMatchId)
                check(!outputAdapter.sourceReferencesChanged(envelope.canonicalMatchId, envelope.sources)) {
                    "Source content changed for canonical match ${envelope.canonicalMatchId.value}; rebuild the relational database before importing corrections"
                }
                log.info("Match already exists: {}", envelope.canonicalMatchId.value)
                outputAdapter.commit()
                return
            }
            val cricSheet = envelope.match

            val teams = upsertTeams(
                cricSheet.match.teams.map { teamNameForMatch(it, cricSheet) }
            )
            Translate.getPeople(cricSheet).forEach { person ->
                upsertPerson(person.id, person.name, 0)
            }

            val players = Translate.getPlayers(cricSheet.match.players, cricSheet)
            val umpires = Translate.getOfficials(cricSheet.match.officials?.umpires, cricSheet)
            val tvUmpires = Translate.getOfficials(cricSheet.match.officials?.tvUmpires, cricSheet)
            val reserveUmpires = Translate.getOfficials(cricSheet.match.officials?.reserveUmpires, cricSheet)
            val matchReferees = Translate.getOfficials(cricSheet.match.officials?.matchReferees, cricSheet)
            val ground = upsertGround(cricSheet.match.venue ?: "")
            val match = addMatchToDatabase(fileName, envelope.canonicalMatchId, publicMatchId, teams, ground, cricSheet)
            outputAdapter.insertSourceReferences(match.id, envelope.sources)

            addMatchPeople(match, players.values.flatten(), "PLAYER")
            addMatchPeople(match, umpires, "UMPIRE")
            addMatchPeople(match, tvUmpires, "TV_UMPIRE")
            addMatchPeople(match, reserveUmpires, "RESERVE_UMPIRE")
            addMatchPeople(match, matchReferees, "MATCH_REFEREE")
            addBallByBall(fileName, match, teams, cricSheet)

            outputAdapter.commit()
        } catch (failure: Exception) {
            try {
                outputAdapter.rollback()
            } catch (rollbackFailure: Exception) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }

    private fun addBallByBall(
        fileName: String,
        match: MatchEntity,
        teams: List<Team>,
        cricSheet: BbbMatchData
    ) {
        val players = cricSheet.match.registry.people
        val matchDateKey = cricSheet.match.dates.firstOrNull()?.let(::parseDate)?.let(::upsertDate)
        var inningsOrder = 0

        cricSheet.innings.forEachIndexed { inningsIndex, inning ->
            log.debug("Parsing innings: {}, {}", inning.team, inningsIndex)
            val battingTeam = teams.find { it.name == teamNameForMatch(inning.team, cricSheet) }
                ?: throw InvalidStateException("Unknown batting team: ${inning.team}")
            val bowlingTeam = requireOpposingTeam(teams, battingTeam)
            val innings = upsertInnings(
                match.id,
                inningsIndex + 1,
                battingTeam.id,
                bowlingTeam.id,
                inning.declared == true
            )
            val powerplays = calculatePowerplays(inning.powerplays, cricSheet.match.ballsPerOver)
            var deliveryNumber = 0

            inning.overs.orEmpty().forEach { over ->
                over.deliveries.forEachIndexed { ballIndex, delivery ->
                    deliveryNumber++
                    inningsOrder++
                    val batterKey = requirePersonKey(players[delivery.batter], delivery.batter)
                    val nonStrikerKey = requirePersonKey(players[delivery.nonStriker], delivery.nonStriker)
                    val bowlerKey = requirePersonKey(players[delivery.bowler], delivery.bowler)
                    val powerplay = powerplays.firstOrNull { deliveryNumber in it.from..it.to }?.powerplayNumber ?: 0
                    val deliveryKey = insertDeliveryIfAbsent(
                        match = match,
                        matchDateKey = matchDateKey,
                        innings = innings,
                        battingTeam = battingTeam,
                        bowlingTeam = bowlingTeam,
                        batterKey = batterKey,
                        nonStrikerKey = nonStrikerKey,
                        bowlerKey = bowlerKey,
                        overNumber = over.over,
                        ballNumber = deliveryNumber,
                        ballInOver = ballIndex + 1,
                        inningsOrder = inningsOrder,
                        delivery = delivery,
                        powerplay = powerplay
                    )
                    if (deliveryKey != null) {
                        addWickets(fileName, deliveryKey, delivery.wickets.orEmpty(), players)
                    }
                }
            }
        }
    }

    private fun insertDeliveryIfAbsent(
        match: MatchEntity,
        matchDateKey: Int?,
        innings: InningsEntity,
        battingTeam: Team,
        bowlingTeam: Team,
        batterKey: Long,
        nonStrikerKey: Long,
        bowlerKey: Long,
        overNumber: Int,
        ballNumber: Int,
        ballInOver: Int,
        inningsOrder: Int,
        delivery: Delivery,
        powerplay: Int
    ): Long? {
        val existingKey = outputAdapter.findDeliveryKey(match.id, innings.id, inningsOrder)
        if (existingKey != null) return null

        return outputAdapter.insertDelivery(
            DeliveryRecord(
                matchKey = match.id,
                matchDateKey = matchDateKey,
                inningsKey = innings.id,
                battingTeamKey = battingTeam.id,
                bowlingTeamKey = bowlingTeam.id,
                batterKey = batterKey,
                nonStrikerKey = nonStrikerKey,
                bowlerKey = bowlerKey,
                overNumber = overNumber,
                ballNumber = ballNumber,
                ballInOver = ballInOver,
                inningsOrder = inningsOrder,
                delivery = delivery,
                powerplay = powerplay
            )
        )
    }

    private fun addWickets(fileName: String, deliveryKey: Long, wickets: List<Wickets>, people: Map<String, String>) {
        wickets.forEach { wicket ->
            val wicketKey = outputAdapter.insertWicket(wicket.kind)
            outputAdapter.insertDeliveryWicket(deliveryKey, wicketKey)
            val fielderKeys = mutableSetOf<Long>()
            wicket.fielders.orEmpty().forEach { fielder ->
                val fielderName = fielder.name
                val fielderKey = if (fielderName != null) {
                    requirePersonKey(people[fielderName], fielderName)
                } else if (fielder.substitute == true) {
                    upsertPerson(UNKNOWN_PLAYER_ID, SUBSTITUTE_PLAYER_NAME, 0)
                } else {
                    throw InvalidStateException("Fielder has no name for wicket ${wicket.kind}")
                }
                if (!fielderKeys.add(fielderKey)) {
                    log.warn(
                        "Duplicate wicket fielder suppressed: fileName={}, deliveryKey={}, wicketKey={}, personKey={}, " +
                                "wicketKind={}, fielderName={}",
                        fileName,
                        deliveryKey,
                        wicketKey,
                        fielderKey,
                        wicket.kind,
                        fielder.name ?: SUBSTITUTE_PLAYER_NAME
                    )
                } else {
                    outputAdapter.insertDeliveryFielder(deliveryKey, wicketKey, fielderKey)
                }
            }
        }
    }

    private fun calculatePowerplays(powerplays: List<PowerPlays>?, ballsPersOVer: Int): List<PowerplayDetails> {
        return powerplays?.mapIndexed { index, powerplay ->
            val fromBall = calculateBallFromOver(powerplay.from, ballsPersOVer)
            val toBall = calculateBallFromOver(powerplay.to, ballsPersOVer)
            PowerplayDetails(index + 1, fromBall, toBall, powerplay.type)
        } ?: emptyList()
    }

    private fun calculateBallFromOver(over: String, ballsPersOVer: Int): Int {
        val parts = over.split(".")
        val overs = parts[0].toInt()
        val balls = parts[1].toInt()
        return (overs * ballsPersOVer) + balls // assuming 6 ball over,
    }

    private fun addMatchPeople(match: MatchEntity, people: List<Person>, roleCode: String) {
        people.forEach { person ->
            val personKey = requirePersonKey(person.id, person.name)
            outputAdapter.insertMatchPerson(match.id, personKey, roleCode)
        }
    }

    private fun addMatchToDatabase(
        fileName: String,
        canonicalMatchId: CanonicalMatchId,
        publicMatchId: com.knowledgespike.ballbyball.types.values.PublicMatchId,
        teamsWithId: List<Team>,
        location: Ground,
        cricSheet: BbbMatchData
    ): MatchEntity {
        val eventName = cricSheet.match.event?.name ?: ""
        val eventMatch = cricSheet.match.event?.matchNumber ?: 0
        val matchType = cricSheet.match.matchType
        val teams = cricSheet.match.teams.map { teamNameForMatch(it, cricSheet) }
        val homeTeam = teamsWithId.find { it.name == teams[0] }
            ?: throw InvalidStateException("Unknown home team: ${teams[0]}")
        val awayTeam = teamsWithId.find { it.name == teams[1] }
            ?: throw InvalidStateException("Unknown away team: ${teams[1]}")
        val matchDate = cricSheet.match.dates.joinToString(separator = ";")
        val matchStartDate = cricSheet.match.dates.firstOrNull()?.let(::parseDate)
        val matchStartDateKey = matchStartDate?.let(::upsertDate)
        val duration = cricSheet.match.dates.size
        val ballsPerOver = cricSheet.match.ballsPerOver
        val toss = teamsWithId.find {
            it.name == teamNameForMatch(cricSheet.match.toss.winner, cricSheet)
        }
            ?: throw InvalidStateException("Unknown toss winner: ${cricSheet.match.toss.winner}")
        val tossDecision = cricSheet.match.toss.decision
        val victoryType = getVictoryType(cricSheet.match.outcome)
        val margin = getMargin(cricSheet.match.outcome)
        val winner = cricSheet.match.outcome.winner?.let { winnerName ->
            teamsWithId.find { it.name == teamNameForMatch(winnerName, cricSheet) }
        }
        val loser = winner?.let { winningTeam -> requireOpposingTeam(teamsWithId, winningTeam) }

        val match = outputAdapter.insertMatch(
            MatchRecord(
                canonicalMatchId = canonicalMatchId,
                publicMatchId = publicMatchId,
                fileName = fileName,
                matchInSeries = eventMatch,
                matchType = matchType,
                eventName = eventName,
                matchDateText = matchDate,
                season = cricSheet.match.season,
                matchStartYear = matchStartDate?.year?.toString() ?: "",
                matchStartDateKey = matchStartDateKey,
                ballsPerOver = ballsPerOver,
                durationDays = duration,
                margin = margin,
                team1Key = homeTeam.id,
                team2Key = awayTeam.id,
                groundKey = location.id,
                tossTeamKey = toss.id,
                tossDecision = tossDecision,
                victoryType = victoryType,
                winnerTeamKey = winner?.id,
                loserTeamKey = loser?.id
            )
        )
        return match

    }

    private fun getMargin(outcome: Outcome): Int {
        val by = outcome.by ?: return 0

        return by.runs ?: by.wickets ?: by.innings ?: 0
    }

    private fun getVictoryType(outcome: Outcome): String {
        val by = outcome.by

        val result = outcome.result
        if (result != null) return result

        return if (by == null) "unknown"
        else if (by.innings != null) "innings"
        else if (by.wickets != null) "wickets"
        else if (by.runs != null) "runs"
        else "unknown"
    }

    private fun upsertGround(name: String): Ground {
        return outputAdapter.upsertGround(name)
    }

    private fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        return outputAdapter.upsertPerson(sourceId, fullName, caId)
    }


    private fun upsertTeams(teams: List<String>): List<Team> {
        return teams.map(outputAdapter::upsertTeam)
    }

    private fun teamNameForMatch(
        teamName: String,
        cricSheet: BbbMatchData
    ): String {
        if (!shouldAppendWomenToTeamName(cricSheet) || teamName.endsWith(" Women")) return teamName
        return "$teamName Women"
    }

    private fun shouldAppendWomenToTeamName(cricSheet: BbbMatchData): Boolean {
        return cricSheet.match.gender.equals("female", ignoreCase = true) &&
                cricSheet.match.event?.name !in WOMEN_TEAM_NAME_EXCEPTIONS
    }

    fun writeAllPeople(people: Stream<PersonEntity>) {
        outputAdapter.writeAllPeople(people.iterator().asSequence())
        outputAdapter.commit()
    }

    private fun upsertDate(date: LocalDate): Int {
        return outputAdapter.upsertDate(date)
    }

    private fun upsertInnings(
        matchKey: Long,
        inningsNumber: Int,
        battingTeamKey: Long,
        bowlingTeamKey: Long,
        declared: Boolean
    ): InningsEntity {
        return outputAdapter.upsertInnings(
            matchKey,
            inningsNumber,
            battingTeamKey,
            bowlingTeamKey,
            declared
        )
    }

    private fun requirePersonKey(sourceId: String?, displayName: String): Long {
        if (sourceId == null)
            throw InvalidStateException("Person is not registered: $displayName")
        return upsertPerson(sourceId, displayName, 0)
    }

    private fun parseDate(value: String): LocalDate = LocalDate.parse(value)

    fun shouldParse(canonicalMatchId: CanonicalMatchId): Boolean {
        return outputAdapter.findMatchKey(canonicalMatchId) == null
    }

    /**
     * Relational matches are modeled as exactly two sides. The bowling (or losing) side is
     * the other registered team for the match, not a free-form third participant.
     */
    private fun requireOpposingTeam(teams: List<Team>, selected: Team): Team =
        teams.firstOrNull { it.id != selected.id }
            ?: error("Match must include an opposing team for ${selected.name}")

}

fun getNameParts(personName: String): Pair<String, String> {
    val sortNamePart: String
    val otherNamePart: String

    val fullName = if (personName.contains("(")) {
        personName.substringBefore("(").trim()
    } else {
        personName
    }

    if (!fullName.contains(" ")) {
        otherNamePart = ""
        sortNamePart = fullName.replace("'", "")
    } else {
        val parts = fullName.split(" ")

        // Assume initials are all upper case of name would be
        // GS Sobers for example
        when {
            parts[0].uppercase(Locale.getDefault()) == parts[0] -> {
                val others = parts.drop(1)
                otherNamePart = parts[0].trim()
                sortNamePart = others.joinToString("").replace(" ", "").replace("'", "")
            }

            parts[0] == "Lord" -> {
                // lord hawke
                // lord j graham
                val others = parts.drop(1)
                val sort = parts.last()
                otherNamePart = others.joinToString(" ")
                sortNamePart = sort.replace(" ", "").replace("'", "")
            }

            (parts[0] == "Sir" || parts[0] == "Earl" || parts[0] == "Duke" || parts[0] == "Nawab") && parts[1] == "of" -> {
                val others = parts.dropLast(1)
                val sort = parts.drop(2)
                otherNamePart = others.joinToString(" ")
                sortNamePart = sort[0].replace(" ", "").replace("'", "")
            }

            else -> {
                // full name is the sort name
                // e.g. Majid Khan
                otherNamePart = ""
                sortNamePart = fullName.replace(" ", "").replace("'", "")
            }
        }
    }
    return Pair(sortNamePart, otherNamePart)


}

data class PowerplayDetails(val powerplayNumber: Int, val from: Int, val to: Int, val type: String)
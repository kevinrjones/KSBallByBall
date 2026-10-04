package com.knowledgespike.ballbyball.parse.database.adapter.csv

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.types.values.PublicMatchId

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.knowledgespike.ballbyball.parse.database.Ground
import com.knowledgespike.ballbyball.parse.database.PersonEntity
import com.knowledgespike.ballbyball.parse.database.Team
import com.knowledgespike.ballbyball.parse.database.InningsEntity
import com.knowledgespike.ballbyball.parse.database.MatchEntity
import com.knowledgespike.ballbyball.parse.database.adapter.OutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.DeliveryRecord
import com.knowledgespike.ballbyball.parse.database.adapter.MatchWriteDecision
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.ballbyball.parse.database.adapter.RelationalWriteSupport
import com.knowledgespike.ballbyball.parse.database.adapter.fingerprint
import com.knowledgespike.ballbyball.parse.database.getNameParts
import java.io.BufferedWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Writes relational tables as UTF-8 CSV files suitable for bulk loading into MariaDB. */
class CsvOutputAdapter(output: Path) : OutputAdapter {
    private val log by LoggerDelegate()
    private val writers = mutableMapOf<String, BufferedWriter>()
    private val people = mutableMapOf<String, Long>()
    private val teams = mutableMapOf<String, Team>()
    private val grounds = mutableMapOf<String, Ground>()
    private val dates = mutableMapOf<LocalDate, Int>()
    private val matches = mutableMapOf<CanonicalMatchId, Long>()
    private val matchPublicIds = mutableMapOf<CanonicalMatchId, PublicMatchId>()
    private val publicMatches = mutableMapOf<PublicMatchId, CanonicalMatchId>()
    private val sourceReferences = mutableMapOf<Long, Set<String>>()
    private val innings = mutableMapOf<Pair<Long, Int>, InningsEntity>()
    private val deliveries = mutableMapOf<Triple<Long, Long, Int>, Long>()
    private val matchPeople = mutableSetOf<Triple<Long, Long, String>>()
    private val deliveryFielders = mutableSetOf<Triple<Long, Long, Long>>()
    private var nextTeamSourceId = 1L
    private var nextGroundSourceId = 1L
    private var nextBallSourceId = 1L
    private var nextWicketSourceId = 1L
    private var nextPersonKey = 1L
    private var nextTeamKey = 1L
    private var nextGroundKey = 1L
    private var nextMatchKey = 1L
    private var nextInningsKey = 1L
    private var nextDeliveryKey = 1L
    private var nextWicketKey = 1L
    private var closed = false
    private val path: Path = prepareOutput(output)

    override fun findMatchKey(canonicalMatchId: CanonicalMatchId): Long? = matches[canonicalMatchId]

    override fun ensurePublicMatchId(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) {
        decideMatchWrite(canonicalMatchId, publicMatchId)
    }

    override fun sourceReferencesChanged(
        canonicalMatchId: CanonicalMatchId,
        sources: List<SourceReference>
    ): Boolean {
        val matchKey = findMatchKey(canonicalMatchId) ?: return false
        val existing = sourceReferences[matchKey] ?: return false
        return existing != sources.map { fingerprint(it) }.toSet()
    }

    override fun upsertPerson(sourceId: String, fullName: String, caId: Int): Long {
        people[sourceId]?.let { return it }
        val key = nextPersonKey++
        val (sortNamePart, otherNamePart) = getNameParts(fullName)
        writeRow(
            "people",
            RelationalWriteSupport.PERSON_COLUMNS,
            listOf(key, sourceId, fullName, sortNamePart, otherNamePart, caId)
        )
        people[sourceId] = key
        return key
    }

    override fun upsertTeam(name: String): Team {
        teams[name]?.let { return it }
        val team = Team(nextTeamKey++, name)
        writeRow("teams", RelationalWriteSupport.TEAM_COLUMNS, listOf(team.id, nextTeamSourceId++, team.name))
        teams[name] = team
        return team
    }

    override fun upsertGround(name: String): Ground {
        grounds[name]?.let { return it }
        val ground = Ground(nextGroundKey++, name)
        writeRow("grounds", RelationalWriteSupport.GROUND_COLUMNS, listOf(ground.id, nextGroundSourceId++, ground.name))
        grounds[name] = ground
        return ground
    }

    override fun upsertDate(date: LocalDate): Int {
        dates[date]?.let { return it }
        val dimensions = RelationalWriteSupport.dateDimensions(date)
        writeRow(
            "dates",
            RelationalWriteSupport.DATE_COLUMNS,
            dimensions.values()
        )
        dates[date] = dimensions.dateKey
        return dimensions.dateKey
    }

    override fun insertMatch(match: MatchRecord): MatchEntity {
        val key = when (val decision = decideMatchWrite(match.canonicalMatchId, match.publicMatchId)) {
            is MatchWriteDecision.Reuse -> return MatchEntity(decision.matchKey, match.publicMatchId)
            MatchWriteDecision.Insert -> nextMatchKey++
        }
        writeRow(
            "matches",
            RelationalWriteSupport.MATCH_COLUMNS,
            RelationalWriteSupport.matchValues(
                key,
                match,
                LocalDateTime.now(java.time.ZoneOffset.UTC)
            )
        )
        matches[match.canonicalMatchId] = key
        matchPublicIds[match.canonicalMatchId] = match.publicMatchId
        publicMatches[match.publicMatchId] = match.canonicalMatchId
        return MatchEntity(key, match.publicMatchId)
    }

    override fun insertSourceReferences(matchId: Long, sources: List<SourceReference>) {
        sourceReferences[matchId] = sources.map { fingerprint(it) }.toSet()
        sources.forEach { source ->
            writeRow(
                "match_source_reference",
                RelationalWriteSupport.SOURCE_REFERENCE_COLUMNS,
                RelationalWriteSupport.sourceReferenceValues(matchId, source)
            )
        }
    }


    override fun upsertInnings(
        matchId: Long,
        inningsNumber: Int,
        battingTeamId: Long,
        bowlingTeamId: Long,
        declared: Boolean
    ): InningsEntity {
        val lookup = matchId to inningsNumber
        innings[lookup]?.let { return it }
        val result = InningsEntity(nextInningsKey++)
        writeRow(
            "innings",
            RelationalWriteSupport.INNINGS_COLUMNS,
            RelationalWriteSupport.inningsValues(
                inningsKey = result.id,
                matchId = matchId,
                inningsNumber = inningsNumber,
                battingTeamId = battingTeamId,
                bowlingTeamId = bowlingTeamId,
                declared = declared
            )
        )
        innings[lookup] = result
        return result
    }

    override fun findDeliveryKey(matchId: Long, inningsId: Long, inningsOrder: Int): Long? =
        deliveries[Triple(matchId, inningsId, inningsOrder)]

    override fun insertDelivery(delivery: DeliveryRecord): Long {
        val key = nextDeliveryKey++
        writeRow(
            "deliveries",
            RelationalWriteSupport.DELIVERY_COLUMNS,
            RelationalWriteSupport.deliveryValues(key, nextBallSourceId++, delivery)
        )
        deliveries[Triple(delivery.matchKey, delivery.inningsKey, delivery.inningsOrder)] = key
        return key
    }

    override fun insertWicket(kind: String): Long {
        val key = nextWicketKey++
        writeRow("wickets", RelationalWriteSupport.WICKET_COLUMNS, listOf(key, nextWicketSourceId++, kind))
        return key
    }

    override fun insertDeliveryWicket(deliveryId: Long, wicketId: Long) {
        writeRow(
            "delivery_wickets",
            RelationalWriteSupport.DELIVERY_WICKET_COLUMNS,
            listOf(deliveryId, wicketId)
        )
    }

    override fun insertDeliveryFielder(deliveryId: Long, wicketId: Long, personId: Long) {
        val bridgeKey = Triple(deliveryId, wicketId, personId)
        if (!deliveryFielders.add(bridgeKey)) {
            log.warn(
                "Duplicate delivery_fielder suppressed from CSV output: deliveryId={}, wicketId={}, personId={}",
                deliveryId,
                wicketId,
                personId
            )
            return
        }
        writeRow(
            "delivery_fielders",
            RelationalWriteSupport.DELIVERY_FIELDER_COLUMNS,
            listOf(deliveryId, wicketId, personId)
        )
    }

    override fun insertMatchPerson(matchId: Long, personId: Long, roleCode: String) {
        if (matchPeople.add(Triple(matchId, personId, roleCode))) {
            writeRow("match_people", RelationalWriteSupport.MATCH_PERSON_COLUMNS, listOf(null, matchId, personId, roleCode))
        }
    }

    override fun writeAllPeople(people: Sequence<PersonEntity>) {
        people.forEach { person -> upsertPerson(person.id, person.name, person.caId) }
    }

    override fun commit() {
        writers.values.forEach(BufferedWriter::flush)
    }

    override fun close() {
        if (closed) return
        closed = true
        commit()
        writers.values.forEach(BufferedWriter::close)
        writers.clear()
    }

    private fun decideMatchWrite(canonicalMatchId: CanonicalMatchId, publicMatchId: PublicMatchId) =
        RelationalWriteSupport.matchWriteDecision(
            canonicalMatchId = canonicalMatchId,
            publicMatchId = publicMatchId,
            existingMatchKey = matches[canonicalMatchId],
            existingPublicMatchId = matchPublicIds[canonicalMatchId]?.value,
            publicIdOwnedByAnotherMatch = publicMatches[publicMatchId]?.let { it != canonicalMatchId } ?: false
        )

    private fun writeRow(tableName: String, headers: List<String>, values: List<Any?>) {
        check(!closed) { "Cannot write to a closed CSV output adapter" }
        val writer = writers.getOrPut(tableName) {
            val file = path.resolve("$tableName.csv")
            val newFile = !Files.exists(file) || Files.size(file) == 0L
            Files.newBufferedWriter(
                file,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            ).also { if (newFile) it.appendLine(headers.joinToString(",")) }
        }
        writer.appendLine(values.joinToString(",", transform = ::toCsvValue))
    }

    private fun toCsvValue(value: Any?): String = when (value) {
        null -> "\\N"
        is Boolean -> if (value) "1" else "0"
        is LocalDateTime -> value.format(DATETIME_FORMATTER)
        is String -> escapeCsv(value)
        else -> value.toString()
    }

    private fun escapeCsv(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private companion object {
        val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun prepareOutput(output: Path): Path {
            require(!Files.exists(output) || Files.isDirectory(output)) {
                "CSV output path must be a directory: $output"
            }
            if (Files.exists(output)) output.toFile().deleteRecursively()
            Files.createDirectories(output)
            return output
        }

    }
}
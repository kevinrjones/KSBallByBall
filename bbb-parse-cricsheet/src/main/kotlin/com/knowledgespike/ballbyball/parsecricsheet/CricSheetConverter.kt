package com.knowledgespike.ballbyball.parsecricsheet

import com.knowledgespike.ballbyball.identity.DeterministicIdentity
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.ProviderNamespaces
import com.knowledgespike.ballbyball.clishared.identity.SourceMatchEnvelope
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.clishared.schema.BbbMatchData
import com.knowledgespike.ballbyball.clishared.schema.By as BbbBy
import com.knowledgespike.ballbyball.clishared.schema.BowlOut as BbbBowlOut
import com.knowledgespike.ballbyball.clishared.schema.Delivery as BbbDelivery
import com.knowledgespike.ballbyball.clishared.schema.Event as BbbEvent
import com.knowledgespike.ballbyball.clishared.schema.Extras as BbbExtras
import com.knowledgespike.ballbyball.clishared.schema.Info as BbbInfo
import com.knowledgespike.ballbyball.clishared.schema.Innings as BbbInnings
import com.knowledgespike.ballbyball.clishared.schema.Match as BbbMatch
import com.knowledgespike.ballbyball.clishared.schema.Officials as BbbOfficials
import com.knowledgespike.ballbyball.clishared.schema.Outcome as BbbOutcome
import com.knowledgespike.ballbyball.clishared.schema.Over as BbbOver
import com.knowledgespike.ballbyball.clishared.schema.PenaltyRuns as BbbPenaltyRuns
import com.knowledgespike.ballbyball.clishared.schema.Player as BbbPlayer
import com.knowledgespike.ballbyball.clishared.schema.PlayersRegistry as BbbPlayersRegistry
import com.knowledgespike.ballbyball.clishared.schema.PowerPlays as BbbPowerPlays
import com.knowledgespike.ballbyball.clishared.schema.Replacements as BbbReplacements
import com.knowledgespike.ballbyball.clishared.schema.Review as BbbReview
import com.knowledgespike.ballbyball.clishared.schema.Role as BbbRole
import com.knowledgespike.ballbyball.clishared.schema.Runs as BbbRuns
import com.knowledgespike.ballbyball.clishared.schema.Target as BbbTarget
import com.knowledgespike.ballbyball.clishared.schema.Toss as BbbToss
import com.knowledgespike.ballbyball.clishared.schema.Wickets as BbbWickets
import com.knowledgespike.ballbyball.parsecricsheet.source.CricSheet
import com.knowledgespike.ballbyball.parsecricsheet.source.Delivery as CricSheetDelivery
import com.knowledgespike.ballbyball.parsecricsheet.source.Info as CricSheetInfo
import com.knowledgespike.ballbyball.parsecricsheet.source.Innings as CricSheetInnings
import com.knowledgespike.ballbyball.parsecricsheet.source.Over as CricSheetOver
import com.knowledgespike.ballbyball.parsecricsheet.source.Replacements as CricSheetReplacements
import kotlinx.serialization.json.Json

class CricSheetConverter(
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }
) {
    fun convert(rawBytes: ByteArray, providerRecordKey: String): SourceMatchEnvelope {
        require(PROVIDER_RECORD_KEY.matches(providerRecordKey)) {
            "Cricsheet provider record key must be a safe basename stem: $providerRecordKey"
        }
        val rawContentDigest = DeterministicIdentity.rawContentDigest(rawBytes)
        val cricSheet = json.decodeFromString<CricSheet>(rawBytes.decodeToString())
        return SourceMatchEnvelope(
            source = SourceReference(
                provider = CRICSHEET_PROVIDER,
                providerRecordKey = providerRecordKey,
                sourceRecordId = DeterministicIdentity.sourceRecordId(
                    namespace = ProviderNamespaces.CRICSHEET,
                    providerRecordKey = providerRecordKey
                ),
                rawContentDigest = rawContentDigest
            ),
            match = convert(cricSheet)
        )
    }

    fun convert(cricSheet: CricSheet): BbbMatchData {
        return BbbMatchData(
            match = mapInfo(cricSheet.info),
            innings = cricSheet.innings.map(::mapInnings)
        )
    }

    private fun mapInfo(info: CricSheetInfo): BbbInfo = BbbInfo(
        ballsPerOver = info.ballsPerOver,
        bowlOut = info.bowlOut?.map { BbbBowlOut(bowler = it.bowler, outcome = it.outcome) },
        city = info.city,
        dates = info.dates,
        event = info.event?.let {
            BbbEvent(name = it.name, matchNumber = it.matchNumber, group = it.group, stage = it.stage)
        },
        gender = info.gender,
        matchType = warehouseMatchType(info.matchType, info.gender),
        matchTypeNumber = info.matchTypeNumber,
        missing = info.missing,
        officials = info.officials?.let {
            BbbOfficials(
                matchReferees = it.matchReferees,
                reserveUmpires = it.reserveUmpires,
                tvUmpires = it.tvUmpires,
                umpires = it.umpires
            )
        },
        outcome = BbbOutcome(
            by = info.outcome.by?.let {
                BbbBy(innings = it.innings, runs = it.runs, wickets = it.wickets)
            },
            bowlOut = info.outcome.bowlOut,
            eliminator = info.outcome.eliminator,
            method = info.outcome.method,
            result = info.outcome.result,
            winner = info.outcome.winner
        ),
        overs = info.overs,
        playerOfMatch = info.playerOfMatch,
        players = info.players,
        registry = BbbPlayersRegistry(people = info.registry.people),
        season = info.season,
        superSubs = info.superSubs,
        teamType = info.teamType,
        teams = info.teams,
        toss = BbbToss(
            uncontested = info.toss.uncontested,
            decision = info.toss.decision,
            winner = info.toss.winner
        ),
        venue = info.venue
    )

    private fun mapInnings(innings: CricSheetInnings): BbbInnings = BbbInnings(
        team = innings.team,
        overs = innings.overs?.map(::mapOver),
        absentHurt = innings.absentHurt,
        penaltyRuns = innings.penaltyRuns?.let { BbbPenaltyRuns(pre = it.pre, post = it.post) },
        declared = innings.declared,
        forfeited = innings.forfeited,
        powerplays = innings.powerplays?.map { BbbPowerPlays(from = it.from, to = it.to, type = it.type) },
        miscountedOvers = innings.miscountedOvers,
        target = innings.target?.let { BbbTarget(overs = it.overs, runs = it.runs) },
        superOver = innings.superOver
    )

    private fun mapOver(over: CricSheetOver): BbbOver = BbbOver(
        over = over.over,
        deliveries = over.deliveries.map(::mapDelivery)
    )

    private fun mapDelivery(delivery: CricSheetDelivery): BbbDelivery = BbbDelivery(
        batter = delivery.batter,
        bowler = delivery.bowler,
        extras = delivery.extras?.let {
            BbbExtras(
                byes = it.byes,
                legbyes = it.legbyes,
                noballs = it.noballs,
                penalty = it.penalty,
                wides = it.wides
            )
        },
        nonStriker = delivery.nonStriker,
        replacements = delivery.replacements?.let(::mapReplacements),
        review = delivery.review?.let {
            BbbReview(
                batter = it.batter,
                by = it.by,
                decision = it.decision,
                umpire = it.umpire,
                umpiresCall = it.umpiresCall
            )
        },
        runs = BbbRuns(
            batter = delivery.runs.batter,
            extras = delivery.runs.extras,
            nonBoundary = delivery.runs.nonBoundary,
            total = delivery.runs.total
        ),
        wickets = delivery.wickets?.map { wicket ->
            BbbWickets(
                fielders = wicket.fielders?.map { BbbPlayer(name = it.name, substitute = it.substitute) },
                kind = wicket.kind,
                playerOut = wicket.playerOut
            )
        }
    )

    private fun mapReplacements(replacements: CricSheetReplacements): BbbReplacements = BbbReplacements(
        match = replacements.match?.map {
            BbbMatch(cameIn = it.cameIn, wentOut = it.wentOut, reason = it.reason, team = it.team)
        },
        role = replacements.role?.map {
            BbbRole(cameIn = it.cameIn, wentOut = it.wentOut, reason = it.reason, role = it.role)
        }
    )

    private fun warehouseMatchType(sourceMatchType: String, gender: String): String {
        val matchType = when (sourceMatchType.lowercase()) {
            "test" -> "t"
            "t20" -> "tt"
            "it20" -> "itt"
            "odi" -> "o"
            "odm" -> "a"
            "mdm" -> "f"
            else -> throw IllegalArgumentException("Unknown match type $sourceMatchType")
        }
        return if (gender.equals("female", ignoreCase = true)) "w$matchType" else matchType
    }

    private companion object {
        val CRICSHEET_PROVIDER = ProviderId.from("cricsheet")
        val PROVIDER_RECORD_KEY = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
    }
}
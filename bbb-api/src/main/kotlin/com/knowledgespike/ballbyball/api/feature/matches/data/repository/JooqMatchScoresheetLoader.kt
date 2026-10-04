package com.knowledgespike.ballbyball.api.feature.matches.data.repository

import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetContext
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetDelivery
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetInnings
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetPage
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetWicket
import com.knowledgespike.ballbyball.api.generated.jooq.tables.Dates
import com.knowledgespike.ballbyball.api.generated.jooq.tables.Deliveries
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DeliveryFielders
import com.knowledgespike.ballbyball.api.generated.jooq.tables.DeliveryWickets
import com.knowledgespike.ballbyball.api.generated.jooq.tables.Grounds
import com.knowledgespike.ballbyball.api.generated.jooq.tables.Innings
import com.knowledgespike.ballbyball.api.generated.jooq.tables.Matches
import com.knowledgespike.ballbyball.api.generated.jooq.tables.People
import com.knowledgespike.ballbyball.api.generated.jooq.tables.Teams
import com.knowledgespike.ballbyball.api.generated.jooq.tables.Wickets
import com.knowledgespike.ballbyball.types.values.MatchType
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.ballbyball.types.values.Season
import kotlinx.coroutines.CoroutineDispatcher
import org.jooq.DSLContext
import org.jooq.impl.DSL.count
import org.slf4j.LoggerFactory

/** Loads scoresheet context, innings, deliveries, and wicket graph for one public match id. */
internal class JooqMatchScoresheetLoader(
    private val dsl: DSLContext,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val log = LoggerFactory.getLogger(JooqMatchScoresheetLoader::class.java)

    private val dimMatch = Matches.MATCHES
    private val dimDate = Dates.DATES
    private val dimTeam1 = Teams.TEAMS.`as`("t1")
    private val dimTeam2 = Teams.TEAMS.`as`("t2")
    private val dimGround = Grounds.GROUNDS
    private val dimTeamWinner = Teams.TEAMS.`as`("tw")
    private val dimInnings = Innings.INNINGS
    private val factDelivery = Deliveries.DELIVERIES
    private val dimInningsBattingTeam = Teams.TEAMS.`as`("ib")
    private val dimInningsBowlingTeam = Teams.TEAMS.`as`("io")
    private val dimPersonBatter = People.PEOPLE.`as`("pb")
    private val dimPersonNonStriker = People.PEOPLE.`as`("pns")
    private val dimPersonBowler = People.PEOPLE.`as`("pbo")
    private val dimPersonFielder = People.PEOPLE.`as`("pf")
    private val dimWicket = Wickets.WICKETS
    private val bridgeDeliveryWicket = DeliveryWickets.DELIVERY_WICKETS
    private val bridgeDeliveryFielder = DeliveryFielders.DELIVERY_FIELDERS

    private val mMatchKey = dimMatch.ID
    private val mPublicMatchId = dimMatch.PUBLIC_MATCH_ID
    private val mFileName = dimMatch.FILE_NAME
    private val mMatchType = dimMatch.MATCH_TYPE
    private val mEventName = dimMatch.EVENT_NAME
    private val mMatchDateText = dimMatch.MATCH_DATE_TEXT
    private val mSeason = dimMatch.SEASON
    private val mTeam1Key = dimMatch.TEAM1_ID
    private val mTeam2Key = dimMatch.TEAM2_ID
    private val mGroundKey = dimMatch.GROUND_ID
    private val mWinnerTeamKey = dimMatch.WINNER_TEAM_ID
    private val mVictoryType = dimMatch.VICTORY_TYPE
    private val mMatchStartDateKey = dimMatch.MATCH_START_DATE_ID
    private val dDateKey = dimDate.DATE_ID
    private val dCalendarDate = dimDate.CALENDAR_DATE
    private val t1TeamKey = dimTeam1.ID
    private val t1TeamName = dimTeam1.TEAM_NAME
    private val t2TeamKey = dimTeam2.ID
    private val t2TeamName = dimTeam2.TEAM_NAME
    private val gGroundKey = dimGround.ID
    private val gGroundName = dimGround.GROUND_NAME
    private val twTeamKey = dimTeamWinner.ID
    private val twTeamName = dimTeamWinner.TEAM_NAME
    private val fmMatchKey = dimMatch.ID
    private val fmMargin = dimMatch.MARGIN
    private val iInningsKey = dimInnings.ID
    private val iInningsNumber = dimInnings.INNINGS_NUMBER
    private val iMatchKey = dimInnings.MATCH_ID
    private val iBattingTeamKey = dimInnings.BATTING_TEAM_ID
    private val iBowlingTeamKey = dimInnings.BOWLING_TEAM_ID
    private val iDeclared = dimInnings.DECLARED
    private val fdMatchKey = factDelivery.MATCH_ID
    private val fdInningsKey = factDelivery.INNINGS_ID
    private val fdDeliveryKey = factDelivery.ID
    private val fdSourceBallId = factDelivery.SOURCE_BALL_ID
    private val fdInningsOrder = factDelivery.INNINGS_ORDER
    private val fdOverNumber = factDelivery.OVER_NUMBER
    private val fdBallNumber = factDelivery.BALL_NUMBER
    private val fdBallInOver = factDelivery.BALL_IN_OVER
    private val fdBatterKey = factDelivery.BATTER_ID
    private val fdNonStrikerKey = factDelivery.NON_STRIKER_ID
    private val fdBowlerKey = factDelivery.BOWLER_ID
    private val fdBatterRuns = factDelivery.BATTER_RUNS
    private val fdExtraRuns = factDelivery.EXTRA_RUNS
    private val fdTotalRuns = factDelivery.TOTAL_RUNS
    private val fdNoBalls = factDelivery.NO_BALLS
    private val fdWides = factDelivery.WIDES
    private val fdByes = factDelivery.BYES
    private val fdLegByes = factDelivery.LEG_BYES
    private val fdNonBoundary = factDelivery.NON_BOUNDARY
    private val fdPowerplay = factDelivery.POWERPLAY
    private val fdWicketCount = factDelivery.WICKET_COUNT
    private val pbPersonKey = dimPersonBatter.ID
    private val pbFullName = dimPersonBatter.FULL_NAME
    private val pnsPersonKey = dimPersonNonStriker.ID
    private val pnsFullName = dimPersonNonStriker.FULL_NAME
    private val pboPersonKey = dimPersonBowler.ID
    private val pboFullName = dimPersonBowler.FULL_NAME
    private val pfPersonKey = dimPersonFielder.ID
    private val pfFullName = dimPersonFielder.FULL_NAME
    private val ibTeamKey = dimInningsBattingTeam.ID
    private val ibTeamName = dimInningsBattingTeam.TEAM_NAME
    private val ioTeamKey = dimInningsBowlingTeam.ID
    private val ioTeamName = dimInningsBowlingTeam.TEAM_NAME
    private val dwDeliveryKey = bridgeDeliveryWicket.DELIVERY_ID
    private val dwWicketKey = bridgeDeliveryWicket.WICKET_ID
    private val dfDeliveryKey = bridgeDeliveryFielder.DELIVERY_ID
    private val dfWicketKey = bridgeDeliveryFielder.WICKET_ID
    private val dfPersonKey = bridgeDeliveryFielder.PERSON_ID
    private val wWicketKey = dimWicket.ID
    private val wKind = dimWicket.WICKET_KIND

    suspend fun load(
        publicMatchId: PublicMatchId
    ): MatchScoresheetPage? =
        withMatchQuery(log, ioDispatcher, "Historical match scoresheet query") {
            val match = dsl.select(
                mPublicMatchId,
                mMatchKey,
                mFileName,
                mMatchType,
                mSeason,
                mEventName,
                mMatchDateText,
                dCalendarDate,
                mTeam1Key,
                mTeam2Key,
                mWinnerTeamKey,
                mVictoryType,
                t1TeamName,
                t2TeamName,
                gGroundName,
                fmMargin
            )
                .from(dimMatch)
                .leftJoin(dimDate).on(mMatchStartDateKey.eq(dDateKey))
                .leftJoin(dimTeam1).on(mTeam1Key.eq(t1TeamKey))
                .leftJoin(dimTeam2).on(mTeam2Key.eq(t2TeamKey))
                .leftJoin(dimGround).on(mGroundKey.eq(gGroundKey))
                .where(mPublicMatchId.eq(publicMatchId.value))
                .fetchOne() ?: return@withMatchQuery null

            val matchKey = requireNotNull(match.get(mMatchKey))

            val team1Key = match.get(mTeam1Key)
            val team2Key = match.get(mTeam2Key)
            val winnerKey = match.get(mWinnerTeamKey)
            val winnerName = when (winnerKey) {
                team1Key -> match.get(t1TeamName)
                team2Key -> match.get(t2TeamName)
                else -> null
            }
            val context = MatchScoresheetContext(
                publicMatchId = PublicMatchId.from(requireNotNull(match.get(mPublicMatchId))),
                fileName = requireNotNull(match.get(mFileName)),
                matchType = match.get(mMatchType)?.takeIf { it.isNotBlank() }?.let(MatchType::from),
                season = match.get(mSeason)?.takeIf { it.isNotBlank() }?.let(Season::from),
                competition = match.get(mEventName)?.takeIf { it.isNotBlank() },
                date = MatchResponseMapper.formatDateText(match.get(mMatchDateText), match.get(dCalendarDate)),
                team1 = match.get(t1TeamName)?.takeIf { it.isNotBlank() },
                team2 = match.get(t2TeamName)?.takeIf { it.isNotBlank() },
                ground = match.get(gGroundName)?.takeIf { it.isNotBlank() },
                result = MatchResponseMapper.formatResult(winnerName, match.get(mVictoryType), match.get(fmMargin))
            )

            val inningsRows = dsl.select(iInningsKey, iInningsNumber, iDeclared, ibTeamName, ioTeamName)
                .from(dimInnings)
                .leftJoin(dimInningsBattingTeam).on(iBattingTeamKey.eq(ibTeamKey))
                .leftJoin(dimInningsBowlingTeam).on(iBowlingTeamKey.eq(ioTeamKey))
                .where(iMatchKey.eq(matchKey))
                .orderBy(iInningsNumber.asc(), iInningsKey.asc())
                .fetch()

            val totalDeliveries = dsl.selectCount()
                .from(factDelivery)
                .where(fdMatchKey.eq(matchKey))
                .fetchOne(count()) ?: 0
            val deliveryInnings = dsl.selectDistinct(fdInningsKey)
                .from(factDelivery)
                .where(fdMatchKey.eq(matchKey))
                .fetch(fdInningsKey)
                .filterNotNull()

            val deliveryRows = dsl.select(
                fdDeliveryKey,
                fdSourceBallId,
                fdInningsKey,
                fdInningsOrder,
                fdOverNumber,
                fdBallNumber,
                fdBallInOver,
                pbFullName,
                pnsFullName,
                pboFullName,
                fdBatterRuns,
                fdExtraRuns,
                fdTotalRuns,
                fdNoBalls,
                fdWides,
                fdByes,
                fdLegByes,
                fdNonBoundary,
                fdPowerplay,
                fdWicketCount
            )
                .from(factDelivery)
                .leftJoin(dimPersonBatter).on(fdBatterKey.eq(pbPersonKey))
                .leftJoin(dimPersonNonStriker).on(fdNonStrikerKey.eq(pnsPersonKey))
                .leftJoin(dimPersonBowler).on(fdBowlerKey.eq(pboPersonKey))
                .where(fdMatchKey.eq(matchKey))
                .orderBy(
                    fdInningsOrder.asc(),
                    fdOverNumber.asc(),
                    fdBallInOver.asc(),
                    fdBallNumber.asc(),
                    fdDeliveryKey.asc()
                )
                .fetch()

            val deliveryKeys = deliveryRows.mapNotNull { it.get(fdDeliveryKey) }
            val wicketRows = if (deliveryKeys.isEmpty()) {
                emptyList()
            } else {
                dsl.select(dwDeliveryKey, dwWicketKey, wKind)
                    .from(bridgeDeliveryWicket)
                    .join(dimWicket).on(dwWicketKey.eq(wWicketKey))
                    .where(dwDeliveryKey.`in`(deliveryKeys))
                    .fetch()
            }
            val fielderRows = if (deliveryKeys.isEmpty()) {
                emptyList()
            } else {
                dsl.select(dfDeliveryKey, dfWicketKey, pfFullName)
                    .from(bridgeDeliveryFielder)
                    .leftJoin(dimPersonFielder).on(dfPersonKey.eq(pfPersonKey))
                    .where(dfDeliveryKey.`in`(deliveryKeys))
                    .fetch()
            }
            val fieldersByWicket = fielderRows.groupBy {
                Pair(it.get(dfDeliveryKey), it.get(dfWicketKey))
            }.mapValues { (_, rows) -> rows.mapNotNull { it.get(pfFullName) } }
            val wicketsByDelivery = wicketRows.groupBy { it.get(dwDeliveryKey) }.mapValues { (_, rows) ->
                rows.mapNotNull { row ->
                    val deliveryId = row.get(dwDeliveryKey)
                    val wicketId = row.get(dwWicketKey)
                    if (deliveryId == null || wicketId == null) {
                        null
                    } else {
                        MatchScoresheetWicket(
                            wicketKey = wicketId,
                            kind = row.get(wKind),
                            fielders = fieldersByWicket[Pair(deliveryId, wicketId)].orEmpty()
                        )
                    }
                }
            }

            val deliveriesByInnings = deliveryRows.groupBy { it.get(fdInningsKey) }.mapValues { (_, rows) ->
                rows.mapNotNull { row ->
                    val deliveryId = row.get(fdDeliveryKey) ?: return@mapNotNull null
                    MatchScoresheetDelivery(
                        deliveryKey = deliveryId,
                        sourceBallId = row.get(fdSourceBallId) ?: 0,
                        inningsOrder = row.get(fdInningsOrder) ?: 0,
                        overNumber = row.get(fdOverNumber) ?: 0,
                        ballNumber = row.get(fdBallNumber) ?: 0,
                        ballInOver = row.get(fdBallInOver) ?: 0,
                        batter = row.get(pbFullName),
                        nonStriker = row.get(pnsFullName),
                        bowler = row.get(pboFullName),
                        batterRuns = row.get(fdBatterRuns) ?: 0,
                        extraRuns = row.get(fdExtraRuns) ?: 0,
                        totalRuns = row.get(fdTotalRuns) ?: 0,
                        noBalls = row.get(fdNoBalls) ?: 0,
                        wides = row.get(fdWides) ?: 0,
                        byes = row.get(fdByes) ?: 0,
                        legByes = row.get(fdLegByes) ?: 0,
                        nonBoundary = row.get(fdNonBoundary),
                        powerplay = row.get(fdPowerplay) ?: 0,
                        wicketCount = row.get(fdWicketCount) ?: 0,
                        wickets = wicketsByDelivery[deliveryId].orEmpty()
                    )
                }
            }
            val innings = inningsRows.map { row ->
                val inningsKey = row.get(iInningsKey)
                MatchScoresheetInnings(
                    inningsNumber = row.get(iInningsNumber) ?: 0,
                    battingTeam = row.get(ibTeamName),
                    bowlingTeam = row.get(ioTeamName),
                    declared = row.get(iDeclared) ?: false,
                    deliveries = deliveriesByInnings[inningsKey].orEmpty()
                )
            }
            val (completeness, missingData) = MatchResponseMapper.scoresheetCompleteness(
                inningsEmpty = inningsRows.isEmpty(),
                totalDeliveries = totalDeliveries,
                missingInningsDeliveries = inningsRows.any { !deliveryInnings.contains(it.get(iInningsKey)) },
                teamsIncomplete = context.team1 == null || context.team2 == null
            )
            MatchScoresheetPage(
                context = context,
                completeness = completeness,
                missingData = missingData,
                innings = innings
            )
        
        }

}

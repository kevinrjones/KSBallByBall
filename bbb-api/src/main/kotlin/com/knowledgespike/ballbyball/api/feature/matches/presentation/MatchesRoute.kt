package com.knowledgespike.ballbyball.api.feature.matches.presentation

import arrow.core.raise.fold
import com.knowledgespike.ballbyball.api.bootstrap.AUTH_JWT
import com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchCriteria
import com.knowledgespike.ballbyball.api.feature.matches.domain.repository.MatchRepository
import com.knowledgespike.ballbyball.api.routing.respondBadRequest
import com.knowledgespike.ballbyball.api.routing.respondOk
import com.knowledgespike.ballbyball.contracts.*
import com.knowledgespike.ballbyball.types.values.Limit
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

fun Route.routeMatches(matchRepository: MatchRepository) {
    authenticate(AUTH_JWT) {
        route("/api") {
            get("/matches") {
                fold(
                    block = { Limit(call.request.queryParameters["days"] ?: call.request.queryParameters["limit"]) },
                    recover = { error -> call.respondBadRequest(error.message) },
                    transform = { limit ->
                        val matches = matchRepository.recentMatches(limit)
                        call.respondOk(RecentMatchesResponse(matches = matches))
                    }
                )
            }
            get("/matches/search") {
                parseMatchSearchRequest(call.request.queryParameters::get).fold(
                    ifLeft = { errors -> call.respondBadRequest(errors) },
                    ifRight = { request ->
                        val page = matchRepository.searchMatches(MatchSearchCriteria.from(request))
                        call.respondOk(page.toResponse())
                    }
                )
            }
            get("/matches/{publicMatchId}/scoresheet") {
                parseMatchScoresheetRequest { name ->
                    if (name == "publicMatchId") {
                        call.parameters[name]
                    } else {
                        call.request.queryParameters[name]
                    }
                }.fold(
                    ifLeft = { errors -> call.respondBadRequest(errors) },
                    ifRight = { request ->
                        try {
                            val scoresheet = matchRepository.scoresheet(
                                request.publicMatchId
                            )
                            if (scoresheet == null) {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    Envelope.failure("The requested match was not found")
                                )
                            } else {
                                call.respondOk(scoresheet.toResponse())
                            }
                        } catch (cause: kotlinx.coroutines.CancellationException) {
                            throw cause
                        } catch (cause: Exception) {
                            log.error("Scoresheet request failed", cause)
                            call.respond(
                                HttpStatusCode.BadGateway,
                                Envelope.failure("The scoresheet is currently unavailable")
                            )
                        }
                    }
                )
            }
        }
    }
}

private val log = LoggerFactory.getLogger("com.knowledgespike.ballbyball.api.matches")

private fun com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchSearchPage.toResponse() =
    MatchSearchResponse(
        matches = matches.map {
            MatchSearchResult(
                publicMatchId = it.publicMatchId,
                fileName = it.fileName,
                matchType = it.matchType,
                season = it.season,
                competition = it.competition,
                date = it.date,
                team1 = it.team1,
                team2 = it.team2,
                ground = it.ground,
                result = it.result
            )
        },
        pagination = MatchSearchPagination(page, pageSize, totalResults, hasNext, nextPage)
    )

private fun com.knowledgespike.ballbyball.api.feature.matches.domain.model.MatchScoresheetPage.toResponse() =
    MatchScoresheetResponse(
        context = MatchScoresheetContext(
            publicMatchId = context.publicMatchId,
            fileName = context.fileName,
            matchType = context.matchType,
            season = context.season,
            competition = context.competition,
            date = context.date,
            team1 = context.team1,
            team2 = context.team2,
            ground = context.ground,
            result = context.result
        ),
        completeness = completeness,
        missingData = missingData,
        innings = innings.map { innings ->
            ScoresheetInnings(
                inningsNumber = innings.inningsNumber,
                battingTeam = innings.battingTeam,
                bowlingTeam = innings.bowlingTeam,
                declared = innings.declared,
                deliveries = innings.deliveries.map { delivery ->
                    ScoresheetDelivery(
                        deliveryKey = delivery.deliveryKey,
                        sourceBallId = delivery.sourceBallId,
                        inningsOrder = delivery.inningsOrder,
                        overNumber = delivery.overNumber,
                        ballNumber = delivery.ballNumber,
                        ballInOver = delivery.ballInOver,
                        batter = delivery.batter,
                        nonStriker = delivery.nonStriker,
                        bowler = delivery.bowler,
                        batterRuns = delivery.batterRuns,
                        extraRuns = delivery.extraRuns,
                        totalRuns = delivery.totalRuns,
                        noBalls = delivery.noBalls,
                        wides = delivery.wides,
                        byes = delivery.byes,
                        legByes = delivery.legByes,
                        nonBoundary = delivery.nonBoundary,
                        powerplay = delivery.powerplay,
                        wicketCount = delivery.wicketCount,
                        wickets = delivery.wickets.map { wicket ->
                            ScoresheetWicket(wicket.wicketKey, wicket.kind, wicket.fielders)
                        }
                    )
                }
            )
        }
    )

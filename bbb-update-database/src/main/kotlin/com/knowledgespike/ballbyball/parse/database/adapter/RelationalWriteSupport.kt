package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.types.values.PublicMatchId
import com.knowledgespike.cricketarchive.InvalidStateException
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

internal object RelationalWriteSupport {
    val PERSON_COLUMNS = listOf("id", "source_person_id", "full_name", "sort_name_part", "other_name_part", "ca_id")
    val PERSON_JDBC_COLUMNS = PERSON_COLUMNS.drop(1)
    val TEAM_COLUMNS = listOf("id", "source_team_id", "team_name")
    val TEAM_JDBC_COLUMNS = TEAM_COLUMNS.drop(1)
    val GROUND_COLUMNS = listOf("id", "source_ground_id", "ground_name")
    val GROUND_JDBC_COLUMNS = GROUND_COLUMNS.drop(1)
    val DATE_COLUMNS = listOf(
        "date_id", "calendar_date", "calendar_year", "calendar_quarter", "calendar_month",
        "month_name", "week_of_year", "day_of_month", "day_of_week", "day_name", "is_weekend"
    )
    val MATCH_COLUMNS = listOf(
        "id", "canonical_match_id", "public_match_id", "source_ca_id", "file_name", "match_in_series",
        "match_type", "event_name", "match_date_text", "season", "match_start_year", "match_start_date_id",
        "balls_per_over", "added_timestamp", "team1_id", "team2_id", "ground_id", "toss_team_id",
        "toss_decision", "victory_type", "winner_team_id", "loser_team_id", "duration_days", "margin", "match_count"
    )
    val MATCH_JDBC_COLUMNS = MATCH_COLUMNS.drop(1)
    val SOURCE_REFERENCE_COLUMNS = listOf(
        "match_id", "provider", "provider_record_key", "source_record_id", "raw_content_digest"
    )
    val INNINGS_COLUMNS = listOf(
        "id", "match_id", "innings_number", "batting_team_id", "bowling_team_id", "declared"
    )
    val INNINGS_JDBC_COLUMNS = INNINGS_COLUMNS.drop(1)
    val WICKET_COLUMNS = listOf("id", "source_wicket_id", "wicket_kind")
    val WICKET_JDBC_COLUMNS = WICKET_COLUMNS.drop(1)
    val DELIVERY_COLUMNS = listOf(
        "id", "source_ball_id", "match_id", "match_date_id", "innings_id", "batting_team_id",
        "bowling_team_id", "batter_id", "non_striker_id", "bowler_id", "over_number", "ball_number",
        "ball_in_over", "innings_order", "batter_runs", "extra_runs", "total_runs", "no_balls", "wides",
        "byes", "leg_byes", "non_boundary", "powerplay", "wicket_count"
    )
    val DELIVERY_JDBC_COLUMNS = DELIVERY_COLUMNS.drop(1)
    val MATCH_PERSON_COLUMNS = listOf("id", "match_id", "person_id", "role_code")
    val MATCH_PERSON_INSERT_COLUMNS = MATCH_PERSON_COLUMNS.drop(1)
    val DELIVERY_WICKET_COLUMNS = listOf("delivery_id", "wicket_id")
    val DELIVERY_FIELDER_COLUMNS = listOf("delivery_id", "wicket_id", "person_id")

    fun inningsValues(
        inningsKey: Long,
        matchId: Long,
        inningsNumber: Int,
        battingTeamId: Long,
        bowlingTeamId: Long,
        declared: Boolean
    ): List<Any?> = listOf(inningsKey, matchId, inningsNumber, battingTeamId, bowlingTeamId, declared)

    fun insertSql(
        tableName: String,
        columns: List<String>,
        keyword: String = "INSERT INTO",
        values: List<String> = columns.map { "?" }
    ): String {
        require(columns.size == values.size) { "Insert columns and values must have the same size" }
        return "$keyword $tableName (${columns.joinToString(", ")}) VALUES (${values.joinToString(", ")})"
    }

    fun validatePublicMatchId(
        canonicalMatchId: CanonicalMatchId,
        publicMatchId: PublicMatchId,
        existingPublicMatchId: Long? = null,
        publicIdOwnedByAnotherMatch: Boolean = false
    ) {
        if (existingPublicMatchId != null && existingPublicMatchId != publicMatchId.value) {
            throw InvalidStateException(
                "Canonical match ${canonicalMatchId.value} already has publicMatchId $existingPublicMatchId, " +
                    "expected ${publicMatchId.value}"
            )
        }
        if (publicIdOwnedByAnotherMatch) {
            throw InvalidStateException(
                "Deterministic publicMatchId ${publicMatchId.value} collides for canonical match " +
                    canonicalMatchId.value
            )
        }
    }

    fun matchWriteDecision(
        canonicalMatchId: CanonicalMatchId,
        publicMatchId: PublicMatchId,
        existingMatchKey: Long?,
        existingPublicMatchId: Long? = null,
        publicIdOwnedByAnotherMatch: Boolean = false
    ): MatchWriteDecision {
        validatePublicMatchId(
            canonicalMatchId = canonicalMatchId,
            publicMatchId = publicMatchId,
            existingPublicMatchId = existingPublicMatchId,
            publicIdOwnedByAnotherMatch = publicIdOwnedByAnotherMatch
        )
        return existingMatchKey?.let(MatchWriteDecision::Reuse) ?: MatchWriteDecision.Insert
    }

    fun matchValues(matchKey: Long, match: MatchRecord, addedAt: Any): List<Any?> =
        listOf(matchKey) + matchInsertValues(match, addedAt)

    fun matchInsertValues(match: MatchRecord, addedAt: Any): List<Any?> = listOf(
        match.canonicalMatchId.value.toString(),
        match.publicMatchId.value,
        null,
        match.fileName,
        match.matchInSeries,
        match.matchType,
        match.eventName,
        match.matchDateText,
        match.season,
        match.matchStartYear,
        match.matchStartDateKey,
        match.ballsPerOver,
        addedAt,
        match.team1Key,
        match.team2Key,
        match.groundKey,
        match.tossTeamKey,
        match.tossDecision,
        match.victoryType,
        match.winnerTeamKey,
        match.loserTeamKey,
        match.durationDays,
        match.margin,
        match.matchCount
    )

    fun sourceReferenceValues(matchKey: Long, source: SourceReference) =
        listOf(
            matchKey,
            source.provider.value,
            source.providerRecordKey,
            source.sourceRecordId.value.toString(),
            source.rawContentDigest.value
        )

    fun deliveryValues(deliveryKey: Long, sourceBallId: Long, delivery: DeliveryRecord): List<Any?> =
        listOf(deliveryKey) + deliveryInsertValues(sourceBallId, delivery)

    fun deliveryInsertValues(sourceBallId: Long, delivery: DeliveryRecord): List<Any?> = listOf(
        sourceBallId,
        delivery.matchKey,
        delivery.matchDateKey,
        delivery.inningsKey,
        delivery.battingTeamKey,
        delivery.bowlingTeamKey,
        delivery.batterKey,
        delivery.nonStrikerKey,
        delivery.bowlerKey,
        delivery.overNumber,
        delivery.ballNumber,
        delivery.ballInOver,
        delivery.inningsOrder,
        delivery.delivery.runs.batter,
        delivery.delivery.runs.extras,
        delivery.delivery.runs.total,
        delivery.delivery.extras?.noballs ?: 0,
        delivery.delivery.extras?.wides ?: 0,
        delivery.delivery.extras?.byes ?: 0,
        delivery.delivery.extras?.legbyes ?: 0,
        delivery.delivery.runs.nonBoundary?.let { if (it) 1 else 0 },
        delivery.powerplay,
        delivery.delivery.wickets.orEmpty().size
    )

    fun dateDimensions(date: LocalDate): DateDimensions {
        val weekFields = WeekFields.ISO
        return DateDimensions(
            dateKey = date.year * 10000 + date.monthValue * 100 + date.dayOfMonth,
            calendarDate = date,
            calendarYear = date.year,
            calendarQuarter = (date.monthValue - 1) / 3 + 1,
            calendarMonth = date.monthValue,
            monthName = date.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
            weekOfYear = date.get(weekFields.weekOfYear()),
            dayOfMonth = date.dayOfMonth,
            dayOfWeek = date.dayOfWeek.value,
            dayName = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH),
            isWeekend = date.dayOfWeek.value >= 6
        )
    }
}

internal sealed interface MatchWriteDecision {
    data object Insert : MatchWriteDecision

    data class Reuse(val matchKey: Long) : MatchWriteDecision
}

internal data class DateDimensions(
    val dateKey: Int,
    val calendarDate: LocalDate,
    val calendarYear: Int,
    val calendarQuarter: Int,
    val calendarMonth: Int,
    val monthName: String,
    val weekOfYear: Int,
    val dayOfMonth: Int,
    val dayOfWeek: Int,
    val dayName: String,
    val isWeekend: Boolean
) {
    fun values(calendarDateValue: Any = calendarDate): List<Any?> = listOf(
        dateKey,
        calendarDateValue,
        calendarYear,
        calendarQuarter,
        calendarMonth,
        monthName,
        weekOfYear,
        dayOfMonth,
        dayOfWeek,
        dayName,
        isWeekend
    )
}
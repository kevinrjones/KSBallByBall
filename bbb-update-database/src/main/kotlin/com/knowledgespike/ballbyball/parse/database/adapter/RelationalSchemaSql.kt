package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.identity.AliasSourceSystem
import java.io.BufferedWriter

internal object RelationalSchemaSql {
    const val DEFAULT_MARIADB_DATABASE_NAME = "acs_ball_by_ball"
    private val mariadbDatabaseNamePattern = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val teamNameAliasSeeds = listOf(
        TeamNameAliasSeed("Cumilla Warriors", 45116),
        TeamNameAliasSeed("Fortune Barishal", 48630),
        TeamNameAliasSeed("Sylhet Super Stars", 36985),
        TeamNameAliasSeed("North-West Warriors", 44366),
        TeamNameAliasSeed("Barbados Tridents", 48094),
        TeamNameAliasSeed("Trinidad & Tobago Red Steel", 25995),
        TeamNameAliasSeed("Warriors", 98),
        TeamNameAliasSeed("Titans", 250),
        TeamNameAliasSeed("Lions", 125),
        TeamNameAliasSeed("Knights", 110),
        TeamNameAliasSeed("Dolphins", 46696),
        TeamNameAliasSeed("Warriors (FairBreak) Women", 49084),
        TeamNameAliasSeed("Birmingham Phoenix", 45098),
        TeamNameAliasSeed("London Spirit", 45099),
        TeamNameAliasSeed("Manchester Originals", 45100),
        TeamNameAliasSeed("Northern Superchargers", 45101),
        TeamNameAliasSeed("Oval Invincibles", 45102),
        TeamNameAliasSeed("Southern Brave", 45103),
        TeamNameAliasSeed("Trent Rockets", 45104),
        TeamNameAliasSeed("Welsh Fire", 45105),
        TeamNameAliasSeed("MI London", 55090),
        TeamNameAliasSeed("Sunrisers Leeds", 55091),
        TeamNameAliasSeed("Manchester Super Giants", 55092),
        TeamNameAliasSeed("Sharjah Warriorz", 50358),
        TeamNameAliasSeed("Sunrisers Hyderabad", 22793),
        TeamNameAliasSeed("Royal Challengers Bengaluru", 6927),
        TeamNameAliasSeed("Kandy Customs Cricket Club", 28817),
        TeamNameAliasSeed("Nugegoda Sports Welfare Club", 28516),
        TeamNameAliasSeed("Central Stags", 66),
        TeamNameAliasSeed("Otago Volts", 260),
        TeamNameAliasSeed("Wellington Firebirds", 389),
        TeamNameAliasSeed("Auckland Aces", 19),
        TeamNameAliasSeed("Birmingham Bears", 387),
        TeamNameAliasSeed("Africa XI", 1976),
        TeamNameAliasSeed("Asia XI", 1782),
        TeamNameAliasSeed("Rawalpindiz", 55249),
        TeamNameAliasSeed("Himachal", 147),
        TeamNameAliasSeed("Jammu & Kashmir", 159),
        TeamNameAliasSeed("Hyderabad (India)", 780),
        TeamNameAliasSeed("Odisha", 409),
        TeamNameAliasSeed("ICC World XI", 1384),
        TeamNameAliasSeed("Swaziland", 48403),
        TeamNameAliasSeed("Zimbabwe", 31229),
        TeamNameAliasSeed("Turks and Caicos Island", 2319),
        TeamNameAliasSeed("South Africa Women", 25291),
        TeamNameAliasSeed("South Africa", 25291),
        TeamNameAliasSeed("South Africa", 10312),
        TeamNameAliasSeed("Guyana Amazon Warriors Women", 55513),
        TeamNameAliasSeed("Royal Challengers Bengaluru Women", 51559)
    )
    private val tablesInDependencyOrder = listOf(
        "team_name_aliases", "dates", "teams", "people", "grounds", "matches", "match_source_reference",
        "innings", "wickets", "deliveries", "match_people", "delivery_wickets", "delivery_fielders"
    )

    fun writeTo(
        writer: BufferedWriter,
        dialect: SqlDialect,
        mariadbDatabaseName: String = DEFAULT_MARIADB_DATABASE_NAME
    ) {
        if (dialect == SqlDialect.MARIADB) {
            require(mariadbDatabaseNamePattern.matches(mariadbDatabaseName)) {
                "Invalid MariaDB database name: $mariadbDatabaseName"
            }
        }
        when (dialect) {
            SqlDialect.MARIADB -> writer.appendLine("USE $mariadbDatabaseName;")
            SqlDialect.POSTGRES -> {
                writer.appendLine("CREATE SCHEMA IF NOT EXISTS acs_ball_by_ball;")
                writer.appendLine("SET search_path TO acs_ball_by_ball;")
            }
            SqlDialect.SQLITE -> writer.appendLine("PRAGMA foreign_keys = ON;")
        }
        writer.appendLine()
        writer.appendLine("-- Reset and recreate the normalized relational schema")
        tablesInDependencyOrder.asReversed()
            .filterNot { it == "team_name_aliases" }
            .forEach { writer.appendLine("DROP TABLE IF EXISTS $it;") }
        writer.appendLine()
        createStatements(dialect).forEach {
            writer.appendLine(it.trimIndent())
            writer.appendLine()
        }
        teamNameAliasSeeds.forEach { seed ->
            writer.appendLine(teamNameAliasInsert(seed, dialect))
        }
    }

    private fun teamNameAliasInsert(seed: TeamNameAliasSeed, dialect: SqlDialect): String {
        val insert = when (dialect) {
            SqlDialect.MARIADB -> "INSERT IGNORE INTO"
            SqlDialect.POSTGRES -> "INSERT INTO"
            SqlDialect.SQLITE -> "INSERT OR IGNORE INTO"
        }
        val escapedName = seed.sourceTeamName.replace("'", "''")
        val onConflict = if (dialect == SqlDialect.POSTGRES) " ON CONFLICT DO NOTHING" else ""
        return "$insert team_name_aliases (source_system, source_team_name, stats_team_id) " +
            "VALUES ('${AliasSourceSystem.CRICSHEET.value}', '$escapedName', ${seed.statsTeamId})$onConflict;"
    }

    private data class TeamNameAliasSeed(val sourceTeamName: String, val statsTeamId: Int)

    private fun createStatements(dialect: SqlDialect): List<String> {
        val key = keyType(dialect)
        val matchKey = matchKeyType(dialect)
        val integer = if (dialect == SqlDialect.SQLITE) "INTEGER" else "INT"
        val calendarYear = if (dialect == SqlDialect.SQLITE) "INTEGER" else "SMALLINT"
        val smallInteger = when (dialect) {
            SqlDialect.MARIADB -> "TINYINT"
            SqlDialect.POSTGRES -> "SMALLINT"
            SqlDialect.SQLITE -> "INTEGER"
        }
        val matchCount = if (dialect == SqlDialect.MARIADB) "TINYINT UNSIGNED" else smallInteger
        val timestamp = if (dialect == SqlDialect.POSTGRES) "TIMESTAMP" else "DATETIME"
        val identity = identityKeyType(dialect)
        val matchIdentity = identityMatchKeyType(dialect)
        val engine = if (dialect == SqlDialect.MARIADB) " ENGINE = InnoDB" else ""
        return listOf(
            """
            CREATE TABLE IF NOT EXISTS team_name_aliases (
                source_system VARCHAR(50) NOT NULL, source_team_name VARCHAR(100) NOT NULL,
                stats_team_id $integer NOT NULL,
                UNIQUE (source_system, source_team_name, stats_team_id)
            )$engine;
            CREATE INDEX IF NOT EXISTS idx_team_name_aliases_stats_team_id ON team_name_aliases (stats_team_id);
            """,
            """
            CREATE TABLE dates (
                date_id $integer NOT NULL PRIMARY KEY, calendar_date DATE NOT NULL,
                calendar_year $calendarYear NOT NULL, calendar_quarter $smallInteger NOT NULL,
                calendar_month $smallInteger NOT NULL, month_name VARCHAR(9) NOT NULL,
                week_of_year $smallInteger NOT NULL, day_of_month $smallInteger NOT NULL,
                day_of_week $smallInteger NOT NULL, day_name VARCHAR(9) NOT NULL,
                is_weekend BOOLEAN NOT NULL, UNIQUE (calendar_date)
            )$engine;
            """,
            """
            CREATE TABLE teams (
                id $identity, source_team_id $integer NOT NULL, team_name VARCHAR(100) NOT NULL,
                UNIQUE (source_team_id)
            )$engine;
            CREATE INDEX idx_teams_name ON teams (team_name);
            """,
            """
            CREATE TABLE people (
                id $identity, source_person_id VARCHAR(10) NOT NULL, full_name VARCHAR(200) NOT NULL,
                sort_name_part VARCHAR(200) NOT NULL, other_name_part VARCHAR(200) NOT NULL,
                ca_id $integer NOT NULL, UNIQUE (source_person_id)
            )$engine;
            CREATE INDEX idx_people_full_name ON people (full_name);
            CREATE INDEX idx_people_sort_name ON people (sort_name_part);
            CREATE INDEX idx_people_ca_id ON people (ca_id);
            """,
            """
            CREATE TABLE grounds (
                id $identity, source_ground_id $integer NOT NULL, ground_name VARCHAR(500) NOT NULL,
                UNIQUE (source_ground_id)
            )$engine;
            CREATE INDEX idx_grounds_name ON grounds (ground_name);
            """,
            """
            CREATE TABLE matches (
                id $matchIdentity, canonical_match_id CHAR(36) NULL, public_match_id BIGINT NULL,
                source_ca_id VARCHAR(20) NULL, file_name VARCHAR(120) NOT NULL,
                match_in_series $integer NOT NULL, match_type VARCHAR(15) NOT NULL,
                event_name VARCHAR(200) NOT NULL, match_date_text VARCHAR(200) NOT NULL,
                season VARCHAR(200) NOT NULL, match_start_year VARCHAR(200) NOT NULL,
                match_start_date_id $integer NULL, balls_per_over $integer NOT NULL,
                added_timestamp $timestamp NOT NULL, team1_id $key NOT NULL, team2_id $key NOT NULL,
                ground_id $key NOT NULL, toss_team_id $key NOT NULL, toss_decision VARCHAR(10) NULL,
                victory_type VARCHAR(15) NOT NULL, winner_team_id $key NULL, loser_team_id $key NULL,
                duration_days $integer NOT NULL, margin $integer NOT NULL,
                match_count $matchCount NOT NULL DEFAULT 1, UNIQUE (canonical_match_id),
                UNIQUE (public_match_id), UNIQUE (source_ca_id),
                FOREIGN KEY (match_start_date_id) REFERENCES dates (date_id),
                FOREIGN KEY (team1_id) REFERENCES teams (id), FOREIGN KEY (team2_id) REFERENCES teams (id),
                FOREIGN KEY (ground_id) REFERENCES grounds (id), FOREIGN KEY (toss_team_id) REFERENCES teams (id),
                FOREIGN KEY (winner_team_id) REFERENCES teams (id), FOREIGN KEY (loser_team_id) REFERENCES teams (id)
            )$engine;
            CREATE INDEX idx_matches_type ON matches (match_type);
            CREATE INDEX idx_matches_file_name ON matches (file_name);
            CREATE INDEX idx_matches_type_year ON matches (match_type, match_start_year);
            CREATE INDEX idx_matches_teams_type ON matches (match_type, team1_id, team2_id);
            CREATE INDEX idx_matches_season ON matches (season);
            CREATE INDEX idx_matches_start_date ON matches (match_start_date_id);
            CREATE INDEX idx_matches_team1 ON matches (team1_id);
            CREATE INDEX idx_matches_team2 ON matches (team2_id);
            CREATE INDEX idx_matches_ground ON matches (ground_id);
            """,
            """
            CREATE TABLE match_source_reference (
                match_id $matchKey NOT NULL, provider VARCHAR(100) NOT NULL,
                provider_record_key VARCHAR(255) NOT NULL, source_record_id CHAR(36) NOT NULL,
                raw_content_digest CHAR(64) NOT NULL, PRIMARY KEY (match_id, provider, source_record_id),
                UNIQUE (provider, source_record_id), FOREIGN KEY (match_id) REFERENCES matches (id)
            )$engine;
            CREATE INDEX idx_match_source_record ON match_source_reference (source_record_id);
            """,
            """
            CREATE TABLE innings (
                id $identity, match_id $matchKey NOT NULL, innings_number $integer NOT NULL,
                batting_team_id $key NOT NULL, bowling_team_id $key NOT NULL,
                declared BOOLEAN NOT NULL DEFAULT FALSE,
                UNIQUE (match_id, innings_number), FOREIGN KEY (match_id) REFERENCES matches (id),
                FOREIGN KEY (batting_team_id) REFERENCES teams (id), FOREIGN KEY (bowling_team_id) REFERENCES teams (id)
            )$engine;
            CREATE INDEX idx_innings_batting_team ON innings (batting_team_id);
            CREATE INDEX idx_innings_bowling_team ON innings (bowling_team_id);
            """,
            """
            CREATE TABLE wickets (
                id $identity, source_wicket_id $integer NOT NULL, wicket_kind VARCHAR(30) NULL,
                UNIQUE (source_wicket_id)
            )$engine;
            CREATE INDEX idx_wickets_kind ON wickets (wicket_kind);
            """,
            """
            CREATE TABLE deliveries (
                id $identity, source_ball_id $integer NOT NULL, match_id $matchKey NOT NULL,
                match_date_id $integer NULL, innings_id $key NOT NULL, batting_team_id $key NOT NULL,
                bowling_team_id $key NOT NULL, batter_id $key NOT NULL, non_striker_id $key NOT NULL,
                bowler_id $key NOT NULL, over_number $integer NOT NULL, ball_number $integer NOT NULL,
                ball_in_over $integer NOT NULL, innings_order $integer NOT NULL, batter_runs $integer NOT NULL,
                extra_runs $integer NOT NULL, total_runs $integer NOT NULL, no_balls $integer NOT NULL,
                wides $integer NOT NULL, byes $integer NOT NULL, leg_byes $integer NOT NULL,
                non_boundary $integer NULL, powerplay $integer NOT NULL, wicket_count $integer NOT NULL,
                UNIQUE (source_ball_id), FOREIGN KEY (match_id) REFERENCES matches (id),
                FOREIGN KEY (match_date_id) REFERENCES dates (date_id), FOREIGN KEY (innings_id) REFERENCES innings (id),
                FOREIGN KEY (batting_team_id) REFERENCES teams (id), FOREIGN KEY (bowling_team_id) REFERENCES teams (id),
                FOREIGN KEY (batter_id) REFERENCES people (id), FOREIGN KEY (non_striker_id) REFERENCES people (id),
                FOREIGN KEY (bowler_id) REFERENCES people (id)
            )$engine;
            CREATE INDEX idx_deliveries_match_order ON deliveries (match_id, innings_order);
            CREATE INDEX idx_deliveries_match_sequence ON deliveries (match_id, innings_order, over_number, ball_in_over);
            CREATE INDEX idx_deliveries_date ON deliveries (match_date_id);
            CREATE INDEX idx_deliveries_innings ON deliveries (innings_id);
            CREATE INDEX idx_deliveries_batting_team ON deliveries (batting_team_id);
            CREATE INDEX idx_deliveries_bowling_team ON deliveries (bowling_team_id);
            CREATE INDEX idx_deliveries_batter ON deliveries (batter_id);
            CREATE INDEX idx_deliveries_non_striker ON deliveries (non_striker_id);
            CREATE INDEX idx_deliveries_bowler ON deliveries (bowler_id);
            CREATE INDEX idx_deliveries_over ON deliveries (over_number);
            CREATE INDEX idx_deliveries_ball_in_over ON deliveries (ball_in_over);
            CREATE INDEX idx_deliveries_powerplay ON deliveries (powerplay);
            """,
            """
            CREATE TABLE match_people (
                id $identity, match_id $matchKey NOT NULL, person_id $key NOT NULL, role_code VARCHAR(32) NOT NULL,
                UNIQUE (match_id, person_id, role_code), FOREIGN KEY (match_id) REFERENCES matches (id),
                FOREIGN KEY (person_id) REFERENCES people (id)
            )$engine;
            CREATE INDEX idx_match_people_person ON match_people (person_id);
            CREATE INDEX idx_match_people_role ON match_people (role_code);
            """,
            """
            CREATE TABLE delivery_wickets (
                delivery_id $key NOT NULL, wicket_id $key NOT NULL, PRIMARY KEY (delivery_id, wicket_id),
                FOREIGN KEY (delivery_id) REFERENCES deliveries (id), FOREIGN KEY (wicket_id) REFERENCES wickets (id)
            )$engine;
            CREATE INDEX idx_delivery_wickets_wicket ON delivery_wickets (wicket_id);
            """,
            """
            CREATE TABLE delivery_fielders (
                delivery_id $key NOT NULL, wicket_id $key NOT NULL, person_id $key NOT NULL,
                PRIMARY KEY (delivery_id, wicket_id, person_id), FOREIGN KEY (delivery_id) REFERENCES deliveries (id),
                FOREIGN KEY (wicket_id) REFERENCES wickets (id), FOREIGN KEY (person_id) REFERENCES people (id)
            )$engine;
            CREATE INDEX idx_delivery_fielders_wicket ON delivery_fielders (wicket_id);
            CREATE INDEX idx_delivery_fielders_person ON delivery_fielders (person_id);
            """
        )
    }

    private fun keyType(dialect: SqlDialect): String = when (dialect) {
        SqlDialect.MARIADB -> "BIGINT UNSIGNED"
        SqlDialect.POSTGRES -> "BIGINT"
        SqlDialect.SQLITE -> "INTEGER"
    }

    private fun matchKeyType(dialect: SqlDialect): String = when (dialect) {
        SqlDialect.MARIADB, SqlDialect.POSTGRES -> "BIGINT"
        SqlDialect.SQLITE -> "INTEGER"
    }

    private fun identityKeyType(dialect: SqlDialect): String = when (dialect) {
        SqlDialect.MARIADB -> "BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY"
        SqlDialect.POSTGRES -> "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY"
        SqlDialect.SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT"
    }

    private fun identityMatchKeyType(dialect: SqlDialect): String = when (dialect) {
        SqlDialect.MARIADB -> "BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY"
        SqlDialect.POSTGRES -> "BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY"
        SqlDialect.SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT"
    }
}
PRAGMA foreign_keys = ON;

DROP TABLE IF EXISTS bridge_delivery_fielder;
DROP TABLE IF EXISTS bridge_delivery_wicket;
DROP TABLE IF EXISTS bridge_match_person;
DROP TABLE IF EXISTS fact_delivery;
DROP TABLE IF EXISTS fact_match;
DROP TABLE IF EXISTS dim_wicket;
DROP TABLE IF EXISTS dim_innings;
DROP TABLE IF EXISTS match_source_reference;
DROP TABLE IF EXISTS dim_match;
DROP TABLE IF EXISTS dim_ground;
DROP TABLE IF EXISTS dim_person;
DROP TABLE IF EXISTS dim_team;
DROP TABLE IF EXISTS dim_date;
DROP TABLE IF EXISTS BallsWickets;
DROP TABLE IF EXISTS Wickets;
DROP TABLE IF EXISTS BallByBall;
DROP TABLE IF EXISTS ReserveUmpiresMatches;
DROP TABLE IF EXISTS MatchRefereesMatches;
DROP TABLE IF EXISTS TvUmpiresMatches;
DROP TABLE IF EXISTS UmpiresMatches;
DROP TABLE IF EXISTS PlayersMatches;
DROP TABLE IF EXISTS Matches;
DROP TABLE IF EXISTS MatchReferees;
DROP TABLE IF EXISTS TvUmpires;
DROP TABLE IF EXISTS ReserveUmpires;
DROP TABLE IF EXISTS Umpires;
DROP TABLE IF EXISTS Players;
DROP TABLE IF EXISTS PersonRegistry;
DROP TABLE IF EXISTS Grounds;
DROP TABLE IF EXISTS Teams;

CREATE TABLE dates
(
    date_id          INTEGER NOT NULL PRIMARY KEY,
    calendar_date    DATE    NOT NULL,
    calendar_year    INTEGER NOT NULL,
    calendar_quarter INTEGER NOT NULL,
    calendar_month   INTEGER NOT NULL,
    month_name       VARCHAR(9) NOT NULL,
    week_of_year     INTEGER NOT NULL,
    day_of_month     INTEGER NOT NULL,
    day_of_week      INTEGER NOT NULL,
    day_name         VARCHAR(9) NOT NULL,
    is_weekend       BOOLEAN NOT NULL,
    UNIQUE (calendar_date)
);

CREATE TABLE teams
(
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    source_team_id INTEGER NOT NULL,
    team_name      VARCHAR(100) NOT NULL,
    UNIQUE (source_team_id)
);
CREATE INDEX idx_teams_name ON teams (team_name);

CREATE TABLE people
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    source_person_id VARCHAR(10) NOT NULL,
    full_name        VARCHAR(200) NOT NULL,
    sort_name_part   VARCHAR(200) NOT NULL,
    other_name_part  VARCHAR(200) NOT NULL,
    ca_id            INTEGER NOT NULL,
    UNIQUE (source_person_id)
);
CREATE INDEX idx_people_full_name ON people (full_name);
CREATE INDEX idx_people_sort_name ON people (sort_name_part);
CREATE INDEX idx_people_ca_id ON people (ca_id);

CREATE TABLE grounds
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    source_ground_id INTEGER NOT NULL,
    ground_name      VARCHAR(500) NOT NULL,
    UNIQUE (source_ground_id)
);
CREATE INDEX idx_grounds_name ON grounds (ground_name);

CREATE TABLE matches
(
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    canonical_match_id  CHAR(36) NULL,
    public_match_id     BIGINT NULL,
    source_ca_id        VARCHAR(20) NULL,
    file_name           VARCHAR(120) NOT NULL,
    match_in_series     INTEGER NOT NULL,
    match_type          VARCHAR(15) NOT NULL,
    event_name          VARCHAR(200) NOT NULL,
    match_date_text     VARCHAR(200) NOT NULL,
    season              VARCHAR(200) NOT NULL,
    match_start_year    VARCHAR(200) NOT NULL,
    match_start_date_id INTEGER NULL,
    balls_per_over      INTEGER NOT NULL,
    added_timestamp     DATETIME NOT NULL,
    team1_id            INTEGER NOT NULL,
    team2_id            INTEGER NOT NULL,
    ground_id           INTEGER NOT NULL,
    toss_team_id        INTEGER NOT NULL,
    toss_decision       VARCHAR(10) NULL,
    victory_type        VARCHAR(15) NOT NULL,
    winner_team_id      INTEGER NULL,
    loser_team_id       INTEGER NULL,
    duration_days       INTEGER NOT NULL,
    margin              INTEGER NOT NULL,
    match_count         INTEGER NOT NULL DEFAULT 1,
    UNIQUE (canonical_match_id),
    UNIQUE (public_match_id),
    FOREIGN KEY (match_start_date_id) REFERENCES dates (date_id),
    FOREIGN KEY (team1_id) REFERENCES teams (id),
    FOREIGN KEY (team2_id) REFERENCES teams (id),
    FOREIGN KEY (ground_id) REFERENCES grounds (id),
    FOREIGN KEY (toss_team_id) REFERENCES teams (id),
    FOREIGN KEY (winner_team_id) REFERENCES teams (id),
    FOREIGN KEY (loser_team_id) REFERENCES teams (id)
);
CREATE INDEX idx_matches_type ON matches (match_type);
CREATE INDEX idx_matches_file_name ON matches (file_name);
CREATE INDEX idx_matches_type_year ON matches (match_type, match_start_year);
CREATE INDEX idx_matches_teams_type ON matches (match_type, team1_id, team2_id);
CREATE INDEX idx_matches_season ON matches (season);
CREATE INDEX idx_matches_start_date ON matches (match_start_date_id);
CREATE INDEX idx_matches_team1 ON matches (team1_id);
CREATE INDEX idx_matches_team2 ON matches (team2_id);
CREATE INDEX idx_matches_ground ON matches (ground_id);
CREATE UNIQUE INDEX uq_matches_source_ca_id ON matches (source_ca_id);

CREATE TABLE match_source_reference
(
    match_id            INTEGER NOT NULL,
    provider            VARCHAR(100) NOT NULL,
    provider_record_key VARCHAR(255) NOT NULL,
    source_record_id    CHAR(36) NOT NULL,
    raw_content_digest  CHAR(64) NOT NULL,
    PRIMARY KEY (match_id, provider, source_record_id),
    UNIQUE (provider, source_record_id),
    FOREIGN KEY (match_id) REFERENCES matches (id)
);
CREATE INDEX idx_match_source_record ON match_source_reference (source_record_id);

CREATE TABLE innings
(
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    match_id        INTEGER NOT NULL,
    innings_number  INTEGER NOT NULL,
    batting_team_id INTEGER NOT NULL,
    bowling_team_id INTEGER NOT NULL,
    declared        BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (match_id, innings_number),
    FOREIGN KEY (match_id) REFERENCES matches (id),
    FOREIGN KEY (batting_team_id) REFERENCES teams (id),
    FOREIGN KEY (bowling_team_id) REFERENCES teams (id)
);
CREATE INDEX idx_innings_batting_team ON innings (batting_team_id);
CREATE INDEX idx_innings_bowling_team ON innings (bowling_team_id);

CREATE TABLE wickets
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    source_wicket_id INTEGER NOT NULL,
    wicket_kind      VARCHAR(30) NULL,
    UNIQUE (source_wicket_id)
);
CREATE INDEX idx_wickets_kind ON wickets (wicket_kind);

CREATE TABLE deliveries
(
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    source_ball_id   INTEGER NOT NULL,
    match_id         INTEGER NOT NULL,
    match_date_id    INTEGER NULL,
    innings_id       INTEGER NOT NULL,
    batting_team_id  INTEGER NOT NULL,
    bowling_team_id  INTEGER NOT NULL,
    batter_id        INTEGER NOT NULL,
    non_striker_id   INTEGER NOT NULL,
    bowler_id        INTEGER NOT NULL,
    over_number      INTEGER NOT NULL,
    ball_number      INTEGER NOT NULL,
    ball_in_over     INTEGER NOT NULL,
    innings_order    INTEGER NOT NULL,
    batter_runs      INTEGER NOT NULL,
    extra_runs       INTEGER NOT NULL,
    total_runs       INTEGER NOT NULL,
    no_balls         INTEGER NOT NULL,
    wides            INTEGER NOT NULL,
    byes             INTEGER NOT NULL,
    leg_byes         INTEGER NOT NULL,
    non_boundary     INTEGER NULL,
    powerplay        INTEGER NOT NULL,
    wicket_count     INTEGER NOT NULL,
    UNIQUE (source_ball_id),
    FOREIGN KEY (match_id) REFERENCES matches (id),
    FOREIGN KEY (match_date_id) REFERENCES dates (date_id),
    FOREIGN KEY (innings_id) REFERENCES innings (id),
    FOREIGN KEY (batting_team_id) REFERENCES teams (id),
    FOREIGN KEY (bowling_team_id) REFERENCES teams (id),
    FOREIGN KEY (batter_id) REFERENCES people (id),
    FOREIGN KEY (non_striker_id) REFERENCES people (id),
    FOREIGN KEY (bowler_id) REFERENCES people (id)
);
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

CREATE TABLE match_people
(
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    match_id  INTEGER NOT NULL,
    person_id INTEGER NOT NULL,
    role_code VARCHAR(32) NOT NULL,
    UNIQUE (match_id, person_id, role_code),
    FOREIGN KEY (match_id) REFERENCES matches (id),
    FOREIGN KEY (person_id) REFERENCES people (id)
);
CREATE INDEX idx_match_people_person ON match_people (person_id);
CREATE INDEX idx_match_people_role ON match_people (role_code);

CREATE TABLE delivery_wickets
(
    delivery_id INTEGER NOT NULL,
    wicket_id   INTEGER NOT NULL,
    PRIMARY KEY (delivery_id, wicket_id),
    FOREIGN KEY (delivery_id) REFERENCES deliveries (id),
    FOREIGN KEY (wicket_id) REFERENCES wickets (id)
);
CREATE INDEX idx_delivery_wickets_wicket ON delivery_wickets (wicket_id);

CREATE TABLE delivery_fielders
(
    delivery_id INTEGER NOT NULL,
    wicket_id   INTEGER NOT NULL,
    person_id   INTEGER NOT NULL,
    PRIMARY KEY (delivery_id, wicket_id, person_id),
    FOREIGN KEY (delivery_id) REFERENCES deliveries (id),
    FOREIGN KEY (wicket_id) REFERENCES wickets (id),
    FOREIGN KEY (person_id) REFERENCES people (id)
);
CREATE INDEX idx_delivery_fielders_wicket ON delivery_fielders (wicket_id);
CREATE INDEX idx_delivery_fielders_person ON delivery_fielders (person_id);

CREATE TABLE IF NOT EXISTS team_name_aliases
(
    source_system    VARCHAR(50)  NOT NULL,
    source_team_name VARCHAR(100) NOT NULL,
    stats_team_id    INTEGER      NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_team_name_aliases_stats_team_id ON team_name_aliases (stats_team_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_team_name_aliases_source_name_team
    ON team_name_aliases (source_system, source_team_name, stats_team_id);

insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Cumilla Warriors', 45116);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Fortune Barishal', 48630);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Sylhet Super Stars', 36985);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'North-West Warriors', 44366);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Barbados Tridents', 48094);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Trinidad & Tobago Red Steel', 25995);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Warriors', 98);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Titans', 250);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Lions', 125);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Knights', 110);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Dolphins', 46696);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Warriors (FairBreak) Women', 49084);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Birmingham Phoenix', 45098);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'London Spirit', 45099);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Manchester Originals', 45100);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Northern Superchargers', 45101);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Oval Invincibles', 45102);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Southern Brave', 45103);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Trent Rockets', 45104);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Welsh Fire', 45105);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'MI London', 55090);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Sunrisers Leeds', 55091);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Manchester Super Giants', 55092);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Sharjah Warriorz', 50358);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Sunrisers Hyderabad', 22793);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Royal Challengers Bengaluru', 6927);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Kandy Customs Cricket Club', 28817);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Nugegoda Sports Welfare Club', 28516);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Central Stags', 66);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Otago Volts', 260);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Wellington Firebirds', 389);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Auckland Aces', 19);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Birmingham Bears', 387);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Africa XI', 1976);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Asia XI', 1782);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Rawalpindiz', 55249);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Himachal', 147);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Jammu & Kashmir', 159);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Hyderabad (India)', 780);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Odisha', 409);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'ICC World XI', 1384);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Swaziland', 48403);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Zimbabwe', 31229);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Turks and Caicos Island', 2319);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'South Africa Women', 25291);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'South Africa', 10312);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Guyana Amazon Warriors Women', 55513);
insert into team_name_aliases values ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Royal Challengers Bengaluru Women', 51559);

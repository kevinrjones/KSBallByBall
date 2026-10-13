USE acs_ball_by_ball;

CREATE TABLE dates
(
    date_id          INT        NOT NULL PRIMARY KEY,
    calendar_date    DATE       NOT NULL,
    calendar_year    SMALLINT   NOT NULL,
    calendar_quarter TINYINT    NOT NULL,
    calendar_month   TINYINT    NOT NULL,
    month_name       VARCHAR(9) NOT NULL,
    week_of_year     TINYINT    NOT NULL,
    day_of_month     TINYINT    NOT NULL,
    day_of_week      TINYINT    NOT NULL,
    day_name         VARCHAR(9) NOT NULL,
    is_weekend       BOOLEAN    NOT NULL,
    UNIQUE KEY uq_dates_calendar_date (calendar_date)
) ENGINE = InnoDB;

CREATE TABLE teams
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_team_id INT             NOT NULL,
    team_name      VARCHAR(100)    NOT NULL,
    UNIQUE KEY uq_teams_source_id (source_team_id)
) ENGINE = InnoDB;

CREATE INDEX idx_teams_name ON teams (team_name);

CREATE TABLE people
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_person_id VARCHAR(10)     NOT NULL,
    full_name        VARCHAR(200)    NOT NULL,
    sort_name_part   VARCHAR(200)    NOT NULL,
    other_name_part  VARCHAR(200)    NOT NULL,
    ca_id            INT             NOT NULL,
    UNIQUE KEY uq_people_source_id (source_person_id)
) ENGINE = InnoDB;

CREATE INDEX idx_people_full_name ON people (full_name);
CREATE INDEX idx_people_sort_name ON people (sort_name_part);
CREATE INDEX idx_people_ca_id ON people (ca_id);

CREATE TABLE grounds
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_ground_id INT             NOT NULL,
    ground_name      VARCHAR(500)    NOT NULL,
    UNIQUE KEY uq_grounds_source_id (source_ground_id)
) ENGINE = InnoDB;

CREATE INDEX idx_grounds_name ON grounds (ground_name);

CREATE TABLE matches
(
    id                 BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    canonical_match_id CHAR(36)        NULL,
    public_match_id    BIGINT          NULL,
    source_ca_id       VARCHAR(20)     NULL,
    file_name          VARCHAR(120)    NOT NULL,
    match_in_series    INT             NOT NULL,
    match_type         VARCHAR(15)     NOT NULL,
    event_name         VARCHAR(200)    NOT NULL,
    match_date_text    VARCHAR(200)    NOT NULL,
    season             VARCHAR(200)    NOT NULL,
    match_start_year   VARCHAR(200)    NOT NULL,
    match_start_date_id INT            NULL,
    balls_per_over     INT             NOT NULL,
    added_timestamp    DATETIME        NOT NULL,
    team1_id           BIGINT UNSIGNED  NOT NULL,
    team2_id           BIGINT UNSIGNED  NOT NULL,
    ground_id          BIGINT UNSIGNED  NOT NULL,
    toss_team_id       BIGINT UNSIGNED  NOT NULL,
    toss_decision      VARCHAR(10)     NULL,
    victory_type       VARCHAR(15)     NOT NULL,
    winner_team_id     BIGINT UNSIGNED  NULL,
    loser_team_id      BIGINT UNSIGNED  NULL,
    duration_days      INT             NOT NULL,
    margin             INT             NOT NULL,
    match_count        TINYINT UNSIGNED NOT NULL DEFAULT 1,
    UNIQUE KEY uq_matches_canonical_id (canonical_match_id),
    UNIQUE KEY uq_matches_public_id (public_match_id),
    UNIQUE KEY uq_matches_source_ca_id (source_ca_id),
    KEY idx_matches_type (match_type),
    KEY idx_matches_file_name (file_name),
    KEY idx_matches_type_year (match_type, match_start_year),
    KEY idx_matches_teams_type (match_type, team1_id, team2_id),
    KEY idx_matches_season (season),
    KEY idx_matches_start_date (match_start_date_id),
    KEY idx_matches_team1 (team1_id),
    KEY idx_matches_team2 (team2_id),
    KEY idx_matches_ground (ground_id),
    CONSTRAINT fk_matches_start_date FOREIGN KEY (match_start_date_id) REFERENCES dates (date_id),
    CONSTRAINT fk_matches_team1 FOREIGN KEY (team1_id) REFERENCES teams (id),
    CONSTRAINT fk_matches_team2 FOREIGN KEY (team2_id) REFERENCES teams (id),
    CONSTRAINT fk_matches_ground FOREIGN KEY (ground_id) REFERENCES grounds (id),
    CONSTRAINT fk_matches_toss_team FOREIGN KEY (toss_team_id) REFERENCES teams (id),
    CONSTRAINT fk_matches_winner_team FOREIGN KEY (winner_team_id) REFERENCES teams (id),
    CONSTRAINT fk_matches_loser_team FOREIGN KEY (loser_team_id) REFERENCES teams (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS team_name_aliases
(
    source_system    VARCHAR(50)  NOT NULL,
    source_team_name VARCHAR(100) NOT NULL,
    stats_team_id    INT          NOT NULL
) ENGINE = InnoDB;

SET @team_name_aliases_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'team_name_aliases'
      AND index_name = 'idx_team_name_aliases_stats_team_id'
);
SET @team_name_aliases_index_ddl = IF(
    @team_name_aliases_index_exists = 0,
    'CREATE INDEX idx_team_name_aliases_stats_team_id ON team_name_aliases (stats_team_id)',
    'SELECT 1'
);
PREPARE team_name_aliases_index_statement FROM @team_name_aliases_index_ddl;
EXECUTE team_name_aliases_index_statement;
DEALLOCATE PREPARE team_name_aliases_index_statement;

SET @team_name_aliases_unique_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'team_name_aliases'
      AND index_name = 'uq_team_name_aliases_source_name_team'
);
SET @team_name_aliases_unique_index_ddl = IF(
    @team_name_aliases_unique_index_exists = 0,
    'CREATE UNIQUE INDEX uq_team_name_aliases_source_name_team ON team_name_aliases (source_system, source_team_name, stats_team_id)',
    'SELECT 1'
);
PREPARE team_name_aliases_unique_index_statement FROM @team_name_aliases_unique_index_ddl;
EXECUTE team_name_aliases_unique_index_statement;
DEALLOCATE PREPARE team_name_aliases_unique_index_statement;

CREATE TABLE match_source_reference
(
    match_id            BIGINT          NOT NULL,
    provider            VARCHAR(100)    NOT NULL,
    provider_record_key VARCHAR(255)    NOT NULL,
    source_record_id    CHAR(36)        NOT NULL,
    raw_content_digest  CHAR(64)        NOT NULL,
    PRIMARY KEY (match_id, provider, source_record_id),
    UNIQUE KEY uq_match_source_provider_record (provider, source_record_id),
    CONSTRAINT fk_match_source_match FOREIGN KEY (match_id) REFERENCES matches (id)
) ENGINE = InnoDB;

CREATE INDEX idx_match_source_record ON match_source_reference (source_record_id);

CREATE TABLE innings
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    match_id         BIGINT          NOT NULL,
    innings_number   INT             NOT NULL,
    batting_team_id  BIGINT UNSIGNED  NOT NULL,
    bowling_team_id  BIGINT UNSIGNED  NOT NULL,
    declared         BOOLEAN         NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_innings_match_number (match_id, innings_number),
    KEY idx_innings_batting_team (batting_team_id),
    KEY idx_innings_bowling_team (bowling_team_id),
    CONSTRAINT fk_innings_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_innings_batting_team FOREIGN KEY (batting_team_id) REFERENCES teams (id),
    CONSTRAINT fk_innings_bowling_team FOREIGN KEY (bowling_team_id) REFERENCES teams (id)
) ENGINE = InnoDB;

CREATE TABLE wickets
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_wicket_id INT             NOT NULL,
    wicket_kind      VARCHAR(30)     NULL,
    UNIQUE KEY uq_wickets_source_id (source_wicket_id)
) ENGINE = InnoDB;

CREATE INDEX idx_wickets_kind ON wickets (wicket_kind);

CREATE TABLE deliveries
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_ball_id    INT             NOT NULL,
    match_id          BIGINT          NOT NULL,
    match_date_id     INT             NULL,
    innings_id        BIGINT UNSIGNED  NOT NULL,
    batting_team_id   BIGINT UNSIGNED  NOT NULL,
    bowling_team_id   BIGINT UNSIGNED  NOT NULL,
    batter_id         BIGINT UNSIGNED  NOT NULL,
    non_striker_id    BIGINT UNSIGNED  NOT NULL,
    bowler_id         BIGINT UNSIGNED  NOT NULL,
    over_number       INT             NOT NULL,
    ball_number       INT             NOT NULL,
    ball_in_over      INT             NOT NULL,
    innings_order     INT             NOT NULL,
    batter_runs       INT             NOT NULL,
    extra_runs        INT             NOT NULL,
    total_runs        INT             NOT NULL,
    no_balls          INT             NOT NULL,
    wides             INT             NOT NULL,
    byes              INT             NOT NULL,
    leg_byes          INT             NOT NULL,
    non_boundary      INT             NULL,
    powerplay         INT             NOT NULL,
    wicket_count      INT             NOT NULL,
    UNIQUE KEY uq_deliveries_source_id (source_ball_id),
    KEY idx_deliveries_match_order (match_id, innings_order),
    KEY idx_deliveries_match_sequence (match_id, innings_order, over_number, ball_in_over),
    KEY idx_deliveries_date (match_date_id),
    KEY idx_deliveries_innings (innings_id),
    KEY idx_deliveries_batting_team (batting_team_id),
    KEY idx_deliveries_bowling_team (bowling_team_id),
    KEY idx_deliveries_batter (batter_id),
    KEY idx_deliveries_non_striker (non_striker_id),
    KEY idx_deliveries_bowler (bowler_id),
    KEY idx_deliveries_over (over_number),
    KEY idx_deliveries_ball_in_over (ball_in_over),
    KEY idx_deliveries_powerplay (powerplay),
    CONSTRAINT fk_deliveries_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_deliveries_date FOREIGN KEY (match_date_id) REFERENCES dates (date_id),
    CONSTRAINT fk_deliveries_innings FOREIGN KEY (innings_id) REFERENCES innings (id),
    CONSTRAINT fk_deliveries_batting_team FOREIGN KEY (batting_team_id) REFERENCES teams (id),
    CONSTRAINT fk_deliveries_bowling_team FOREIGN KEY (bowling_team_id) REFERENCES teams (id),
    CONSTRAINT fk_deliveries_batter FOREIGN KEY (batter_id) REFERENCES people (id),
    CONSTRAINT fk_deliveries_non_striker FOREIGN KEY (non_striker_id) REFERENCES people (id),
    CONSTRAINT fk_deliveries_bowler FOREIGN KEY (bowler_id) REFERENCES people (id)
) ENGINE = InnoDB;

CREATE TABLE match_people
(
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    match_id  BIGINT          NOT NULL,
    person_id BIGINT UNSIGNED  NOT NULL,
    role_code VARCHAR(32)     NOT NULL,
    UNIQUE KEY uq_match_people_role (match_id, person_id, role_code),
    KEY idx_match_people_person (person_id),
    KEY idx_match_people_role (role_code),
    CONSTRAINT fk_match_people_match FOREIGN KEY (match_id) REFERENCES matches (id),
    CONSTRAINT fk_match_people_person FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;

CREATE TABLE delivery_wickets
(
    delivery_id BIGINT UNSIGNED NOT NULL,
    wicket_id   BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (delivery_id, wicket_id),
    KEY idx_delivery_wickets_wicket (wicket_id),
    CONSTRAINT fk_delivery_wickets_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id),
    CONSTRAINT fk_delivery_wickets_wicket FOREIGN KEY (wicket_id) REFERENCES wickets (id)
) ENGINE = InnoDB;

CREATE TABLE delivery_fielders
(
    delivery_id BIGINT UNSIGNED NOT NULL,
    wicket_id   BIGINT UNSIGNED NOT NULL,
    person_id   BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (delivery_id, wicket_id, person_id),
    KEY idx_delivery_fielders_wicket (wicket_id),
    KEY idx_delivery_fielders_person (person_id),
    CONSTRAINT fk_delivery_fielders_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries (id),
    CONSTRAINT fk_delivery_fielders_wicket FOREIGN KEY (wicket_id) REFERENCES wickets (id),
    CONSTRAINT fk_delivery_fielders_person FOREIGN KEY (person_id) REFERENCES people (id)
) ENGINE = InnoDB;
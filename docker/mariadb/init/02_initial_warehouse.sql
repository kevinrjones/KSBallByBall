USE acs_ball_by_ball;

-- Dimensional warehouse schema.
-- Source identifiers are retained for ETL traceability; *_key columns are
-- surrogate keys used by the warehouse facts.

-- One row per calendar date. The ETL process is responsible for populating
-- this conformed dimension before loading facts.
CREATE TABLE dim_date
(
    date_key         INT        NOT NULL PRIMARY KEY,
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

    UNIQUE KEY uq_dim_date_calendar_date (calendar_date)
) ENGINE = InnoDB;

-- Type 1 team dimension sourced from Teams.
CREATE TABLE dim_team
(
    team_key       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_team_id INT             NOT NULL,
    team_name      VARCHAR(100)    NOT NULL,

    UNIQUE KEY uq_dim_team_source_id (source_team_id),
    KEY idx_dim_team_name (team_name)
) ENGINE = InnoDB;

-- Shared person dimension for players and all match-official roles.
CREATE TABLE dim_person
(
    person_key       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_person_id VARCHAR(10)     NOT NULL,
    full_name        VARCHAR(200)    NOT NULL,
    sort_name_part   VARCHAR(200)    NOT NULL,
    other_name_part  VARCHAR(200)    NOT NULL,
    ca_id            INT             NOT NULL,

    UNIQUE KEY uq_dim_person_source_id (source_person_id),
    KEY idx_dim_person_full_name (full_name),
    KEY idx_dim_person_sort_name (sort_name_part),
    KEY idx_dim_person_ca_id (ca_id)
) ENGINE = InnoDB;

CREATE TABLE dim_ground
(
    ground_key       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_ground_id INT             NOT NULL,
    ground_name      VARCHAR(500)    NOT NULL,

    UNIQUE KEY uq_dim_ground_source_id (source_ground_id),
    KEY idx_dim_ground_name (ground_name)
) ENGINE = InnoDB;

-- One row per source match. Match-level descriptors are kept here so they
-- are shared by both the match and delivery facts.
CREATE TABLE dim_match
(
    match_key            BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    canonical_match_id   CHAR(36)        NULL,
    source_ca_id         VARCHAR(20)     NULL,
    file_name            VARCHAR(120)    NOT NULL,
    match_in_series      INT             NOT NULL,
    match_type           VARCHAR(15)     NOT NULL,
    event_name           VARCHAR(200)    NOT NULL,
    match_date_text      VARCHAR(200)    NOT NULL,
    season               VARCHAR(200)    NOT NULL,
    match_start_year     VARCHAR(200)    NOT NULL,
    match_start_date_key INT             NULL,
    balls_per_over       INT             NOT NULL,
    added_timestamp      DATETIME        NOT NULL,
    team1_key            BIGINT UNSIGNED NOT NULL,
    team2_key            BIGINT UNSIGNED NOT NULL,
    ground_key           BIGINT UNSIGNED NOT NULL,
    toss_team_key        BIGINT UNSIGNED NOT NULL,
    toss_decision        VARCHAR(10)     NULL,
    victory_type         VARCHAR(15)     NOT NULL,
    winner_team_key      BIGINT UNSIGNED NULL,
    loser_team_key       BIGINT UNSIGNED NULL,
    public_match_id      BIGINT          NULL,

    UNIQUE KEY uq_dim_match_canonical_id (canonical_match_id),
    UNIQUE KEY uq_dim_match_public_match_id (public_match_id),

    KEY idx_dim_match_type (match_type),
    KEY idx_dim_match_file_name (file_name),
    KEY idx_dim_match_type_year (match_type, match_start_year),
    KEY idx_dim_match_teams_type (match_type, team1_key, team2_key),
    KEY idx_dim_match_season (season),
    KEY idx_dim_match_start_date (match_start_date_key),
    KEY idx_dim_match_team1 (team1_key),
    KEY idx_dim_match_team2 (team2_key),
    KEY idx_dim_match_ground (ground_key),

    CONSTRAINT fk_dim_match_start_date
        FOREIGN KEY (match_start_date_key) REFERENCES dim_date (date_key),
    CONSTRAINT fk_dim_match_team1
        FOREIGN KEY (team1_key) REFERENCES dim_team (team_key),
    CONSTRAINT fk_dim_match_team2
        FOREIGN KEY (team2_key) REFERENCES dim_team (team_key),
    CONSTRAINT fk_dim_match_ground
        FOREIGN KEY (ground_key) REFERENCES dim_ground (ground_key),
    CONSTRAINT fk_dim_match_toss_team
        FOREIGN KEY (toss_team_key) REFERENCES dim_team (team_key),
    CONSTRAINT fk_dim_match_winner_team
        FOREIGN KEY (winner_team_key) REFERENCES dim_team (team_key),
    CONSTRAINT fk_dim_match_loser_team
        FOREIGN KEY (loser_team_key) REFERENCES dim_team (team_key)
) ENGINE = InnoDB;

CREATE TABLE match_source_reference
(
    match_key           BIGINT       NOT NULL,
    provider            VARCHAR(100) NOT NULL,
    provider_record_key VARCHAR(255) NOT NULL,
    source_record_id    CHAR(36)     NOT NULL,
    raw_content_digest  CHAR(64)     NOT NULL,

    CONSTRAINT pk_match_source_reference PRIMARY KEY (match_key, provider, source_record_id),
    CONSTRAINT uq_match_source_provider_record UNIQUE (provider, source_record_id),
    CONSTRAINT fk_match_source_match FOREIGN KEY (match_key) REFERENCES dim_match (match_key)
) ENGINE = InnoDB;

CREATE INDEX idx_match_source_record ON match_source_reference (source_record_id);

-- One row per innings in a match. This is a helper dimension for innings-level
-- analysis and avoids repeating the innings/team relationship on every query.
CREATE TABLE dim_innings
(
    innings_key      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    match_key        BIGINT          NOT NULL,
    innings_number   INT             NOT NULL,
    batting_team_key BIGINT UNSIGNED NOT NULL,
    bowling_team_key BIGINT UNSIGNED NOT NULL,

    UNIQUE KEY uq_dim_innings_match_number (match_key, innings_number),
    KEY idx_dim_innings_batting_team (batting_team_key),
    KEY idx_dim_innings_bowling_team (bowling_team_key),

    CONSTRAINT fk_dim_innings_match
        FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    CONSTRAINT fk_dim_innings_batting_team
        FOREIGN KEY (batting_team_key) REFERENCES dim_team (team_key),
    CONSTRAINT fk_dim_innings_bowling_team
        FOREIGN KEY (bowling_team_key) REFERENCES dim_team (team_key)
) ENGINE = InnoDB;

CREATE TABLE dim_wicket
(
    wicket_key       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_wicket_id INT             NOT NULL,
    wicket_kind      VARCHAR(30)     NULL,

    UNIQUE KEY uq_dim_wicket_source_id (source_wicket_id),
    KEY idx_dim_wicket_kind (wicket_kind)
) ENGINE = InnoDB;

-- Fact grain: one row per source match.
CREATE TABLE fact_match
(
    match_key      BIGINT           NOT NULL PRIMARY KEY,
    match_date_key INT              NULL,
    ground_key     BIGINT UNSIGNED  NOT NULL,
    duration_days  INT              NOT NULL,
    margin         INT              NOT NULL,
    match_count    TINYINT UNSIGNED NOT NULL DEFAULT 1,

    KEY idx_fact_match_date (match_date_key),
    KEY idx_fact_match_ground (ground_key),

    CONSTRAINT fk_fact_match_match
        FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    CONSTRAINT fk_fact_match_date
        FOREIGN KEY (match_date_key) REFERENCES dim_date (date_key),
    CONSTRAINT fk_fact_match_ground
        FOREIGN KEY (ground_key) REFERENCES dim_ground (ground_key)
) ENGINE = InnoDB;

-- Fact grain: one row per delivery in the source BallByBall table.
CREATE TABLE fact_delivery
(
    delivery_key     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_ball_id   INT             NOT NULL,
    match_key        BIGINT          NOT NULL,
    match_date_key   INT             NULL,
    innings_key      BIGINT UNSIGNED NOT NULL,
    batting_team_key BIGINT UNSIGNED NOT NULL,
    bowling_team_key BIGINT UNSIGNED NOT NULL,
    batter_key       BIGINT UNSIGNED NOT NULL,
    non_striker_key  BIGINT UNSIGNED NOT NULL,
    bowler_key       BIGINT UNSIGNED NOT NULL,
    over_number      INT             NOT NULL,
    ball_number      INT             NOT NULL,
    ball_in_over     INT             NOT NULL,
    innings_order    INT             NOT NULL,
    batter_runs      INT             NOT NULL,
    extra_runs       INT             NOT NULL,
    total_runs       INT             NOT NULL,
    no_balls         INT             NOT NULL,
    wides            INT             NOT NULL,
    byes             INT             NOT NULL,
    leg_byes         INT             NOT NULL,
    non_boundary     INT             NULL,
    powerplay        INT             NOT NULL,
    wicket_count     INT             NOT NULL,

    UNIQUE KEY uq_fact_delivery_source_id (source_ball_id),
    KEY idx_fact_delivery_match_order (match_key, innings_order),
    KEY idx_fact_delivery_match_seq (match_key, innings_order, over_number, ball_in_over),
    KEY idx_fact_delivery_date (match_date_key),
    KEY idx_fact_delivery_innings (innings_key),
    KEY idx_fact_delivery_batting_team (batting_team_key),
    KEY idx_fact_delivery_bowling_team (bowling_team_key),
    KEY idx_fact_delivery_batter (batter_key),
    KEY idx_fact_delivery_non_striker (non_striker_key),
    KEY idx_fact_delivery_bowler (bowler_key),
    KEY idx_fact_delivery_over (over_number),
    KEY idx_fact_delivery_ball_in_over (ball_in_over),
    KEY idx_fact_delivery_powerplay (powerplay),

    CONSTRAINT fk_fact_delivery_match
        FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    CONSTRAINT fk_fact_delivery_date
        FOREIGN KEY (match_date_key) REFERENCES dim_date (date_key),
    CONSTRAINT fk_fact_delivery_innings
        FOREIGN KEY (innings_key) REFERENCES dim_innings (innings_key),
    CONSTRAINT fk_fact_delivery_batting_team
        FOREIGN KEY (batting_team_key) REFERENCES dim_team (team_key),
    CONSTRAINT fk_fact_delivery_bowling_team
        FOREIGN KEY (bowling_team_key) REFERENCES dim_team (team_key),
    CONSTRAINT fk_fact_delivery_batter
        FOREIGN KEY (batter_key) REFERENCES dim_person (person_key),
    CONSTRAINT fk_fact_delivery_non_striker
        FOREIGN KEY (non_striker_key) REFERENCES dim_person (person_key),
    CONSTRAINT fk_fact_delivery_bowler
        FOREIGN KEY (bowler_key) REFERENCES dim_person (person_key)
) ENGINE = InnoDB;

-- Factless bridge: one row per person/role assignment in a match.
CREATE TABLE bridge_match_person
(
    match_person_key BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    match_key        BIGINT          NOT NULL,
    person_key       BIGINT UNSIGNED NOT NULL,
    role_code        VARCHAR(32)     NOT NULL,

    UNIQUE KEY uq_bridge_match_person_role (match_key, person_key, role_code),
    KEY idx_bridge_match_person_person (person_key),
    KEY idx_bridge_match_person_role (role_code),

    CONSTRAINT fk_bridge_match_person_match
        FOREIGN KEY (match_key) REFERENCES dim_match (match_key),
    CONSTRAINT fk_bridge_match_person_person
        FOREIGN KEY (person_key) REFERENCES dim_person (person_key)
) ENGINE = InnoDB;

-- Factless bridge: a delivery can have more than one associated wicket.
CREATE TABLE bridge_delivery_wicket
(
    delivery_key BIGINT UNSIGNED NOT NULL,
    wicket_key   BIGINT UNSIGNED NOT NULL,

    PRIMARY KEY (delivery_key, wicket_key),
    KEY idx_bridge_delivery_wicket_wicket (wicket_key),

    CONSTRAINT fk_bridge_delivery_wicket_delivery
        FOREIGN KEY (delivery_key) REFERENCES fact_delivery (delivery_key),
    CONSTRAINT fk_bridge_delivery_wicket_wicket
        FOREIGN KEY (wicket_key) REFERENCES dim_wicket (wicket_key)
) ENGINE = InnoDB;


CREATE TABLE bridge_delivery_fielder
(
    delivery_key BIGINT UNSIGNED NOT NULL,
    wicket_key   BIGINT UNSIGNED NOT NULL,
    person_key   BIGINT UNSIGNED NOT NULL,

    PRIMARY KEY (delivery_key, wicket_key, person_key),
    KEY idx_bridge_delivery_fielder_wicket (wicket_key),
    KEY idx_bridge_delivery_fielder_person (person_key),

    CONSTRAINT fk_bridge_delivery_fielder_delivery
        FOREIGN KEY (delivery_key) REFERENCES fact_delivery (delivery_key),
    CONSTRAINT fk_bridge_delivery_fielder_wicket
        FOREIGN KEY (wicket_key) REFERENCES dim_wicket (wicket_key),
    CONSTRAINT fk_bridge_delivery_fielder_person
        FOREIGN KEY (person_key) REFERENCES dim_person (person_key)
) ENGINE = InnoDB;

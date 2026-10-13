# DataLoader developer commands

This document is the developer runbook for the `DataLoader` project. Keep it
updated whenever a Gradle task, parser option, output adapter, migration, or
database-loading workflow is added or changed.

## Prerequisites

- Java 21. Gradle uses the Java toolchain configured in the module build files.
- MariaDB client tools (`mariadb`) when loading data into MariaDB.
- Docker and the reproducible dual-database setup in `docs/setup/SETUP-DB.md`
  when running the MariaDB and PostgreSQL test environments.
- A Cricsheet base directory containing raw retrieval data, normalized shared-
  schema data, and the player registry CSV. The updater consumes normalized
  JSON produced by `bbb-parse-cricsheet`.

The commands below assume the shell variables have been set. Use paths without
spaces when passing them through Gradle's `--args` option.

```bash
export CRICSHEET_ROOT=/path/to/cricsheet
export CRICSHEET_RAW=cricsheet
export CRICSHEET_DATA=normalized
export PLAYER_REGISTRY=people.csv
export CSV_DIR=generated-warehouse-csv
export SQL_FILE=generated-warehouse.sql
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=cricsheet
export DB_USER=cricsheet
export DB_PASSWORD='change-me'
export STATS_DB_HOST=localhost
export STATS_DB_PORT=3306
export STATS_DB_NAME=stats
export STATS_DB_URL="jdbc:mariadb://${STATS_DB_HOST}:${STATS_DB_PORT}/${STATS_DB_NAME}"
export STATS_DB_USER=stats
export STATS_DB_PASSWORD='change-me'
export BBB_DB_URL="jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME}"
export BBB_DB_USER=$DB_USER
export BBB_DB_PASSWORD='change-me'
```

## Build and verify

Run the complete verification suite:

```bash
./gradlew clean check --no-daemon
```

Run the database-loader tests only:

```bash
./gradlew :bbb-update-database:test --no-daemon
```

Compile the loader without running it:

```bash
./gradlew :bbb-update-database:compileKotlin --no-daemon
```

Display the parser command-line help:

```bash
./gradlew :bbb-update-database:run --no-daemon --args="-h"
```

Display the Cricsheet retrieval application's command-line help:

```bash
./gradlew :bbb-get-cricsheet-data:run --no-daemon --args="--help"
```

Download the Cricsheet JSON archives and register CSV files:

```bash
./gradlew :bbb-get-cricsheet-data:run --no-daemon \
  --args="--base-directory /path/to/data --data-directory cricsheet"
```

The command creates `/path/to/data/cricsheet/zips`, extracts each JSON archive
into `/path/to/data/cricsheet/<archive-name-without-.zip>`, and stores
`people.csv` and `names.csv` directly in `/path/to/data`. For example,
`bbl_json.zip` is extracted into `/path/to/data/cricsheet/bbl_json`.
`--names-directory` is accepted for compatibility but does not change the
register destination. Use `--force` when the data directory already exists.
The base directory must be absolute, while the data directory must be
relative to it.

For a scheduled partial refresh, add `--nightly` (or `-n`):

```bash
./gradlew :bbb-get-cricsheet-data:run --no-daemon \
  --args="--base-directory /path/to/data --data-directory cricsheet --nightly"
```

Nightly mode downloads only `recently_added_7_json.zip` followed by
`recently_added_2_json.zip`. It stores the ZIPs in
`/path/to/data/cricsheet/zips` and merges their contents directly into
`/path/to/data/cricsheet`; when both archives contain a file, the second
archive wins. It always refreshes `people.csv` and `names.csv` in
`/path/to/data`, separately from the extracted JSON, reuses existing
directories without requiring `--force`, overwrites managed files, and keeps
stale or unrelated files. `--names-directory` is validated but does not change
the register destination. `--force` remains accepted as a harmless no-op in
nightly mode.

The retrieval options are:

| Option                     | Required | Description                                                                                                                 |
|----------------------------|----------|-----------------------------------------------------------------------------------------------------------------------------|
| `-h`, `--help`             | No       | Print command-line help.                                                                                                    |
| `--version`                | No       | Print the application version.                                                                                              |
| `-bd`, `--base-directory`  | Yes      | Absolute root directory for the download.                                                                                   |
| `-dd`, `--data-directory`  | Yes      | Relative directory for JSON match data and downloaded ZIPs.                                                                 |
| `-nd`, `--names-directory` | No       | Deprecated compatibility option; registers are always stored directly in the base directory.                                |
| `-f`, `--force`            | No       | Reuse existing target directories and overwrite managed files without deleting unrelated files.                             |
| `-n`, `--nightly`          | No       | Download the two fixed recent archives directly into the configured data directory; registers remain in the base directory. |

## IDE Gradle run configuration examples

The following commands mirror the Gradle run configurations currently
available in the IDE. Set the shell variables from the prerequisites section
before running them; adjust `DB_*` values when using a different database.

### `DataLoader [run-produce-sql]`

Generate the SQL output file using the default SQL output adapter:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory $CRICSHEET_ROOT --data-directory $CRICSHEET_DATA \
    --player-registry $PLAYER_REGISTRY --outputType SQL --outputFile $SQL_FILE"
```

### `applications [:bbb-update-database:run] (sql_file)`

Generate an offline SQL script explicitly with `SQL_FILE` output:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory $CRICSHEET_ROOT --data-directory $CRICSHEET_DATA \
    --player-registry $PLAYER_REGISTRY --outputType SQL_FILE \
    --outputFile $SQL_FILE"
```

### `applications [:bbb-update-database:run] (nightly-db)`

Import the normalized nightly data directly into MariaDB:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory $CRICSHEET_ROOT --data-directory $CRICSHEET_DATA \
    --player-registry $PLAYER_REGISTRY --nightly --outputType DATABASE \
    --connectionString jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME} \
    --userName $DB_USER --password $DB_PASSWORD"
```

### `applications [:bbb-update-database:run] (nightly-csv)`

Generate CSV output from the nightly data:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory $CRICSHEET_ROOT --data-directory $CRICSHEET_DATA \
    --player-registry $PLAYER_REGISTRY --nightly --outputType CSV \
    --csvDir $CSV_DIR"
```

### `applications [:bbb-parse-cricsheet:run] (nightly)`

Normalize the downloaded nightly JSON into a separate output directory. For
this configuration, point `CRICSHEET_RAW` at the nightly raw-data directory
before running the command:

```bash
./gradlew :bbb-parse-cricsheet:run --no-daemon \
  --args="--base-directory $CRICSHEET_ROOT --input $CRICSHEET_RAW \
    --output parsed --not-parsed not-parsed --stats-connection-string $STATS_DB_URL \
    --stats-user-name $STATS_DB_USER --stats-password-environment-variable STATS_DB_PASSWORD \
    --bbb-connection-string $BBB_DB_URL --bbb-user-name $BBB_DB_USER \
    --bbb-password-environment-variable BBB_DB_PASSWORD"
```

### `applications [:bb-web:run]`

Start the browser-facing web application. The IDE configuration name is kept
as shown, although the Gradle project is named `bbb-web`:

```bash
API_BASE_URL=http://localhost:8081 \
./gradlew :bbb-web:run --no-daemon
```

### `applications [:bbb-update-database:run] (csv)`

Generate warehouse CSV files from the full normalized data set:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory $CRICSHEET_ROOT --data-directory $CRICSHEET_DATA \
    --player-registry $PLAYER_REGISTRY --outputType CSV --csvDir $CSV_DIR"
```

### `applications [:bbb-parse-cricsheet:run]`

Normalize the full downloaded Cricsheet JSON into the shared schema:

```bash
./gradlew :bbb-parse-cricsheet:run --no-daemon \
  --args="--base-directory $CRICSHEET_ROOT --input $CRICSHEET_RAW \
    --output $CRICSHEET_DATA --not-parsed not-parsed --stats-connection-string $STATS_DB_URL \
    --stats-user-name $STATS_DB_USER --stats-password-environment-variable STATS_DB_PASSWORD \
    --bbb-connection-string $BBB_DB_URL --bbb-user-name $BBB_DB_USER \
    --bbb-password-environment-variable BBB_DB_PASSWORD"
```

## Normalize Cricsheet data

`bbb-parse-cricsheet` is the Cricsheet source adapter. It reads raw Cricsheet
JSON and writes one versioned `CanonicalMatchEnvelope` per input file. Each
envelope contains `BbbMatchData`; `match.sourceCaId` is an optional,
CricketArchive-specific lookup result and is `null` when no provider match is
found, rather than a source-neutral identity field. It also contains a provider-scoped UUIDv5,
an exact-byte SHA-256 digest, and explicit single-source merge evidence. The
safe basename stem is the Cricsheet provider key, so names such as
`wi_201706.json` are supported; parent directories do not affect identity. The
parser maps supported formats to warehouse values such as `tt`, `wtt`, `a`, and
`wa`.

Parsing requires connections to both the CricketArchive stats database and the
ball-by-ball database that owns the persistent aliases. Each match is first
matched by the two team names, first match date, and victory margin
(`Matches.HowMuch`); victory type is intentionally not used. If exact names do
not resolve a match, configured Cricsheet aliases are applied to one or both
teams and the same date, margin, and interchangeable-team lookup is retried. If
the margin-constrained lookup still finds no match, the parser retries with the
same date and interchangeable teams without `Matches.HowMuch`; duplicate stats
rows are accepted only when they identify one distinct `CaId`, while multiple
distinct candidates, missing matches, and invalid alias targets remain failures
rather than being guessed.
For female matches, ` Women` is normally appended to team names for stats
matching; Charlotte Edwards Cup, Rachael Heyhoe Flint Trophy, and Women's T20
Challenge Match matches are exceptions because their source team names already
match the stats naming convention. Female matches involving `The Blaze` leave
that team unsuffixed while the opponent continues to receive ` Women` when required. Matches with the exact event names `ICC World Cricket League
Europe Region Division Two Twenty20` and `Kenya T20 Quadrangular Series` have
no stats representation, so they skip the lookup, retain an empty
`sourceCaId`, and are still written below `--output`. `East Asia Cup` matches
in seasons `2016/17` and `2018`, `ICC World Twenty20 Asia Region Qualifier A`, `ICC World
Twenty20 Africa Region Qualifier A`, `ICC World Twenty20 Africa Region
Qualifier B`, `ICC World Twenty20 East Asia-Pacific Region Qualifier A`, and
`ICC World Twenty20 East Asia-Pacific Region Qualifier B` matches receive the
same treatment. `ICC World Twenty20 Europe Region Qualifier A` and `ICC World
Twenty20 Europe Region Qualifier B`, `ICC World Twenty20 Europe Region
Qualifier C`, `ICC World Twenty20 Americas Sub Regional Qualifier A`, and `ICC
World Twenty20 Asia Region Qualifier B` matches receive the same treatment;
other East Asia Cup seasons continue through normal stats linking. `ICC World
Twenty20 Africa Region Qualifier C` matches in season `2018/19`, `ICC World
Cricket League Americas Region Division One Twenty20` matches in season `2010`,
and `ACC Twenty20 Cup` matches in season `2014/15` receive the same treatment.
`Stan Nagaiah Trophy` matches receive the same treatment in all seasons.
`Continental Cup` matches receive the same treatment in all seasons. `Major
League Cricket` matches in season `2023` and `Asian Games Men's Cricket
Competition` matches in season `2014/15` receive the same treatment; other
seasons continue through normal stats linking. `Nepal Premier League` matches
receive the same treatment in all seasons.
The resulting `Matches.CaId` is written to the optional integration field
`match.sourceCaId` and later to `matches.source_ca_id`. Successful normalized files are written below the
`--output` directory. Any conversion, linking, or output error is logged
immediately, accumulated, and does not stop the remaining files; the original
raw file is copied to the matching relative path below `--not-parsed`. A final
error summary is logged and the parser exits non-zero when any file failed.
Lookup-specific failures, including the margin retry, are written to both the
console and `logs/lookup_failures.log`; that file uses the same 10 MB rotation,
60-day history, and 20 GB total-size cap as `logs/application.log`.

```bash
./gradlew :bbb-parse-cricsheet:run --no-daemon \
  --args="--base-directory /path/to/data --input cricsheet --output parsed \
    --not-parsed not-parsed \
    --stats-connection-string jdbc:mariadb://localhost:3306/stats \
    --stats-user-name stats --stats-password-environment-variable STATS_DB_PASSWORD \
    --bbb-connection-string jdbc:mariadb://localhost:3306/cricsheet \
    --bbb-user-name cricsheet --bbb-password-environment-variable BBB_DB_PASSWORD"
```

When a source name needs correction, add a reusable alias to the ball-by-ball
database after checking the target in the stats database:

```sql
SELECT Id, TeamId, Name FROM Teams WHERE TeamId = 123;

INSERT INTO team_name_aliases (source_system, source_team_name, stats_team_id)
VALUES ('f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b', 'Comilla Warriors', 123);
```

For Cricsheet, `source_system` must be the provider namespace UUID
`f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b`.

The dialect's `1__initial_tables.sql` migration creates the alias table, its
unique `(source_system, source_team_name, stats_team_id)` index, and the
source-match uniqueness constraint before aliases are inserted. The target must
resolve to exactly one stats `Teams.TeamId`; the parser never creates or guesses
aliases. Multiple rows with the same `source_team_name` are supported;
the parser validates and tries every target until a match is found. After
inserting the mappings, rerun the same parser command so the failed match is
retried with the persisted aliases.

The initial migration is schema-only; operator-owned alias rows are deliberately
not embedded in migrations. Re-importing a canonical envelope reconciles a newly
discovered `sourceCaId` into an existing warehouse row through the JDBC,
SQL-script, and CSV adapters, while conflicting IDs fail with an actionable
error.

The base directory must be absolute, while `--input`, `--output`, and
`--not-parsed` are safe relative child directories. Every `.json` file below
the input directory is processed recursively and its relative path is mirrored
below either the parsed output directory or the not-parsed raw-file directory.
Existing managed parsed files and failed raw files are replaced; stale and
unrelated output files remain. Invalid files are reported immediately and in a
final accumulated summary after the remaining files are attempted. The command
returns a non-zero status if any file failed.

Check dependency updates and refresh the version catalog when required:

```bash
./gradlew dependencyUpdates --no-daemon
./gradlew versionCatalogUpdate --no-daemon
```

## Database schema

The database schema is created by the Flyway migrations in the dialect-specific
directories under `bbb-update-database/migrations`: `mysql`, `postgres`, and
`sqlite`. MySQL remains the default. Select another directory with
`FLYWAY_DATABASE` (or `-Pmigration.database`) and configure the connection through
environment variables rather than putting credentials in a command history.

```bash
export FLYWAY_URL="jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME}"
export FLYWAY_USER="$DB_USER"
export FLYWAY_PASSWORD="$DB_PASSWORD"
./gradlew :bbb-update-database:flywayMigrate --no-daemon
```

For PostgreSQL, use the PostgreSQL JDBC URL and migration directory:

```bash
export FLYWAY_DATABASE=postgres
export FLYWAY_URL="jdbc:postgresql://localhost:5432/cricsheet"
export FLYWAY_USER=cricsheet
export FLYWAY_PASSWORD='change-me'
./gradlew :bbb-update-database:flywayMigrate --no-daemon
```

For SQLite, point the SQLite JDBC URL at a database file. SQLite has no
application schema, so the migration task uses SQLite's built-in `main` database
name instead of `cricsheet`. SQLite migrations run without Flyway's outer
transaction so the first migration statement can enable foreign keys:

```bash
export FLYWAY_DATABASE=sqlite
export FLYWAY_URL="jdbc:sqlite:/path/to/cricsheet.db"
./gradlew :bbb-update-database:flywayMigrate --no-daemon \
  -Pflyway.executeInTransaction=false
```

For a new MySQL or PostgreSQL database, ensure the `cricsheet` database exists
before running the migration. Each dialect directory contains one
`1__initial_tables.sql` migration with the complete normalized relational
schema, including team aliases and the nullable unique `source_ca_id`. It creates
`matches.id` as the internal generated key while keeping `canonical_match_id`
and the nullable unique `public_match_id` separate. `match_source_reference`
retains provider keys, source IDs, and raw digests.
Existing databases are rebuild targets: back up the old schema, apply the
single migration, and replay canonical envelopes rather than copying old
surrogate keys.

The API and web client use `/api/matches/{publicMatchId}/scoresheet`. The
repository resolves that value through `matches.public_match_id` and reads
the normalized relational tables. A deterministic public-ID collision fails
rather than probing for a different URL.

```bash
mariadb --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USER" --password="$DB_PASSWORD" \
  --execute="CREATE DATABASE IF NOT EXISTS $DB_NAME"
```

## Database updater command-line options

All parser runs use the Gradle application task:

```bash
./gradlew :bbb-update-database:run --no-daemon --args="<options>"
```

| Option                     | Required            | Description                                                                                                  |
|----------------------------|---------------------|--------------------------------------------------------------------------------------------------------------|
| `-h`, `--help`             | No                  | Print the command-line help.                                                                                 |
| `-bd`, `--base-directory`  | Yes                 | Absolute root directory containing scorecards and register data.                                             |
| `-dd`, `--data-directory`  | Yes                 | Relative match-data directory below `baseDirectory`.                                                         |
| `-nd`, `--names-directory` | No                  | Deprecated compatibility option; the register is read from `baseDirectory`.                                  |
| `-pr`, `--player-registry` | Yes                 | Player-registry filename relative to `baseDirectory`.                                                        |
| `-n`, `--nightly`          | No                  | Read JSON files from the configured data directory using the same discovery and metadata rules as full mode. |
| `-ot`, `--outputType`      | No                  | `SQL` (default), `DATABASE`, `SQL_FILE`, or `CSV`.                                                           |
| `-c`, `--connectionString` | For database output | JDBC connection string.                                                                                      |
| `-u`, `--userName`         | For database output | Database username.                                                                                           |
| `-p`, `--password`         | No                  | Database password. Prefer an environment variable or protected shell history.                                |
| `--database`               | For SQL files       | SQL dialect: `mariadb`, `postgres`, or `sqlite` (default: `mariadb`).                                        |
| `-o`, `--outputFile`       | For SQL files       | SQL script path relative to `baseDirectory`; with `SQL`, selects file output.                                |
| `-sf`, `--sqlFile`         | For SQL files       | Alias for `--outputFile`; the path is relative to `baseDirectory`.                                           |
| `-cd`, `--csvDir`          | For CSV output      | CSV directory relative to `baseDirectory`.                                                                   |
| `-f`, `--force`            | For CSV output      | Replace existing contents in the CSV output directory; without it, non-empty directories are refused.       |

The `--database` option applies only to generated SQL files. `mysql` is
deliberately rejected because the MariaDB script dialect may emit DDL that
MySQL 8 does not support. This is separate from Flyway migrations under
`bbb-update-database/migrations/mysql/` and direct JDBC output, where
`jdbc:mysql:` remains recognized.

The updater consumes only normalized `CanonicalMatchEnvelope` JSON from
`bbb-parse-cricsheet`; it does not decode raw Cricsheet snake-case fields.
Both full and nightly modes scan normalized JSON files recursively below
`[base]/[data]`, so archive-named full directories and flat nightly files use
the same importer. The player registry is read from
`[base]/[player-registry]` in both modes, matching the downloader's base-level
register files. The deprecated `--names-directory` option is accepted and
validated but does not alter this location:

SQL output file paths supplied with `-o`/`--outputFile` or `-sf`/`--sqlFile`
must be relative to `baseDirectory`; for example, `sql/update.sql` is written
to `[base]/sql/update.sql`. Absolute paths and paths that escape `baseDirectory`
are rejected.

CSV output directories supplied with `-cd`/`--csvDir` follow the same rule; for
example, `csv/warehouse` is written to `[base]/csv/warehouse`. Absolute paths
and paths that escape `baseDirectory` are rejected.

CSV transactions are coordinated by `CsvOutputAdapter` and published by its
`CsvBundlePublisher`. The publisher serializes changed tables into a complete
staged directory, swaps that directory into place with a durable journal, and
recovers interrupted swaps when the output is opened again. This keeps all CSV
tables on the same generation after a failed match write or process restart.

Direct `DATABASE`/`SQL` output is idempotent for matches: it checks the
canonical UUID against `matches.canonical_match_id` before inserting a match.
The fully qualified source path remains in `matches.file_name` for provenance.
`SQL_FILE` and `CSV` outputs are file-generation workflows and do not query an
existing database.

Human verification checklist:

- [ ] Parse the same raw file twice and confirm source UUID, canonical UUID,
  and digest are unchanged.
- [ ] Move the file under a different parent directory and confirm identities
  remain unchanged.
- [ ] Correct the bytes without changing the provider basename stem and confirm
  only the raw digest changes.
- [ ] Import the same canonical envelope twice and confirm one `matches` row
  and one source-reference row exist.

To ouput the data in SQL format:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory /path/to/data --data-directory cricsheet --player-registry people.csv --database mariadb -o sql/update.sql"
```

To ouput the data in CSV format:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory /path/to/data --data-directory cricsheet --player-registry people.csv -ot csv -cd csv"
```

A nightly run might look like this:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--base-directory /path/to/data --data-directory cricsheet --player-registry people.csv --nightly"
```

Every normalized JSON supplies the competition from `info.event.name` and the
warehouse match type from `info.matchType`; missing event names remain empty in
the warehouse. The existing database, SQL-file, and CSV output options are
unchanged.

## Produce CSV output

Run the parser with `CSV` output and a destination directory:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType CSV --base-directory $CRICSHEET_ROOT --data-directory cricsheet --player-registry people.csv --csvDir $CSV_DIR"
```

The adapter clears `[base]/$CSV_DIR` before writing. It emits one file per warehouse
table, with headers matching the generated warehouse schema, including files such
as `dim_person.csv`, `dim_match.csv`, `fact_delivery.csv`,
`bridge_delivery_wicket.csv`, and `bridge_delivery_fielder.csv`. Nullable
values are written as MariaDB's `\N`
marker.

The `dim_match.csv` file includes the source JSON filename in its `file_name`
column. The `fact_match.csv` file contains only match-level measures and keys.

## Load SQL output into MariaDB

If you load the SQL into the database it will create or replace the generated
warehouse rows according to the selected SQL dialect.

```bash
cd [SQL Directory]
mysql -u root -p acs_ball_by_ball < update.sql
```

## Load CSV output into MariaDB

The database must have the warehouse tables before loading CSV data. The loader
script loads files in foreign-key dependency order and skips optional files that
were not generated because the source data contained no rows for that table.
Its configuration is kept outside the applications project in
`/Users/kevinjones/Dropbox/projects/cricket/KSBallByBall/scripts/.env`.

Copy the example configuration, set the database credentials and CSV location,
then run the loader:

```bash
cp /Users/kevinjones/Dropbox/projects/cricket/KSBallByBall/scripts/.env.example \
  /Users/kevinjones/Dropbox/projects/cricket/KSBallByBall/scripts/.env
/Users/kevinjones/Dropbox/projects/cricket/KSBallByBall/scripts/load-warehouse-csv-mariadb.sh
```

`CSV_DIR` may be an absolute path or a path relative to the repository root
(`/Users/kevinjones/Dropbox/projects/cricket/KSBallByBall`). The script stops
with an error when that directory is missing, reports every file it skips or
loads, and returns a non-zero status if any load fails. It uses
`--local-infile=1`; the MariaDB server may also need `local_infile=ON`. The CSV
adapter allocates warehouse keys in the files, so load CSV output into an empty
warehouse (or coordinate key and duplicate handling explicitly before loading it
into an existing warehouse).

## Load CSV output into PostgreSQL

PostgreSQL can import the generated CSV files with `\copy`. This is a client-side
operation, so the files are read from the machine running `psql`. The
`cricsheet` schema must already have been migrated, and the tables must be
loaded in foreign-key dependency order:

```bash
PG_HOST=localhost
PG_PORT=5432
PG_NAME=cricsheet
PG_USER=cricsheet

for table in \
  dim_date \
  dim_team \
  dim_person \
  dim_ground \
  dim_match \
  dim_innings \
  dim_wicket \
  fact_match \
  fact_delivery \
  bridge_match_person \
  bridge_delivery_wicket \
  bridge_delivery_fielder
do
  file="$CRICSHEET_ROOT/$CSV_DIR/$table.csv"
  if [ -f "$file" ]; then
    PGPASSWORD="$DB_PASSWORD" psql --host="$PG_HOST" --port="$PG_PORT" \
      --username="$PG_USER" --dbname="$PG_NAME" <<SQL
SET search_path TO cricsheet;
\copy $table FROM '$file' WITH (FORMAT csv, HEADER true, NULL '\N')
SQL
  fi
done
```

The `NULL '\N'` option converts the CSV adapter's nullable-value marker into
PostgreSQL `NULL`. As with MariaDB, load the files into an empty warehouse or
coordinate key and duplicate handling explicitly before loading them into an
existing warehouse.

## Load CSV output into SQLite

SQLite's command-line importer can load the generated files when the data does
not contain nullable `\N` values:

```bash
SQLITE_DB=/path/to/cricsheet.db

for table in \
  dim_date \
  dim_team \
  dim_person \
  dim_ground \
  dim_match \
  dim_innings \
  dim_wicket \
  fact_match \
  fact_delivery \
  bridge_match_person \
  bridge_delivery_wicket \
  bridge_delivery_fielder
do
  file="$CRICSHEET_ROOT/$CSV_DIR/$table.csv"
  if [ -f "$file" ]; then
    sqlite3 "$SQLITE_DB" <<SQL
.mode csv
.import --skip 1 '$file' $table
SQL
  fi
done
```

The SQLite shell does not provide a direct equivalent of PostgreSQL's
`NULL '\N'` import option, so `\N` values are not converted to SQL `NULL` by
this command. For complete nullable-value handling, generate a SQLite
`SQL_FILE` script instead and load it with `sqlite3` as shown below.

## Alternative output modes

Write an executable MariaDB SQL script instead of CSV:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType SQL_FILE --base-directory $CRICSHEET_ROOT --data-directory cricsheet --player-registry people.csv --outputFile $SQL_FILE"
```

Load that script into MariaDB:

```bash
mariadb --host="$DB_HOST" --port="$DB_PORT" \
  --user="$DB_USER" --password="$DB_PASSWORD" "$DB_NAME" < "$CRICSHEET_ROOT/$SQL_FILE"
```

The generated SQL file is self-contained for the warehouse schema: it drops
the warehouse tables in foreign-key dependency order, recreates them from the
shared warehouse schema definition, and then loads the generated rows. This is
destructive to the existing warehouse data, so use it only when replacing the
entire warehouse is intended. The target database must already exist. MariaDB
output explicitly uses the `InnoDB` storage engine for every warehouse table so
foreign-key creation does not depend on the target server's default engine.

`bridge_delivery_fielder` uses `(delivery_key, wicket_key, person_key)` as its
composite primary key. The parser suppresses repeated fielder associations and
logs the source filename and key values; generated SQL and direct JDBC output
also use dialect-specific conflict handling as a final safeguard.

Generate a PostgreSQL script by selecting the PostgreSQL adapter:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType SQL_FILE --database postgres --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --outputFile $SQL_FILE"
```

The PostgreSQL script creates and selects the `cricsheet` schema, uses identity
columns, and uses PostgreSQL `ON CONFLICT` syntax. Load it after creating the
target database:

```bash
psql --host=localhost --port=5432 --username="$DB_USER" --dbname=cricsheet \
  --file="$CRICSHEET_ROOT/$SQL_FILE"
```

Generate and load a SQLite script:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType SQL_FILE --database sqlite --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --outputFile $SQL_FILE"
sqlite3 /path/to/cricsheet.db < "$CRICSHEET_ROOT/$SQL_FILE"
```

SQLite scripts enable foreign keys, use `INTEGER PRIMARY KEY AUTOINCREMENT`,
and start a SQLite transaction after the schema has been recreated.

The SQL-file adapters are implemented in the dialect-specific packages under
`bbb-update-database/src/main/kotlin/com/knowledgespike/ballbyball/parse/database/adapter`.
The direct JDBC adapters are selected automatically from the `jdbc:mariadb:`,
`jdbc:mysql:`, `jdbc:postgresql:`, or `jdbc:sqlite:` connection prefix.

Write directly to MariaDB through the JDBC adapter:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType DATABASE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --connectionString jdbc:mariadb://${DB_HOST}:${DB_PORT}/${DB_NAME} --userName $DB_USER --password $DB_PASSWORD"
```

Write directly to PostgreSQL through the JDBC adapter:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType DATABASE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --connectionString jdbc:postgresql://localhost:5432/cricsheet --userName $DB_USER --password $DB_PASSWORD"
```

Write directly to SQLite through the JDBC adapter. The SQLite JDBC URL should
point to the migrated database file; username and password are not required:

```bash
./gradlew :bbb-update-database:run --no-daemon \
  --args="--outputType DATABASE --baseDirectory $CRICSHEET_DIR --playerRegistry $PLAYER_REGISTRY --connectionString jdbc:sqlite:/path/to/cricsheet.db"
```

`SQL` is the default output type and behaves like direct database output when
no output file is supplied. Supplying `--outputFile` with `SQL` selects the SQL
script adapter instead. `SQL_FILE` makes that offline behavior explicit. The
SQL-file adapter resets and recreates the warehouse tables; direct `DATABASE`
output does not reset tables.

## Ktor applications

The repository now includes two runnable Ktor applications. The architecture,
configuration, endpoints, and browser-to-API flow are documented in
`docs/architecture/applications.md`.

Run the JOOQ-backed API against a migrated local database:

```bash
DB_JDBC_URL='jdbc:mariadb://localhost:3307/acs_ball_by_ball' \
DB_USER=acs_ball_by_ball \
DB_PASSWORD='acs_ball_by_ball-local-password' \
./gradlew :bbb-api:run --no-daemon
```

Run the static HTMX web application in a second shell:

```bash
API_BASE_URL=http://localhost:8081 \
./gradlew :bbb-web:run --no-daemon
```

The browser-facing application listens on port `8080` and the API listens on
port `8081` by default. Override `WEB_PORT`, `API_PORT`, and the database
variables rather than storing credentials in source files. Both Ktor modules
use `application.yaml` with `${ENV:default}` substitutions; do not add an
`application.conf` file. The API remains startable when the database is down
and exposes that state through `/health`; the web application maps API
connection, timeout, status, and malformed-response failures to `502 Bad
Gateway`. The complete database container setup remains in
`docs/setup/SETUP-DB.md`.

## Docker and Compose Development Environment

A full development environment is available using Docker Compose in `compose.yaml`. This brings up:

- **MariaDB 12.3.2** database configured with database `acs_ball_by_ball` and user `ballbyball`; set `DB_PASSWORD` and
  `DB_ROOT_PASSWORD` before starting Compose. It auto-initializes relational and dimensional warehouse schemas from
  `docker/mariadb/init/`.
- **bbb-api** listening on port `8081` connected to the database.
- **bbb-web** listening on port `8080` connected to `bbb-api`.
- **bbb-update-database** container runnable on demand with the `tools` profile.

On a fresh MariaDB volume, the init scripts create the complete post-migration-5
warehouse schema, including `canonical_match_id`, `public_match_id`, and
`match_source_reference`; Flyway is not required for that first initialization.

### Start the development stack (MariaDB, API, Web)

```bash
docker compose up -d --build
```

Access the web interface at `http://localhost:8080` and the API at `http://localhost:8081`.

### Run database updates with bbb-update-database in Docker

Place Cricsheet JSON/CSV files in `./data` (or set `DATA_DIR` in `.env`), then run:

```bash
docker compose run --rm update-database \
  --outputType DATABASE \
  --baseDirectory /data \
  --playerRegistry people.csv \
  --connectionString jdbc:mariadb://mariadb:3306/acs_ball_by_ball \
  --userName ballbyball \
  --password "$DB_PASSWORD"
```

### Stop the development environment

```bash
docker compose down
# Or to remove volumes and reset the database:
docker compose down -v
```

## GitHub Actions CI/CD

Workflows are located in `.github/workflows/`:

- **CI (`ci.yml`)**: Runs `./gradlew clean check` on all pull requests and pushes to `main`.
- **BBB-API (`build-bbb-api.yml`)**: Builds and checks `bbb-api`, creates install distribution artifacts, and on version
  tags (`v*.*.*`) builds and pushes multi-architecture (`linux/amd64`, `linux/arm64`) Docker images to Docker Hub.
- **BBB-Web (`build-bbb-web.yml`)**: Builds and checks `bbb-web`, creates install distribution artifacts, and pushes
  Docker images on version tags.
- **BBB-Update-Database (`build-bbb-update-database.yml`)**: Builds and checks `bbb-update-database`, creates
  distribution artifacts, and pushes Docker images on version tags.
- **Reusable Workflows & Actions**:
    - `reusable-gradle.yml` & `reusable-docker.yml`
    - Composite actions in `.github/workflows/actions/` (`setup-jdk`, `use-gradle`, `docker-push`)

## Updating this runbook

When adding a tool or command:

1. Add its normal invocation and a minimal example here.
2. Document required inputs, output files, and database prerequisites.
3. Update the parser option table when `Application.kt` changes.
4. Add or update verification commands when tests, migrations, or adapters
   change.
5. Record the completed documentation change in `docs/project_memory.md`.
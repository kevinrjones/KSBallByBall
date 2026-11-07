# Database architecture

## Authoritative schema

The authoritative match-data schema is normalized and has no production
`dim_*` or `fact_*` tables. It is versioned independently for MariaDB,
PostgreSQL, and SQLite under `bbb-update-database/migrations`; each dialect has
one complete migration, `1__initial_tables.sql`.

The schema contains:

| Table | Responsibility |
|---|---|
| `dates` | Calendar attributes keyed by `date_id`. |
| `teams` | Source team identity and display name. |
| `people` | Shared players and officials with source names and `ca_id`. |
| `grounds` | Source ground identity and display name. |
| `matches` | Match entity, metadata, teams, result, measures, and identities. |
| `match_source_reference` | Provider/source key and raw-content digest provenance. |
| `innings` | Ordered innings and batting/bowling team roles. |
| `deliveries` | Ordered ball data, runs, extras, powerplay, and wicket count. |
| `wickets` | Source wicket identity and kind. |
| `match_people` | Match/player/official role assignments. |
| `delivery_wickets` | Delivery-to-wicket association. |
| `delivery_fielders` | Wicket-specific fielder association. |

`matches.id` is the generated internal relational key. It is distinct from
`canonical_match_id`, the stable ingestion identity, and nullable unique
`public_match_id`, the API identity. `duration_days`, `margin`, and
`match_count` live on `matches`; delivery measures live directly on
`deliveries`. All dependent rows reference the internal keys of their parent
rows.

## Identity and provenance

The updater derives canonical identity from the canonical envelope and never
from a filename. Replaying an unchanged envelope is idempotent through unique
source, match, innings, delivery, role, wicket, and association keys. A source
reference with the same canonical match identity but a changed digest is
rejected so corrected source data cannot silently leave stale rows.

`match_people.role_code` represents `PLAYER`, `UMPIRE`, `TV_UMPIRE`,
`RESERVE_UMPIRE`, and `MATCH_REFEREE`. `match_source_reference` stores the
provider, provider record key, source record ID, and raw-content digest needed
for correction detection.

`IdentityCore` owns transactional identity maps, duplicate gates, public-match
ownership, source-CA allocation, key counters, snapshots, and rollback for all
write adapters. `JdbcIdentityStore` delegates those decisions, composes core
source-CA ownership with persisted match rows, and performs provenance and
storage-specific lookups; JDBC-generated keys remain a storage-specific hook.

`bbb-shared` owns JDBC pool lifecycle through `DatabaseConnection` and
`DataSourceManager`. Pool keys contain process-local HMAC fingerprints of the
JDBC URL and password rather than retaining those raw credential values;
`DataSourceManager.closeAll()` closes all pools and is registered as a JVM
shutdown hook. Individual `DbConnection.close()` calls return leases to the
pool.

## Loading paths

`bbb-update-database` writes the same relational contract through three output
paths:

1. `JdbcOutputAdapter` is a facade around `RelationalMatchSession`,
   `JdbcIdentityStore`, and `JdbcRowSink`; the facade retains match-scoped JDBC
   transaction and auto-commit handling.
2. `SqlScriptOutputAdapter` delegates domain writes to the same session and
   serializes relational rows as executable dialect-specific SQL.
3. `CsvOutputAdapter` delegates to the session and publishes parent-before-child
   files for bulk loading.

The loading order is `dates`, `teams`, `people`, `grounds`, `matches`, source
references, `innings`, `wickets`, `deliveries`, `match_people`,
`delivery_wickets`, and `delivery_fielders`. Duplicate match roles and
fielder associations are suppressed using their relational unique keys.
The session is the single domain decision-maker: stores resolve existing
identities and generated keys, while sinks persist its insert/update rows.
Canonical reuse backfills a missing `public_match_id`, updates `innings.declared`,
and reconciles source-CA ownership once through the shared allocator.

`RelationalSchemaSql.kt` is the schema generator used for output scripts. It
destructively rebuilds match-data tables but creates `team_name_aliases` and its
target index only when absent, preserving operator-owned alias mappings across
script applies. MariaDB output defaults to `acs_ball_by_ball`, with an optional
validated database-name parameter. Adapter parity creates SQLite from this
generated DDL and applies generated SQLite scripts to a fresh database before
reading match facts; MariaDB and PostgreSQL generated DDL are also applied in
Testcontainers, including alias uniqueness and alias-preserving reapplication.
The versioned dialect migrations remain authoritative for deployed databases
and define the table contract with dialect-specific identity, foreign-key, and
conflict syntax. Each current dialect's `1__initial_tables.sql` creates
`team_name_aliases` with `IF NOT EXISTS` and does not drop it, preserving
operator mappings if that table-creation statement is applied again. The initial
migration also enforces uniqueness for the full
`(source_system, source_team_name, stats_team_id)` triple while allowing one
source name to target multiple stats teams.

## Match transaction phases

Every adapter requires `beginMatch()` before `commit()`. The shared
`MatchTransaction` runs every participant's memory-only `prepareCommit()`, then
the `durableCommit()` hooks, then participant `commit()` finalizers. JDBC calls
`Connection.commit()` and CSV publishes its bundle only in the durable phase;
identity state is finalized afterward. Preparation failures roll back all
participants. Once a durable hook starts, failure handling is best-effort
finalization rather than a claim that committed database or published file
effects can be undone. Closing an active match transaction rolls it back.
The SQL-script adapter's match-scoped durable hook is intentionally a no-op:
rows are staged in the script. After a successful import, the application
calls `complete()`, which appends `COMMIT;` and journal-publishes the completed
script. `close()` does not publish: it rolls back an active match if needed and
abandons the staged script, deleting it when no publication journal is pending.

The initial migration creates the unique alias-triple index. Before restoring
operator-owned aliases during cutover, remove exact duplicate triples from the
export; do not collapse distinct `stats_team_id` targets for one source name.

## Cutover procedure

Existing databases are rebuild targets, not in-place translation targets:

1. Back up the existing database and retain the backup until verification is
   complete.
2. Export `team_name_aliases` outside the database being rebuilt, preserving the
   explicit columns `source_system`, `source_team_name`, and `stats_team_id`.
   Record and verify the exported row count; do not rely on a temporary table in
   the database that will be rebuilt.
3. Create a fresh target database and apply that dialect's
   `1__initial_tables.sql` migration. Do not manually replay the destructive
   baseline against the existing database.
4. Restore the exported alias rows into `team_name_aliases` before replaying
   matches. The unique key is the full `(source_system, source_team_name,
   stats_team_id)` triple: review/deduplicate exact duplicate triples in the
   export rather than discarding distinct targets for one source name.
5. Verify the restored row count and alias targets, then replay every canonical
   envelope through the updater.
6. Verify counts, identity/provenance rejection, and data integrity before putting
   the rebuilt database into use.

The migrations intentionally contain no warehouse-row copy logic. Numeric
internal IDs may change during replay; canonical and public identities are the
stable operational references. Operator aliases are integration data rather
than canonical-envelope data, so they must be exported and restored separately.
# Close remaining relational output P1 blockers

### Title

Unify JDBC writes and preserve operator aliases

### Date/time completed

2026-10-09 21:34

### What was shipped

- Added a shared SQLite-JDBC/session parity suite for re-import, source-CA
  reconciliation and conflicts, innings declaration reuse, public-ID backfill
  and collisions, duplicate bridge rows, and rollback.
- Added `MatchIdentityStore` with in-memory and JDBC implementations;
  `RelationalMatchSession` now owns domain decisions for JDBC, SQL-script, and
  CSV writes, and `JdbcOutputAdapter` is a transaction facade over
  `JdbcRowSink`.
- Added explicit relational update rows and generated-key returns so all sinks
  persist session decisions while JDBC retains database identity values.
- Made generated SQL-script bootstrap preserve `team_name_aliases`; documented
  durable operator ownership and the immutable destructive migration history.
- Added ADR 0043, updated the database/application architecture and FIXME, and
  refreshed the README design notes.

### Key decisions

- Keep one domain write brain in `RelationalMatchSession`; stores provide
  identity/ownership/key information and sinks persist rows.
- Use live-database reuse semantics across adapters: backfill null public IDs,
  update `innings.declared`, and apply source-CA reconciliation once.
- Keep `JdbcOutputAdapter` responsible for JDBC transaction and auto-commit
  lifecycle; dialect subclasses provide duplicate clauses and connection setup.
- Treat alias mappings as durable operator-owned configuration. Do not rewrite
  historical migrations; never manually replay the destructive initial
  migration over populated operator mappings.

### Gotchas

- CSV updates can target rows published by an earlier match transaction; the
  adapter retains match/innings projections and republishes the innings table
  for those updates.
- Update rows use explicit null predicates for public-ID and source-CA
  backfills so concurrent values are not overwritten.
- Kover reporting was attempted, but `:bbb-update-database:koverHtmlReport` is
  not configured as a task for this module; no coverage percentage was
  generated.

### Test coverage areas

- `AdapterParityTest`: SQLite JDBC and recording-session parity across the P1
  behaviors, including pending source-CA ownership and rollback.
- `RelationalMatchSessionTest`, `SourceCaIdAllocatorTest`, SQL-script and CSV
  output tests: explicit update rows, generated identities, transactional CSV
  updates, and source-CA registration/reconciliation.
- `RelationalSchemaSqlTest`: alias row preservation and uniqueness after
  repeated generated SQLite schema application.
- `./gradlew check --no-daemon` passed across the repository.
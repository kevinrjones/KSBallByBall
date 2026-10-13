# Relational output remediation

**Date/time completed**: 2026-10-09 18:31

## What was shipped

- Added `RelationalMatchSession` and a `RelationalSink` row contract; SQL script and CSV adapters delegate match-domain writes to the shared session.
- Replaced the source-CA state with `SourceCaIdAllocator` and typed `Reject`, `NoOp`, `RegisterNow`, and `PendingUpdate` outcomes; CSV patches `source_ca_id` by column name.
- Unified SQL file replacement and CSV bundle publication/recovery through `JournaledMatchReplace`; JDBC uses shared SourceCaId decisions and explicit identity-free innings insert values.
- Added additive alias uniqueness migrations for MySQL, PostgreSQL, and SQLite on `(source_system, source_team_name, stats_team_id)` and aligned generated schema DDL.
- Moved stats exclusions and women's competition/team naming exceptions into classpath policy resources with fail-fast loading.
- Reused one stats repository in `JdbcMatchLinker`, removed the combined candidate API, and added the sealed `ParseLinkError` family.
- Corrected ADR 0042, README policy references, and the FIXME completion status.

## Key decisions

- Use composition: one stateful relational match session with SQL/CSV serialization sinks; keep JDBC's direct database I/O adapter-specific.
- Preserve sealed migration `1__initial_tables.sql`; add the unique index in migration 2 for each dialect.
- Keep operational exception lists as versioned, offline-friendly classpath text resources.

## Gotchas

- Databases containing duplicate alias triples must deduplicate them before applying migration 2.
- Policy resources are mandatory and non-empty; a missing or empty resource fails during policy initialization.
- The historical `docs/project_memory.md` log remains intact below its index; archive old entries incrementally rather than appending long task records there.

## Test coverage areas

- Relational session and SourceCaId allocator outcomes, transaction commit/rollback, SQL/CSV adapter behavior, journal recovery and publish failures, JDBC value ordering.
- Alias uniqueness for all dialects and matching greenfield DDL.
- Policy resource failure modes, unchanged parser exclusions, team-name exceptions, linker candidates/errors.
- `./gradlew :bbb-update-database:check :bbb-parse-cricsheet:check :bbb-cli-shared:check` passed.
- MySQL/PostgreSQL migration-container tests passed; the MariaDB repository integration test was skipped because Docker was unavailable.
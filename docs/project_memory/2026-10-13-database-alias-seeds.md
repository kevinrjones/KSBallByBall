# Seed team aliases during database rebuilds

### Title

Seed team aliases during database rebuilds

### Date/time completed

2026-10-13 09:57

### What was shipped

- Generated rebuild SQL now inserts the 48 baseline Cricsheet team aliases
  already present in each dialect's initial migration.
- Alias inserts use dialect-specific conflict handling and preserve
  operator-managed rows because the alias table is not dropped.
- Updated migration-focused assertions to include seeded rows while retaining
  checks for distinct alias targets and duplicate-triple rejection.
- Enabled the existing Kover plugin in `bbb-update-database` to report coverage
  for the schema writer and updater tests.
- Updated README and architecture documentation to describe the seeding and
  preservation behavior.

### Key decisions

- Kept the migration alias rows as the parity reference and used the shared
  `AliasSourceSystem.CRICSHEET` identifier in generated SQL.
- Used `INSERT IGNORE` for MariaDB, `ON CONFLICT DO NOTHING` for PostgreSQL, and
  `INSERT OR IGNORE` for SQLite so schema scripts can be reapplied safely.

### Gotchas

- Replaying migration alias DDL is safe, but replaying its plain data inserts is
  not; migration contract tests reapply only the alias DDL statements.
- The updater module is below 100% overall coverage (92.3% lines, 70% branches),
  but `RelationalSchemaSql` is at 100% for class, method, branch, line, and
  instruction coverage.

### Test coverage areas

- Exact generated-schema parity with all alias rows in the MariaDB, PostgreSQL,
  and SQLite migrations.
- Reapplication with baseline rows and an additional operator alias preserved.
- MariaDB and PostgreSQL schema application in Testcontainers and SQLite
  migration/schema contract comparison.
- Kover coverage report for `RelationalSchemaSql`: 100% across all reported
  metrics.
- `./gradlew :bbb-update-database:test --no-daemon` passed.
- `./gradlew clean check --no-daemon` passed across all modules.
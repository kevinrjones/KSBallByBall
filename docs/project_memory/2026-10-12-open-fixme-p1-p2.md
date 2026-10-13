# Close open FIXME P1/P2

### Title

Success-only SQL publish, safe CSV recovery, and schema/provenance parity

### Date/time completed

2026-10-12 14:45

### What was shipped

- SQL script adapters publish from `complete()` only after successful `runImport`; incomplete output is abandoned and the previous final script remains untouched.
- CSV journal recovery now applies the normal empty-or-`--force` gate; provenance checks fail closed for missing or empty existing references.
- Flyway alias migrations preserve `team_name_aliases`, use dialect-safe re-apply guards, and are locked against generated-schema drift by parity tests.
- SQL script row emission now requires an active match transaction; `docs/FIXME.md`, `README.md`, this project-memory entry, and ADR 0049 record the completed behavior.

### Key decisions

- `OutputAdapter.complete()` is a default no-op; SQL scripts publish synchronously during completion, while close abandons uncompleted staged output.
- CSV recovery does not attempt identity hydration: recovered non-empty output is rejected unless the operator requests `--force`.
- The schema remains dual-source, with semantic parity enforced in tests rather than a new canonical schema generator.
- MySQL uses an `information_schema.statistics` guard and prepared DDL for the alias index because MySQL 8.4 does not accept `CREATE INDEX IF NOT EXISTS`.

### Gotchas

- The initial migration scripts are rebuild-oriented and are not generally idempotent; re-application tests deliberately repeat only the alias DDL, whose preservation is the contract under test.
- `:bbb-update-database:koverHtmlReport` is not configured, so a module coverage percentage could not be generated.

### Test coverage areas

- SQL success-only publication, failed-import abandonment, active-transaction completion rejection, and transaction-gated emits at adapter and application boundaries.
- Recovered CSV directory rejection/preservation without force and clean replacement with force.
- JDBC and in-memory fail-closed provenance, including empty and missing references.
- Migration alias non-drop policy, dialect-safe alias DDL re-application, unique triple contract, and generated/migrated SQLite schema parity.
- `./gradlew :bbb-update-database:check --no-daemon` passed.
- `./gradlew clean check --no-daemon` passed across the workspace.
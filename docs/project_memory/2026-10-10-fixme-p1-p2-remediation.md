# FIXME P1/P2 remediation

### Title

Close the P1 blockers and deliver the P2 remediation plan

### Date/time completed

2026-10-10 15:30 BST

### What was shipped

- Female match linking now uses raw Cricsheet team names for aliases and
  policy-shaped names for stats lookup; generated SQL scripts reject `mysql`
  and remain MariaDB/PostgreSQL/SQLite-only.
- JDBC, SQL-script, and CSV adapters share `SessionBackedOutputAdapter` and
  `MatchTransaction`; `writeAllPeople` is transactional and active close rolls
  back without inventing a JDBC commit.
- CSV sink snapshots, identity uniqueness gates, allocator lifecycle, and pure
  database calculations were consolidated; non-empty CSV directories require
  explicit `--force` to replace.
- Added SQLite schema-vs-migration metadata checks, migration `2__` duplicate
  triple preflight, alias cutover instructions, and pool key/shutdown handling.
- `MatchLinker` returns sealed `StatsLinkResult` values, uses bounded
  set-based alias queries, and receives a validated `ParseCricsheetInput` layout.

### Key decisions

- Reject `mysql` only for generated SQL scripts; retain MySQL Flyway migrations
  and direct `jdbc:mysql:` support as separate paths.
- Keep `OutputAdapter` as `Database`'s port while concrete relational adapters
  inherit session forwarding and compose transaction participants.
- Alias corrections are keyed by raw source names; expected link outcomes are
  values, and parser policy skips are owned by the linker.
- Add the alias duplicate preflight to migration `2__` after confirming that
  migration has not been applied in any environment.

### Gotchas

- The generated SQL-script path is not executed as part of the parity suite;
  source-CA facts are verified from recording-sink, SQLite, and CSV outputs, and
  the remaining script-parity limitation is explicit in `docs/FIXME.md`.
- Existing databases are rebuild targets. Export `team_name_aliases` outside
  the database before applying the current migration sequence, then restore and
  verify mappings before replay.
- The MariaDB stats-repository integration test remains skipped by its current
  test configuration.

### Test coverage areas

- Female raw-source alias regression, sealed linking outcomes, policy skips,
  set-based alias predicates, and validated parser inputs.
- JDBC commit/rollback/close, people-bootstrap rollback, duplicate identity
  gates, CSV force/refuse and close behavior, and adapter parity including CSV.
- Generated SQLite schema contract, MySQL/PostgreSQL migration preflight,
  shared data-source keying and lifecycle.
- `./gradlew clean check --no-daemon` passed repository-wide (49 tasks ran, 4
  were up to date); `git diff --check` reported no whitespace errors.
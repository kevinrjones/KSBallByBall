# Close the P2 backlog

### Title

Close the full P2 backlog with durable phases, generated-schema parity, and a shared identity core

### Date/time completed

2026-10-11 07:41

### What was shipped

- `MatchTransaction` now requires an active transaction and coordinates
  memory-only preparation, participant durable commits, and finalization; JDBC
  commit and CSV publication occur only in the durable phase.
- Adapter parity applies generated SQLite schema and generated SQL scripts,
  reads match facts, and compares session, JDBC, CSV, and script results.
- `IdentityCore` owns transactional identity state; JDBC performs cache-miss
  queries, and adapters receive sessions through construction.
- Removed dead write APIs, the match-person null-ID protocol, and the
  `replaceFile` wrapper; unified journal phases and CSV staged-source ownership.
- Kept sealed link results as the domain API, allowed empty exclusion policies,
  and introduced validated `SourceCaId` / `StatsTeamId` value classes.
- Parameterized and validated the MariaDB database name, added a non-Docker
  SQL/bind shape test, and closed all 18 P2 items in `docs/FIXME.md`.

### Key decisions

- Commit is an explicit match-transaction operation, never an inactive flush.
- A failure before durable work rolls back all participants; after durable work
  begins, the coordinator best-effort finalizes and does not claim to undo
  external effects.
- All adapters share one transactional identity core; JDBC contributes lookup
  and generated-key hooks only.
- `RelationalSchemaSql` is the generated-schema truth for parity; the default
  MariaDB database remains `acs_ball_by_ball` but can be safely overridden.

### Gotchas

- Post-durable failures are inherently non-atomic; CSV journals preserve staged
  data for recovery, but a successful JDBC commit cannot be reversed.
- MariaDB and PostgreSQL DDL receive deterministic table/index and dialect-form
  smoke coverage; actual apply/metadata execution is required only for SQLite
  in the default test profile. The MariaDB stats integration remains optional.
- P3 findings remain deferred; the P1 regression guard table remains closed.

### Test coverage areas

- Transaction ordering, inactive commit, pre-durable rollback, durable failure,
  best-effort finalization, and suppressed cleanup failures.
- Full generated-schema adapter parity, applied SQLite-script readback, source-CA
  ownership, identity cache misses, and rollback snapshots.
- Journal recovery, legacy journal phase compatibility, staged CSV source
  retention, SQL/bind positions, empty/missing policy resources, and tiny-type
  validation.
- `:bbb-shared:check`, `:bbb-parse-cricsheet:check`,
  `:bbb-update-database:check`, and `:bbb-cli-shared:check` passed; the MariaDB
  integration test was skipped by its optional profile.
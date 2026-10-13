# Resolve the remaining FIXME P2s

### Title

Complete alias diagnostics, transaction rollback, and Cricsheet mapping fixes

### Date/time completed

2026-10-12 15:53

### What was shipped

- Link failure criteria now identify source alias keys and include policy-shaped
  stats names where they differ; female wrong-key and correct-key regressions
  cover miss and link outcomes.
- Durable-commit failure now rolls all match transaction participants back in
  reverse order, suppresses rollback failures, and does not finalize identity.
- JDBC failure recovery is covered through a proxy connection, proving rollback
  happens before autocommit restoration and outer rollback is a no-op.
- Replaced Cricsheet JSON key normalization with an explicit mapper from source
  DTOs to warehouse DTOs; preserved the source decode, digest, and envelope path.
- Locked P2-1 through P2-3 closed in `docs/FIXME.md` and documented the new
  behavior in `README.md`.

### Key decisions

- Kept the alias change diagnostic-only; no write-time warning or rejection was
  added for policy-shaped alias rows.
- Roll back every participant on a durable failure; retain best-effort
  finalization only after all durable commits succeed.
- Retained both DTO graphs because their wire shapes differ, and used an
  explicit field-by-field mapper rather than changing shared contracts.

### Gotchas

- A CSV durable publish that fails after writing files cannot be fully undone by
  the current in-memory rollback; this remains the accepted sink limitation.
- Kover is applied to `bbb-shared` only, so this task's touched modules do not
  expose a coverage report or percentage.

### Test coverage areas

- Source/stats alias diagnostics, right-key/wrong-key female aliases, and parser
  miss/ambiguity messages.
- Transaction phase ordering, durable failure rollback-all behavior, suppressed
  rollback failures, inactive transaction state, JDBC rollback/autocommit order,
  and post-durable best-effort finalization.
- Cricsheet warehouse match-type prefixes, info/innings/delivery mapping,
  replacements, unknown input keys, and unsupported match-type rejection.
- `:bbb-parse-cricsheet:test`, `:bbb-update-database:check`,
  `:bbb-parse-cricsheet:check`, and `./gradlew clean check --no-daemon` passed.
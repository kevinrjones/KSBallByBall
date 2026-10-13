# Close documentation and alias source-system P2s

### Title

Correct SQL-script lifecycle docs and unify Cricsheet alias keys

### Date/time completed

2026-10-12 15:23

### What was shipped

- Updated the database/application architecture guides and ADR 0006 to describe
  success-only SQL-script publication and the current alias-preserving baseline
  migrations accurately.
- Added `AliasSourceSystem.CRICSHEET`, backed by the Cricsheet provider
  namespace UUID, and made it the `JdbcTeamNameAliasRepository` default.
- Unified Cricsheet alias seed values across parser, migration, schema, and SQL
  script tests, and added a wrong-key miss/canonical-key hit regression.
- Closed P2-1 and P2-2 in `docs/FIXME.md`; kept all eight residual P3s deferred.

### Key decisions

- Keep `f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b` as the canonical alias
  `source_system`; `ProviderId("cricsheet")` remains unchanged for provider
  provenance.
- Do not add a migration to rewrite existing alias rows keyed by the literal
  `cricsheet`; such rows remain invisible until an operator corrects them.
- Do not change SQL-script runtime behavior or checked-in migration SQL.

### Gotchas

- `team_name_aliases.source_system` is the provider namespace UUID, not the
  string used in `match_source_reference.provider`.
- A wrong-key alias row silently misses; the regression now locks both the miss
  and canonical-key lookup behavior.

### Test coverage areas

- Wrong `source_system` silent miss and canonical UUID alias lookup.
- Schema/migration uniqueness seeds and generated-schema alias preservation.
- `./gradlew :bbb-shared:test :bbb-parse-cricsheet:test :bbb-update-database:test --no-daemon` passed.
- `./gradlew clean check --no-daemon` passed across the workspace.
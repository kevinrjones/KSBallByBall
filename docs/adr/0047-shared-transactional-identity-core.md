# ADR 0047: Shared transactional identity core

## Status

Accepted

## Context

`InMemoryIdentityStore` and `JdbcIdentityStore` duplicated identity maps,
ownership sets, source-CA allocation, snapshots, commit, and rollback. Their
public-match ownership models differed, making adapter behavior vulnerable to
drift. `SessionBackedOutputAdapter` also required subclasses to initialize its
session later through a `lateinit` method.

## Decision

- Keep one `IdentityCore` as the owner of transactional maps, sets, key
  allocation, public-match ownership, source-CA state, snapshots, rollback, and
  finalization.
- Represent public-match ownership uniformly as `PublicMatchId -> match key`.
- Make `JdbcIdentityStore` delegate identity behavior to `IdentityCore` and
  perform JDBC queries only on cache misses; database-generated key allocation
  remains a JDBC-specific hook.
- Inject the completed `RelationalMatchSession` into
  `SessionBackedOutputAdapter` at construction. JDBC, CSV, and SQL-script
  adapters construct their session and sink wiring once.
- Keep persisted IDs scalar at JDBC/SQL boundaries while validating source CA
  and stats-team identities with tiny types at parse and write edges.

## Consequences

- Cache, source-CA, duplicate-role, snapshot, and rollback decisions have one
  implementation across output adapters.
- JDBC-specific code is limited to lookup-on-miss queries, generated keys, and
  source/provenance SQL.
- Transactional identity fields, counters, snapshot lifecycle, and rollback now
  live directly in `IdentityCore`, avoiding a pass-through state owner.

## Validation

- Relational session and adapter parity tests cover cache misses, public-match
  ownership, source-CA reconciliation, snapshot rollback, and generated IDs.
- `:bbb-update-database:check` validates JDBC, SQL-script, CSV, and SQLite write
  paths.
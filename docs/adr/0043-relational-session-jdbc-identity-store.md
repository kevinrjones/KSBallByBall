# ADR 0043: Single relational write pipeline with JDBC identity store

## Status

Accepted

## Context

`RelationalMatchSession` previously owned domain write decisions for the SQL-script
and CSV adapters, while `JdbcOutputAdapter` independently implemented match
identity, source-CA reconciliation, row writes, and generated-key handling. That
split allowed public-ID reuse, innings declaration, and source-CA behavior to
diverge under the same `OutputAdapter` contract.

## Decision

- `RelationalMatchSession` is the single domain write implementation for JDBC,
  SQL-script, and CSV output.
- The session depends on `MatchIdentityStore` for identity lookups, ownership,
  key allocation, and transactional identity state. `InMemoryIdentityStore`
  serves the file adapters; `JdbcIdentityStore` queries live rows and requests
  generated keys for JDBC identity columns.
- The session emits explicit `RelationalRow` insert/update operations through a
  `RelationalSink`. `JdbcRowSink` binds row values, returns generated IDs, and
  applies dialect-provided duplicate clauses; SQL and CSV sinks serialize the
  same session decisions in their respective formats.
- `JdbcOutputAdapter` remains responsible for JDBC transaction and
  auto-commit lifecycle only. Dialect subclasses provide duplicate SQL and
  connection initialization.
- Canonical reuse semantics are shared: backfill a missing
  `public_match_id`, update `innings.declared`, and decide/apply source-CA
  reconciliation once in the session. The database constraint remains the
  final guard against concurrent uniqueness conflicts.

## Consequences

- Match identity, public-ID collision checks, source-CA ownership, innings
  updates, and duplicate suppression are decided once and projected by sinks.
- Generated-key differences are isolated to the store/sink protocol; file
  adapters retain deterministic in-memory keys.
- The shared adapter parity suite protects durable outcomes across SQLite JDBC
  and the session with a recording sink.
- `JdbcOutputAdapter` transaction ownership is intentionally retained. The
  later shared transaction-facade decision is recorded in ADR 0044.

## Validation

- `AdapterParityTest` covers insert/re-import, late and conflicting source-CA
  assignment, pending ownership conflict, innings `declared`, public-ID
  backfill/collision, duplicate bridge rows, and rollback.
- `:bbb-update-database:check` validates the implementation and existing JDBC,
  SQL-script, CSV, and migration behavior.
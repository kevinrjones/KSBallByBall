# ADR 0044: Session-backed adapters and match transactions

## Status

Accepted (transaction phase ordering and failure policy superseded by
[ADR 0046](0046-durable-match-commit-phases.md); SQL-script publication
lifecycle superseded by [ADR 0049](0049-success-only-sql-script-publishing.md))

## Context

`OutputAdapter` is the write port used by `Database`, but JDBC, SQL-script, and CSV
adapters repeated the same forwarding methods to `RelationalMatchSession`. Each
adapter also coordinated session identity snapshots with its own sink and
transaction state. JDBC passed `Connection.commit()` through the source CA-ID
allocator, `close()` could leave a JDBC transaction active, and
`Database.writeAllPeople` did not share the match transaction envelope.

## Decision

- Keep `OutputAdapter` as the port consumed by `Database`; implement its
  session-backed domain write methods once in `SessionBackedOutputAdapter`.
- Let JDBC, SQL-script, and CSV adapters compose a `MatchTransaction` from the
  relational session participant and sink-specific transaction participants.
  The facade owns non-reentrant begin/commit/rollback and rolls back all started
  participants when a transaction operation fails.
- Keep identity-state finishing in `SourceCaIdAllocator` and
  `RelationalMatchSession`. JDBC owns the durable `Connection.commit()` call at
  the adapter boundary, followed by auto-commit restoration.
- `close()` rolls back an active match transaction and never commits it. CSV
  publishes through explicit commit; pending rows are not published on close.
  At the time of this ADR, SQL-script close finalized the staged script after
  rolling back an active match snapshot; this lifecycle is superseded by ADR 0049.
- Wrap `Database.writeAllPeople` in the same begin/commit/rollback envelope as
  `Database.writeMatch`.
- Put match-person and delivery-fielder uniqueness gates in
  `MatchIdentityStore`, so every sink receives only accepted rows and snapshots
  restore these keys on rollback.
- Keep CSV row buffers and update caches in a dedicated
  `CsvOutputState`; they are sink state, separate from relational identity
  snapshots. Remove the unused `updatedRows` publication parameter.
- Keep pure player-name, victory/margin, and powerplay calculations in
  `MatchDataCalculations`, outside the database orchestrator and relational
  session.

## Consequences

- Domain forwarding and session identity snapshots have one implementation;
  concrete adapters retain only sink wiring and lifecycle behavior.
- JDBC commit ownership is explicit and cannot be invoked through
  `SourceCaIdAllocator`.
- Failed people bootstrap or match writes roll back session and sink state.
- Adapter close no longer invents a JDBC commit; explicit successful commits
  remain observable at the connection boundary.
- CSV non-transactional writes require explicit commit before close; active
  match work is rolled back and never implicitly published.
- Duplicate suppression is independent of sink SQL syntax and participates in
  transactional identity snapshots.
- `Database` and `RelationalMatchSession` share pure calculations without the
  session importing helpers from the orchestrator.

## Validation

- `:bbb-update-database:test` covers JDBC commit count and active-close rollback,
  failed people bootstrap rollback, adapter parity, and SQL/CSV behavior.
- `:bbb-update-database:check` passes.
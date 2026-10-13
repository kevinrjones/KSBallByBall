# ADR 0046: Durable match commit phases

## Status

Accepted

## Context

`MatchTransaction` previously mixed memory preparation with adapter-owned durable
work: CSV published in `prepareCommit`, while JDBC passed `Connection.commit()`
as a callback before participants finalized identity state. `commit()` also
allowed inactive calls that behaved like an undocumented flush. Durable file or
database effects cannot always be undone after a failure.

## Decision

- Require an active match transaction for every `commit()`; inactive commit
  throws and is not an alternate flush API.
- Use one participant-owned sequence: all `prepareCommit()` hooks, all
  `durableCommit()` hooks, then all participant `commit()` finalizers.
- Keep `prepareCommit()` memory-only. CSV publication and JDBC
  `Connection.commit()` run only in `durableCommit()`; participant `commit()`
  finalizes identity or adapter state and restores JDBC auto-commit.
- If preparation fails, roll back every participant in reverse order, attach
  rollback failures as suppressed exceptions, and clear active state.
- Once durable work begins, do not claim full rollback. On a durable-hook or
  finalizer failure, best-effort finalize every participant, attach cleanup
  failures to the primary failure, clear active state, and rethrow the primary
  failure. Adapters remain responsible for their own recoverable file-journal
  cleanup.
- Closing an active adapter continues to roll back; only explicit commit
  publishes CSV or commits JDBC.

## Consequences

- All sinks observe the same prepare → durable → finalize ordering.
- Commit failure after durable work is explicitly non-atomic; filesystem
  journals provide recovery where possible, while a successful JDBC commit
  cannot be reversed.
- Callers must use `beginMatch()` before `commit()`; production
  `Database.withMatchTransaction` already follows this contract.

## Validation

- `MatchTransactionTest` covers ordering, inactive commit, preparation rollback,
  durable failure, suppressed cleanup failures, and post-durable finalizer
  failure.
- CSV and JDBC adapter tests cover publication and connection commit behavior.
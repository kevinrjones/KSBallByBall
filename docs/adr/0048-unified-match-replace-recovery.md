# ADR 0048: Unified match replacement recovery

## Status

Accepted

## Context

`JournaledMatchReplace` supports both single-file SQL-script output and
directory-based CSV bundles. Their recovery implementations assigned different
meanings to `BACKUP_TAKEN`: a FILE target was rolled back, while a present
DIRECTORY target was kept. A crash after the content move but before the
`PUBLISHED` journal write could therefore commit one output kind and roll back
the other.

## Decision

- Keep the journal-free atomic-move fast path for FILE replacement.
- Use one non-atomic sequence for FILE fallback and DIRECTORY publication:
  `STAGED`, optional backup move, `BACKUP_TAKEN`, source move, `PUBLISHED`, then
  backup and journal cleanup.
- Treat only `PUBLISHED` as committed. Recovery at that phase keeps the target
  and removes source/backup leftovers; every earlier phase restores an available
  backup before cleaning leftovers.
- When an earlier-phase journal has no backup, preserve a possibly moved target
  during restart recovery because the record cannot prove whether publication
  happened. Synchronous live rollback may remove a newly created target when no
  prior target existed.
- A replacement's preflight recovery preserves its supplied source while
  restoring prior state, then continues the requested replacement.

## Consequences

- `BACKUP_TAKEN` has one meaning for SQL scripts and CSV bundles.
- Recovery policy is phase-based; FILE and DIRECTORY differences are limited to
  their move and cleanup operations.
- A target is never considered committed solely because it exists; the durable
  `PUBLISHED` phase remains the commit marker.

## Validation

- Journal recovery tests cover `BACKUP_TAKEN` with present and absent targets
  for FILE and DIRECTORY, `STAGED`, `PUBLISHED`, and legacy `SOURCE_MOVED`.
- Existing atomic FILE, non-atomic fallback, CSV publication, and durable commit
  tests remain green.
# ADR 0049: Success-only SQL script publishing

## Status

Accepted

## Context

`Application` owns SQL-script adapters with `use {}`. Before this decision, the
adapter's `close()` appended `COMMIT;` and journal-published staged output even
when `runImport` failed after one or more successful match transactions. A
partial rebuild script could therefore replace a previous final script and look
ready to apply.

## Decision

- Add `OutputAdapter.complete()` as a default no-op so adapters that publish
  through other durable mechanisms retain their existing lifecycle.
- Call `complete()` only after `runImport` succeeds. SQL-script adapters append
  their final `COMMIT;` and journal-publish staged output during `complete()`.
- SQL-script `close()` rolls back any active match transaction, abandons staged
  output, and leaves the final path untouched unless `complete()` already
  published it.
- Require no active match transaction at completion and reject SQL-script row
  emission outside an active match transaction.

## Consequences

- Failed imports preserve an existing script or leave an absent final path
  absent; staged temporary output is removed.
- Direct SQL-script adapter users must call `complete()` after successful work
  before closing the adapter.
- JDBC and CSV adapters use the default no-op completion method; CSV's own
  publication lifecycle is unchanged.

## Validation

- `SqlScriptOutputAdapterTest` covers failed import abandonment, completion
  during an active transaction, success publication, and rejection of
  transaction-free writes.
- `ApplicationTest` verifies successful import publication and failed-import
  preservation of the previous output.
- `:bbb-update-database:check` passes.
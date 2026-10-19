# ADR 0050: Staged streaming CSV output

## Status

Accepted

## Context

CSV output previously copied all accumulated table CSV files into a temporary
bundle and executed a full journaled directory replacement with hardware `fsync`
flushes on every match commit. For an import with thousands of matches, this
caused $O(N^2)$ disk I/O and excessive kernel context switches, making CSV
generation substantially slower than SQL script generation.

## Decision

- Use staged streaming CSV writers in a temporary directory during import.
- Table files are opened and appended to directly during match transactions; on
  match rollback, table files are truncated back to their snapshot byte offsets.
- Buffer `matches` and `innings` records in memory during the import to support
  in-place updates without intermediate file rewrites.
- Finalize and write `matches.csv` and `innings.csv` and journal-publish the
  staged directory bundle to the final destination path atomically on `complete()`.
- If closed without calling `complete()`, rollback active transactions and clean up
  staged temporary files without modifying the destination directory.

## Consequences

- Disk I/O drops from $O(N^2)$ to strictly $O(N)$ linear streaming writes.
- File copying and directory swap operations are reduced from thousands of times
  per import to exactly once at completion.
- CSV generation performance achieves parity with SQL script generation.

## Validation

- `CsvOutputAdapterTest` verifies transaction rollbacks, byte truncation, error
  handling, in-match updates, and single atomic directory publication on `complete()`.
- `AdapterParityTest` verifies consistency between JDBC, SQL script, and CSV adapters.
- `./gradlew check` passes across all modules.

# ADR 0042: Relational match session and output sinks

## Status

Accepted

Implementation details about JDBC transaction ownership, sink-specific
duplicate clauses, CSV snapshots, and stats-link policy/results are superseded
by ADR 0044 and ADR 0045. The shared relational session and journaled output
decisions remain in effect.

## Context

`SqlScriptOutputAdapter` and `CsvOutputAdapter` each implemented the full `OutputAdapter` surface with near-identical in-memory domain write logic (match identity, source CA allocation, row construction, key counters). `JdbcOutputAdapter` duplicated some decisions against live SQL. Journaled replace existed twice (CSV publisher + SQL script path). Alias uniqueness lived only in application code. Stats/team exception lists were hardcoded Kotlin sets.

## Decision

1. **Session + sinks**: `RelationalMatchSession` owns `RelationalOutputState`, row construction, key allocation, and match/source-CA write decisions. SQL and CSV adapters serialize `RelationalRow` values through `RelationalSink`; SQL retains dialect-specific conflict clauses, and CSV retains bundle buffering and publication. JDBC keeps live database I/O but uses shared SourceCaId outcomes and `RelationalWriteSupport` value helpers.
2. **Single SourceCaId policy**: `SourceCaIdAllocator` returns explicit `Reject`, `NoOp`, `RegisterNow`, or `PendingUpdate` outcomes. The session or JDBC adapter applies those outcomes while each persistence sink projects them; CSV updates `source_ca_id` by column name.
3. **One journaled replace utility**: `JournaledMatchReplace` owns backup, journal, recovery, and rollback for single files and directory bundles. SQL scripts continue to emit their own explicit `BEGIN`/`COMMIT` statements for consumers of the generated script.
4. **Alias uniqueness in schema**: Each dialect's `1__initial_tables.sql` migration enforces unique `(source_system, source_team_name, stats_team_id)`. A source team name can still map to multiple stats teams.
5. **CSV transaction model**: The session snapshots `RelationalOutputState`; the CSV adapter snapshots only sink-local row buffers and match-person deduplication state, not `delivery_fielders`.
6. **Policy as versioned resources**: `StatsLookupPolicy` and `TeamNamePolicy` load exclusion/exception lists from classpath resources under `policies/`, failing fast when a resource is missing or empty.
7. **Linker cleanup**: The JDBC constructor shares one `JdbcStatsMatchRepository` with its alias resolver; parse/link exceptions share the sealed `ParseLinkError` hierarchy; exact and alias candidate APIs stay separate.

## Consequences

- Domain write bugs fix once for SQL script and CSV paths.
- JDBC remains a DB-backed adapter but no longer owns a divergent SourceCaId lifecycle.
- Operators get DB-level protection against duplicate aliases after migration.
- Alias exports must be deduplicated by full triple before restore into a fresh schema; distinct stats-team targets for one source name remain valid.
- Policy lists are reviewable in git without code changes; invalid/missing resources fail fast at load.
- The JDBC session/identity-store/row-sink follow-up is recorded in ADR 0043; that decision supersedes the JDBC-specific follow-up described here.
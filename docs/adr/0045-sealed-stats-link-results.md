# ADR 0045: Sealed stats-link results and linker-owned policy

## Status

Accepted

## Context

`MatchLinker` previously returned a `String` on success and threw
`ParseLinkError` subclasses for expected not-found, ambiguity, and invalid-alias
outcomes. `CricSheetParser` also bypassed the linker for policy exclusions, and
the lookup procedure enumerated Cartesian products of alias candidates.

## Decision

- `MatchLinker.link` returns `StatsLinkResult`: `Linked`, `NotFound`,
  `Ambiguous`, `InvalidAlias`, or `Skipped`. Expected lookup outcomes are data;
  repository/connection failures remain exceptions.
- `StatsMatchLinkPolicy` is evaluated by `StatsMatchLinker`; the parser always
  invokes the linker and maps expected results into the existing per-file
  failure or skip behavior.
- `TeamNamePolicy` only shapes stats-facing names. Alias lookup uses raw source
  names, validates target IDs, and issues one grouped candidate query after the
  exact-name attempt.
- A margin-constrained `NotFound` triggers one explicit second attempt without
  margin; no exception is used to control that retry.
- `CricSheetParser` consumes `ParseCricsheetInput`, which carries its validated
  directory layout. JDBC construction is explicit through
  `StatsMatchLinker.fromConnections`.

## Consequences

- Callers must handle every sealed result exhaustively; successful linkage is
  represented by `Linked.sourceCaId`.
- Expected no-match, ambiguity, invalid-alias, and skip outcomes no longer
  escape the linker as control-flow exceptions.
- Alias identity and stats display names remain distinct during female name
  shaping and manual alias resolution.
- `CricSheetParser` maps only expected results to parse-file outcomes; runtime
  database failures remain exceptional and are logged by the linker.

## Validation

- `StatsMatchLinkerTest` covers exact, set-based alias, not-found, ambiguous,
  invalid-alias, and margin-retry behavior.
- Parser tests cover expected result mapping, policy skips, validated directory
  layout reuse, and preserving failed source files while continuing later files.
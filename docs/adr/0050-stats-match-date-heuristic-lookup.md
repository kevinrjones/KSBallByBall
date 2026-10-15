# ADR 0050: Sequential stats match date fallback

## Status

Accepted

## Context

Cricsheet ball-by-ball files may provide a later calendar date than the
CricketArchive match start date, or may differ by one or two days. The linker
tries the primary date with `HowMuch` when available and then retries that date
without a margin, but a miss leaves an otherwise uniquely identifiable match
unlinked.

## Decision

- Preserve all exact-primary-date attempts before using any date heuristic;
  attempt the margin-constrained lookup only when a margin is available.
- Only after the primary-date attempts return `NotFound`, try alternate dates
  in order: primary - 1, primary + 1, primary - 2, primary + 2.
- For each alternate date, query with the original margin first when present,
  then without a margin if that query returns `NotFound`.
- Stop at the first non-`NotFound` result, including `Ambiguous` and
  `InvalidAlias`; if every attempt misses, retain the primary-date `NotFound`.
- Keep `MatchStartDate = ?` SQL, existing team and alias matching, and skip
  policy unchanged. Do not expand the search window beyond two days in either
  direction.

## Consequences

- One- and two-day start-date mismatches can link when team criteria identify a
  unique stats match, while exact-date matches retain priority and closer
  offsets are preferred over farther ones.
- Ambiguous alternate-date results cannot silently fall through to another
  date and select a different match.
- Hard misses incur at most eight additional lookups when a margin exists; the
  date heuristic is not run for policy-skipped matches.
- Longer multi-day start mismatches remain unresolved and require a broader
  policy decision rather than an implicit SQL date range.

## Validation

- `StatsMatchDateHeuristicTest` covers candidate ordering (±1 then ±2).
- `StatsMatchLinkerTest` covers one- and two-day offsets, closer-date priority,
  exact-date priority, alternate margin/plain retries, ambiguity stopping, and
  the all-miss result.
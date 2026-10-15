# Requirements

### Overview & Goals

Extend the existing stats match date heuristic so two-day start mismatches (BBB date vs CricketArchive `MatchStartDate`) can still resolve a unique `sourceCaId`, while keeping closer (±1) offsets ahead of ±2.

### Scope

#### In Scope

- Widen `StatsMatchDateHeuristic.candidateDates` from ±1 to ordered ±1 then ±2.
- Keep cascade unchanged: exact primary margin/plain first; heuristics only on `NotFound`.
- Per alternate date: margin then plain; stop on first non-`NotFound`.
- Tests, ADR 0050, architecture/README/project-memory updates.

#### Out of Scope

- BETWEEN/range SQL; window beyond ±2; schema/`MatchEndDate` changes.

### Functional Requirements

1. Candidate order: `[primary-1, primary+1, primary-2, primary+2]`.
2. Ambiguous on an earlier candidate still stops the cascade (no fall-through to ±2).
3. Exact primary date still preferred over any heuristic date.

# Delivery Steps

### ✓ Step 1: RED — extend candidate-order and ±2 linker tests
- Update `StatsMatchDateHeuristicTest` for four ordered offsets.
- Add linker cases: two days before, two days after; ensure ±1 still preferred over ±2 when both exist.

### ✓ Step 2: GREEN — widen heuristic candidates to ±2
- Implement ordered ±1 then ±2 in `StatsMatchDateHeuristic`.
- Make new tests green; linker orchestration stays the same loop.

### ✓ Step 3: Docs and module check
- Update ADR 0050, architecture, README, project memory for ±2 window.
- Run `./gradlew :bbb-parse-cricsheet:check`.

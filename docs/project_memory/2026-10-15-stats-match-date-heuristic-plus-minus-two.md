# Stats match date heuristic ±2 window

### Title

Stats match date heuristic ±2 window

### Date/time completed

2026-10-15 21:29

### What was shipped

- Widened sequential date-heuristic candidates from ±1 day to ordered
  `primary-1`, `primary+1`, `primary-2`, `primary+2` so two-day start mismatches
  can still resolve a unique CricketArchive `sourceCaId`.
- Closer offsets still win before farther ones; exact primary date, margin-then-
  plain per date, and stop-on-first non-`NotFound` behavior are unchanged.
- Updated ADR 0050, architecture notes, README, and linker/heuristic tests for
  the two-day window.

### Key decisions

- Prefer distance-first ordering (±1 before ±2) with earlier-before-later at the
  same distance to keep multi-day-behind cases early without letting ±2 steal a
  nearer unique match.
- Keep exact `MatchStartDate = ?` SQL; heuristics remain criteria retries only.
- Cap the window at two days; wider spans stay an explicit follow-up.

### Gotchas

- Hard misses can now make up to eight alternate lookups when a margin is
  present (four dates × margin + plain).
- Neighbor fixtures two days away can still produce `Ambiguous` or a wrong unique
  link if teams alone are not distinctive; uniqueness remains the safety net.

### Test coverage areas

- Candidate order includes ±1 then ±2.
- Linker covers two days before/after, closer-date preference over ±2, and the
  existing exact/margin/ambiguity/miss paths.
- `./gradlew :bbb-parse-cricsheet:check --no-daemon` passed.

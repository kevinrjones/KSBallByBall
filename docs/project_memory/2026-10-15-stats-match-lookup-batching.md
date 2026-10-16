# Stats match lookup batching

### Title

Batch stats match lookup across date heuristics

### Date/time completed

2026-10-15

### What was shipped

- The JDBC stats repository preloads `Teams` once and resolves names and stats
  team IDs to internal team IDs in memory.
- Exact-team candidates for the primary date and all four ±1/±2 dates are
  fetched with one set-based query; a second query is used only for alias
  candidates. Date, margin, and exact-versus-alias priority remain unchanged.
- Positive alias results are cached for the repository lifetime. Empty results
  are deliberately not cached so newly added mappings remain visible.
- Updated `README.md` and application architecture notes with the batching
  behavior and an optional operator-managed stats index recommendation.

### Key decisions

- Preserve the existing selection order in memory: closer date first, then
  margin-constrained exact and alias matches, followed by plain exact and alias
  matches for each date.
- Do not alter the external CricketArchive schema. Its initial table definition
  lacks a `Matches.MatchStartDate` index; consider
  `Matches(MatchStartDate, HomeTeamId, AwayTeamId)` to accelerate these lookups.
- Cache only positive alias lookups to preserve visibility of aliases inserted
  after an earlier miss.

### Gotchas

- Hard-miss linking now uses one exact-candidate query instead of one query per
  date; alias fallback can add one more bulk match query, regardless of the
  margin and plain fallback priorities applied in memory.
- The parser's MariaDB/Testcontainers integration test remains skipped: its
  Docker endpoint returned HTTP 400 both during the full check and an isolated
  rerun, although other module tests started containers later in the full run.
- The repository-wide `clean check` reports a live-database test expecting
  non-empty API data and alias-count/schema comparison failures in
  `bbb-update-database`; the parser module check passes.
- No end-to-end elapsed-time benchmark was run against the production stats
  database; observed validation covers reduced SQL query counts, not total parse
  throughput.

### Test coverage areas

- Exact-date and ±1/±2 ordering, margin fallbacks, aliases, ambiguity, and
  invalid-alias behavior through the linker tests.
- Five-date SQL parameter binding, one-time team preload, and positive-alias
  cache behavior.
- `./gradlew :bbb-parse-cricsheet:check --no-daemon` passes, including the
  linker and SQL/cache tests.
- `./gradlew clean check --no-daemon` was run; it fails in unrelated
  `bbb-api` and `bbb-update-database` tests (see Gotchas).
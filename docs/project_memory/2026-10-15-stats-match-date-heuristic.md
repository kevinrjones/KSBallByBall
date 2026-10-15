# Sequential stats match date fallback

### Title

Sequential stats match date fallback

### Date/time completed

2026-10-15 12:48

### What was shipped

- Stats linking now tries exact primary-date criteria with margin when present
  and then without; only after all primary-date attempts miss, it retries the
  previous and next dates in that order, with margin before plain lookup on each
  alternate date.
- The first `Linked`, `Ambiguous`, or `InvalidAlias` alternate-date result stops
  the cascade; all misses retain the primary-date `NotFound` criteria.
- Added the pure ±1-day candidate helper, heuristic attempt/success logging,
  SQLite-backed lookup tests, ADR 0050, and updates to the architecture guide and
  README.
- Aligned the SQLite linker fixture with the repository's existing
  `MatchType` predicate and updated parser-test `MatchLinker` lambdas to accept
  the current interface parameters.

### Key decisions

- Keep CricketArchive SQL on exact `MatchStartDate` equality; the date heuristic
  is a bounded sequence of existing criteria lookups, not a range query.
- Keep the source primary date as the anchor and try only `primary - 1` then
  `primary + 1` to limit neighboring-fixture false positives.
- Treat ambiguity and invalid aliases as terminal results for the cascade;
  policy-skipped matches never enter lookup.

### Gotchas

- The ±1-day window does not resolve longer multi-day start-date differences;
  rerunning the parser remains the remediation path after operator data updates.
- Repository-wide `./gradlew clean check --no-daemon` still has unrelated API
  live-database and updater alias-seed assertion failures; the changed
  `:bbb-parse-cricsheet:check` passes.

### Test coverage areas

- Candidate date order; start-date offsets in both directions; exact-date
  priority; alternate-date margin matching and plain fallback; ambiguity stop;
  total miss criteria.
- `./gradlew :bbb-parse-cricsheet:check --no-daemon` passed.
- `./gradlew clean check --no-daemon` ran all modules but failed in unrelated
  `bbb-api` and `bbb-update-database` tests.
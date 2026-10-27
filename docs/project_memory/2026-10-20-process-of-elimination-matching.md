# Process-of-Elimination and Duck-Typing Name Matching for Players and Officials

## Title
Multi-pass elimination and relaxed duck-typing name matching for players and officials with warning logging to lookup_general.log

## Date/time completed
2026-10-20 21:42

## What was shipped
- Implemented multi-pass elimination matching in `StatsPlayerLinker` where high-confidence exact/fuzzy candidate matches claim roster records first, leaving remaining unmatched candidates and persons for secondary duck-typing resolution.
- Added `PlayerNameMatcher.matchCloseEnough`, `isCloseEnoughMatch`, `isSurnameRelaxedFuzzyMatch`, and `calculateSimilarityScore` supporting given-name token sharing (e.g., `Mehedi Maruf` $\leftrightarrow$ `Mehedi Hasan`) and relaxed initials/surname tolerance (e.g., TV Umpire `D Shepherd` $\leftrightarrow$ `DJ Shepard`).
- Added warning-level logging via `LOOKUP_FAILURE_LOGGER` to route elimination matches to `lookup_general.log`.
- Guarded against short 3-letter surname conflation (e.g., `Ray` vs `Roy`) and initial mismatches.
- Added unit and integration tests across `PlayerNameMatcherTest` and `StatsPlayerLinkerTest`.

## Key decisions
- Multi-pass execution: Pass 1 resolves exact and high-confidence fuzzy candidates; Pass 2 iteratively applies relaxed duck-typing over unclaimed candidates; Pass 3 logs remaining unresolved candidates as errors.
- Logging: Relaxed elimination matches emit `WARN` logs which `logback.xml` routes to `lookup_general.log` (and stdout), while hard unmatched failures emit `ERROR` logs to `lookup_errors.log`.

## Gotchas
- Surnames of length $\le 3$ (e.g. `Roy` vs `Ray`) are strictly protected from matching on 1-character edits to prevent conflating common short surnames.

## Test coverage areas
- `PlayerNameMatcherTest`: Verifying given-name sharing, relaxed fuzzy surname matching, short-surname rejection, and initial mismatch rejection.
- `StatsPlayerLinkerTest`: Verifying end-to-end player linking and official linking (`Mehedi Maruf` to `Mehedi Hasan`, `D Shepherd` to `DJ Shepard`) with DB persistence and cache updates.

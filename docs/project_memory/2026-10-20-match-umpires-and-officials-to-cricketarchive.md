# Match Umpires and Officials to CricketArchive

## Title
Match umpires, reserve umpires, and match referees to CricketArchive database

## Date/time completed
2026-10-20 10:20

## What was shipped
- Extended `JdbcStatsPlayerRepository` with SQL queries retrieving on-field umpires (`UmpiresMatches` / `Umpires`), TV umpires (`TvUmpiresMatches` / `TvUmpires`), reserve umpires (`ReserveUmpiresMatches` / `ReserveUmpires`), match referees (`MatchRefereesMatches` / `MatchReferees`), and general officials (`OfficialsMatches` / `Officials`).
- Updated `PlayerNameMatcher` to perform robust partial-name, multi-word, and compact initials matching (e.g. matching `AA Baig` from Ball-by-Ball data to `A Ali Baig` in CricketArchive data).
- Handled bidirectional initials, word-by-word given name subset matching, and disambiguation when multiple candidates share surnames.
- Added comprehensive unit and integration tests across `PlayerNameMatcherTest`, `JdbcStatsPlayerRepositoryTest`, and `StatsPlayerLinkerTest`.

## Key decisions
- Extended roster querying to include all official match roles alongside playing XI members so that `StatsPlayerLinker` automatically resolves and caches CricketArchive person IDs (`ca_id`) for umpires and referees.
- Supported partial-name and compact initial matching in `PlayerNameMatcher` using bidirectional initial extraction (`extractInitials`), ensuring variations like `AA Baig` -> `A Ali Baig` or `Ranjan Madugalle` -> `RS Madugalle` resolve accurately.
- Retained strict disambiguation: if initials or partial names match multiple candidates in the same match roster, matching returns null rather than linking ambiguously.

## Gotchas
- When extracting initials for tokens like `AA` vs `A Ali`, a single token containing multiple letters is treated as compact initials so that `["aa"]` yields `["a", "aa"]` while `["a", "ali"]` yields `["aa"]`, matching them cleanly.

## Test coverage areas
- `PlayerNameMatcherTest`: Compact initials matching (`AA Baig` to `A Ali Baig`), single initial matching (`A Baig`), given name subsets (`Ali Baig`), full name matching (`Abbas Ali Baig`), referee/umpire name variations (`RS Madugalle`, `KN Ananthapadmanabhan`).
- `JdbcStatsPlayerRepositoryTest`: Querying match roster containing players, umpires, TV umpires, reserve umpires, and match referees across SQLite memory database.
- `StatsPlayerLinkerTest`: End-to-end linking and caching of match umpires and referees.

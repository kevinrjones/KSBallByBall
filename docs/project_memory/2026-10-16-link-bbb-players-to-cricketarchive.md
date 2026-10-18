# Link BBB Players to CricketArchive Stats Database

## Title

Link BBB Player Identities to CricketArchive with In-Memory Caching and Write-Through Persistence

## Date/time completed

2026-10-16 16:30

## What was shipped

- Added `SourcePersonId` tiny type in `bbb-shared` with validation and serialization support.
- Implemented `PlayerNameMatcher` functional engine in `bbb-parse-cricsheet` supporting exact normalized full name matching, tokenized initials and surname matching, and diacritic/punctuation stripping against match rosters.
- Implemented `StatsPlayerRepository` / `JdbcStatsPlayerRepository` for querying CricketArchive match player rosters by `sourceCaId`.
- Implemented `PlayerIdentityRepository` / `JdbcPlayerIdentityRepository` for preloading known player `(source_person_id -> ca_id)` mappings from the BBB database `people` table and persisting newly resolved links immediately.
- Implemented `ThreadSafePlayerCache` backed by `ConcurrentHashMap` for O(1) thread-safe player lookups.
- Implemented `StatsPlayerLinker` and integrated it into `CricSheetParser` and `Application.kt`, ensuring matches with all cached players bypass stats database queries completely.
- Added comprehensive unit and integration tests covering name variations, caching, database persistence, and parser linking.

## Key decisions

- Player matching is scoped strictly to the roster of players in the linked match (`sourceCaId`), preventing false positive name collisions across the global player database.
- Cache-first lookup skips stats queries whenever all players in a match are already cached, keeping subsequent match processing fast.
- Write-through persistence writes resolved player IDs to both the memory cache and the BBB `people` table immediately during match parsing.

## Gotchas

- Some players may have compound surnames (e.g. "de Villiers", "du Plessis") or hyphenated first names ("Jean-Paul"); name tokenization normalizes spaces and punctuation to cleanly handle multi-part surnames and initials.

## Test coverage areas

- `PlayerNameMatcherTest`: Exact matches, known names, initials + surname, compound surnames, accented characters, ambiguous matches.
- `JdbcStatsPlayerRepositoryTest`: Querying match rosters, preloading BBB player mappings, saving new mappings.
- `StatsPlayerLinkerTest`: Cache hit bypass, cache miss matching & persistence, unmatched player handling.
- `CricSheetParserTest` & `ApplicationTest`: Pipeline integration and connection configuration.
- `SourcePersonIdTest`: Validation and boundary checks.

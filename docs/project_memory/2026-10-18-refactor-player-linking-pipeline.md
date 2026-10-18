# Project memory entry: Refactor Player Linking & Ingestion Pipeline

### Title
Refactor Player Linking and Database Ingestion Pipeline

### Date/time completed
2026-10-18 11:25

### What was shipped
- Extracted shared cricket name splitting heuristic (`getNameParts`) into `bbb-cli-shared` (`PersonNameSplitter.kt`), eliminating duplicate logic across `bbb-parse-cricsheet` and `bbb-update-database`.
- Standardized `peopleCaId` mapping in `PlayersRegistry` to be explicitly and consistently keyed by immutable `source_person_id` rather than display names across `CricSheetParser`, `Translate`, and `Database`.
- Adopted `SourcePersonId` tiny type consistently across `PlayerIdentityRepository`, `PlayerCache`, `StatsPlayerLinker`, and `PlayerMatchResult`.
- Aligned in-memory cache population in `StatsPlayerLinker` with database persistence success, preventing phantom cache hits when database writes fail.
- Refactored `StatsPlayerLinker.linkPlayers` and `CricSheetParser.processFile` into declarative functional pipelines using standard collection partitioning and composable transformations.

### Key decisions
- Consolidated domain name splitting heuristics into `bbb-cli-shared` to prevent cross-module drift between parsing and database update operations.
- Contractually bound `peopleCaId` keys strictly to `source_person_id`, removing ambiguous name-versus-id fallback resolution in `Translate.kt`.
- Synchronized cache updates to occur only after successful database persistence in `StatsPlayerLinker`.

### Gotchas
- When splitting person titles (e.g., "Lord", "Nawab of"), ensure safety guards on token length prevent index out of bounds exceptions on two-word names.

### Test coverage areas
- `PersonNameSplitterTest` in `bbb-cli-shared` covering standard names, initials, compound titles, parenthesized role tags, and Turkish locale invariance.
- `StatsPlayerLinkerTest` in `bbb-parse-cricsheet` testing cache hits, database queries, unmatched handling, and database write failure consistency.
- `JdbcStatsPlayerRepositoryTest` and `CricSheetParserTest` testing end-to-end player linking and `peopleCaId` JSON envelope serialization.
- `DatabaseTest` in `bbb-update-database` verifying `peopleCaId` extraction and database insertion into the `people` table.
- Full `./gradlew clean check --no-daemon` across all modules.

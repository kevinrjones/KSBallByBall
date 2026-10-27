# Substitute Fielders Roster Resolution

## Title
Querying substitute fielders from Fielding table in stats database roster lookups

## Date/time completed
2026-10-20 21:25

## What was shipped
- Extended `JdbcStatsPlayerRepository.DEFAULT_ROSTER_QUERIES` to query the `Fielding` table joined with `Players` and `Matches` based on match `CaId`.
- Enabled automated resolution of substitute fielders who participated as fielders (taking catches, stumpings, or participating in dismissals) without appearing in `PlayersMatches`.
- Added unit and integration tests across `JdbcStatsPlayerRepositoryTest` and `StatsPlayerLinkerTest`.

## Key decisions
- Integrated the `Fielding` table query alongside `PlayersMatches`, `UmpiresMatches`, `TvUmpiresMatches`, `ReserveUmpiresMatches`, `MatchRefereesMatches`, and `OfficialsMatches`.
- Maintained automatic de-duplication via `distinctBy { it.playerId to it.fullName }` to ensure players appearing in both playing XI and fielding rosters do not duplicate.

## Gotchas
- The `Fielding` table stores `PlayerId` referencing `Players.Id` and `MatchId` referencing `Matches.Id`, requiring a join on `Players` to extract `PlayerId`, `FullName`, `SortNamePart`, and `OtherNamePart`.

## Test coverage areas
- `JdbcStatsPlayerRepositoryTest`: Verifying that substitute fielders who only appear in `Fielding` are returned as part of `playersForMatch`.
- `StatsPlayerLinkerTest`: Verifying that substitute fielders in the roster are resolved, cached, and written through to `PlayerIdentityRepository`.

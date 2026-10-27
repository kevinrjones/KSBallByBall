# Exact Stats Person Name Matching

### Title
Exact stats person name matching

### Date/time completed
2026-10-22 10:42

### What was shipped
- Added exact full-name lookup across `Players`, `Umpires`, `TvUmpires`, `ReserveUmpires`, `MatchReferees`, `Scorers`, and `Officials`.
- Linked unresolved Cricsheet players and officials when the exact lookup produced one distinct stats ID, including identities absent from the match roster.
- Kept ambiguous exact names unresolved and persisted/cache-populated only unique results.
- Updated the README with the new person-link fallback behavior.

### Key decisions
- Match-roster matching remains the first choice; global exact lookup only runs after the existing roster matching passes.
- Candidate uniqueness is determined by external stats ID, deduplicating repeated IDs across role-specific and generic official tables.
- Full-name equality is checked exactly after the SQL query to guard against case-insensitive database collations.

### Gotchas
- Some stats database variants may not contain every optional people table; individual lookup queries tolerate missing tables.
- Exact names that map to multiple distinct stats IDs remain unresolved rather than being selected arbitrarily.

### Test coverage areas
- JDBC tests cover all identity tables, duplicate IDs, and conflicting exact-name IDs.
- Linker tests cover an off-roster referee with a unique exact ID, persistence/cache behavior, and unresolved ambiguous results.
- `./gradlew clean check --no-daemon` passed across all modules.
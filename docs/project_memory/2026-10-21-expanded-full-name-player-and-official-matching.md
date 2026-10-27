# Expanded Full Name Player and Official Resolution

### Title
Expanded Full Name Player and Official Resolution

### Date/time completed
2026-10-21 21:45

### What was shipped
- Enhanced `StatsRosterPlayer` and `JdbcStatsPlayerRepository` to query and include `ExpandedFullName` from both `Players` and `Officials` tables in the stats database.
- Integrated `ExpandedFullName` across all tiers in `PlayerNameMatcher`:
  - Tier 1: exact normalized match against `fullName`, `knownAs`, or `expandedFullName`.
  - Tier 2 & Tier 3: initials and fuzzy surname matching evaluated against tokenized `expandedFullName`.
  - Tier 4 & Duck-typing (`matchCloseEnough`): token overlap and composite similarity scoring against `expandedFullName`.
- Enabled automated resolution of names where Cricsheet uses middle or given names as surnames (e.g. `E Malinga` $\leftrightarrow$ `Kiribathgala Kankanamalage Eshan Malinga Dharmasena`, `S Vidyut` $\leftrightarrow$ `Vidyut Sivaramakrishnan`, `Ahsan Malik` $\leftrightarrow$ `Malik Ahsan Ahmad Jamil`, `KS Jagannathan` $\leftrightarrow$ `Kannan Sudarsanam Jagannathan`, `P Ushettige` $\leftrightarrow$ `Ushettige Don Pasindu Madawa`, `S Gimhan` $\leftrightarrow$ `Sithara Gimhan Liyanage`, `U Lakshan` $\leftrightarrow$ `Warnakulasooriya Umesh Lakshan Kurera`, `SB Keerthana` $\leftrightarrow$ `Keerthana Sathyamoorthy Balakrishnan`, `Homendro Kabrambam` $\leftrightarrow$ `Kabrambam Homendro Meitei`, `P Prafullomani` $\leftrightarrow$ `Pukhrambam Prafullomani Singh`).
- Added comprehensive unit and integration tests across `PlayerNameMatcherTest` and `JdbcStatsPlayerRepositoryTest`.

### Key decisions
- Querying `p.ExpandedFullName` and `o.ExpandedFullName` leverages existing full name metadata in CricketArchive without additional queries or schema migrations.
- When evaluating `candidateExpandedTokens` in `checkTokensMatch` and `checkTokensCloseEnoughMatch`, using the trailing token of the expanded full name as the default candidate surname allows standard initials + surname logic to seamlessly resolve inverted and multi-token patronymic names.

### Gotchas
- When `candidateExpandedTokens` is checked without an explicit surname, passing an empty explicit surname list ensures `candidateExpandedTokens.last()` is used as the surname rather than forcing candidate's primary abbreviated surname.

### Test coverage areas
- `PlayerNameMatcherTest`: expanded full name resolution across Tier 1, Tier 2, and duck-typing fallback matching.
- `JdbcStatsPlayerRepositoryTest`: retrieval of `ExpandedFullName` from `Players` and `Officials` SQLite in-memory tables.

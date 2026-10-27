# Fuzzy and Phonetic Player Name Matching

## Title
Match-scoped fuzzy and phonetic player name resolution

## Date/time completed
2026-10-20 11:45

## What was shipped
- Implemented pure Kotlin bounded Levenshtein edit distance and similarity ratio calculations in `PlayerNameMatcher`.
- Implemented cricket-specific transliteration and phonetic normalization (handling `dh`/`d`, `th`/`t`, `ph`/`f`/`p`, `ee`/`i`, `oo`/`u`, `w`/`v`, `y`/`i`, `q`/`k`, and consecutive duplicate letter collapse).
- Integrated multi-tiered matching into `PlayerNameMatcher`:
  - Tier 1: Exact normalized match on `fullName` or `knownAs`
  - Tier 2: Initials and exact surname match
  - Tier 3: Initials and fuzzy Levenshtein surname match (bounded by length-proportional distance and similarity ratio)
  - Tier 4: Initials and transliteration/phonetic surname match
- Enforced strict uniqueness and ambiguity rejection: if multiple candidates in the match roster match with fuzzy/phonetic similarity, `matchPlayer` returns `null` to eliminate false positives.
- Ensured resolved player identities write through to the BBB `people` database table and `ThreadSafePlayerCache` via `StatsPlayerLinker`.
- Enhanced `PlayerNameMatcher` to correctly handle inverted candidate name formats (e.g. `Varadharajan, A` / surname first) and single-token candidates when matching against full given name + surname source tokens (`Aaditya Varadarajan`).
- Updated `CricSheetParser` and `StatsPlayerLinker` to log parsing lookup failures for unmatched players and officials (or empty rosters) directly at ERROR level to `lookup_errors.log` (`LOOKUP-ERRORS-FILE`), including the relative path of the parsed JSON file (e.g. `odis_json/1445038.json`) or FQN.
- Added comprehensive unit and integration tests across `PlayerNameMatcherTest` and `StatsPlayerLinkerTest`.

## Key decisions
- Scoped fuzzy string distance metrics strictly to the in-memory match roster (~22–26 players), keeping execution time $< 0.1\text{ ms}$ with zero full-table database scans.
- Applied length-proportional distance thresholding (length $\le 3$ disallowed for fuzzy matching; length $4..7$ allowed distance $\le 1$; length $\ge 8$ allowed distance $\le 2$).
- Gated fuzzy surname matching on initial / given-name compatibility, preventing conflation of different players sharing similar surnames.

## Gotchas
- When performing in-match relational CSV updates in `CsvOutputState`, `emitRow` must immediately return after applying the update rather than continuing into standard row append logic.

## Test coverage areas
- `PlayerNameMatcherTest`: Exact matches, initials matches, spelling discrepancy matches (`Aaditya Varadarajan` to `A Varadharajan`), transliteration variations (`Mohammed Shami` $\leftrightarrow$ `Mohammad Shami`, `Ravi Bishnoi` $\leftrightarrow$ `R Bishnoy`, `Usman Qasim` $\leftrightarrow$ `U Kasim`, `Anrich Nortje` $\leftrightarrow$ `A Nortje`, `A Choudhary` $\leftrightarrow$ `A Chowdhury`), short surname guard (`Roy` vs `Ray`), initials mismatch guard (`K Varadarajan` vs `A Varadharajan`), ambiguity rejection, and string utility metrics.
- `StatsPlayerLinkerTest`: Verification that fuzzy matches resolve and write through to database and in-memory caches.

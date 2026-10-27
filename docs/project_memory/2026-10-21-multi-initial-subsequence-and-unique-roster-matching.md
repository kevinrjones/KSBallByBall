# Multi-Initial Subsequence and Unique Match Roster Resolution

### Title
Multi-Initial Subsequence and Unique Match Roster Resolution

### Date/time completed
2026-10-21 11:15

### What was shipped
- Enhanced `PlayerNameMatcher` with `isInitialsCompatible` and `isSubsequence` to resolve multi-initial player records (e.g. `TPLND Perera` matching `N Perera`, `DLS Croospulle` matching `L Croospulle`, `IS McLelland` matching `S McLelland`, `LMD Madushanka` matching `D Madushanka`, `AGDSJ Bandara` matching `Dinusha Bandara`, `ST Ndwandwa` matching `Tsepo Ndwandwa`).
- Extended `matchCloseEnough` duck typing and elimination matching to resolve unique surnames within match rosters (e.g. `D Morton` $\leftrightarrow$ `HJ Morton`, `M Home` $\leftrightarrow$ `CS Home`, `A Virdi` $\leftrightarrow$ `GS Virdi`, `Mirza Baig` $\leftrightarrow$ `Tahir Baig`), distinctive token overlap (e.g. `SM Meherob` $\leftrightarrow$ `Meherob Hasan`), and transliterated initials (`Qazi Onik` $\leftrightarrow$ `Kazi Anik`).
- Guarded strictly against initial conflicts (e.g. `K Varadarajan` vs `A Varadharajan`) and short 3-letter surname mismatches (e.g. `Roy` vs `Ray`).
- Added comprehensive unit and integration tests across `PlayerNameMatcherTest` and `StatsPlayerLinkerTest`.

### Key decisions
- Subsequence initial matching (`isSubsequence`) allows single or multi-token source initials to match multi-initial acronyms from stats database rosters when surnames match.
- Unique surname resolution in `matchCloseEnough` allows substitute fielders and players with varying given names in Cricsheet to resolve to their official match roster entries when the surname is unique within the match and at least 4 characters long.

### Gotchas
- When comparing initials, initial strings must never be tested as arbitrary substrings of full candidate words to prevent false-positive initial matches between distinct players sharing the same surname.

### Test coverage areas
- `PlayerNameMatcherTest`: multi-initial candidate matches, transliterated names (`Qazi Onik` $\leftrightarrow$ `Kazi Anik`), unique surnames (`D Morton`, `A Virdi`, `Mirza Baig`, `SM Meherob`), and initial conflict rejections.
- `StatsPlayerLinkerTest`: match-scoped elimination and cache persistence.

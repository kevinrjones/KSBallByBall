# Player Elimination Matching with CamelCase Sort Parts and Transliteration Equivalence

### Title
Player Elimination Matching with CamelCase Sort Parts and Transliteration Equivalence

### Date/time completed
2026-10-21 10:35

### What was shipped
- Resolved player matching for CamelCase database sort names (e.g. `MehdiHasan`, `ShakibAlHasan`, `tenDoeschate`) by splitting camelCase on uppercase transitions during string normalization.
- Enhanced first-name token comparison in `checkTokensCloseEnoughMatch` and `calculateSimilarityScore` to support fuzzy and phonetic transliteration variants (e.g. `Mehedi` $\leftrightarrow$ `Mehdi`), allowing process-of-elimination matching for players with spelling discrepancies across source JSON and database rosters.
- Added single-candidate elimination fallback in `matchCloseEnough` when all other players in a match roster have been resolved, while guarding strictly against short 3-letter surname mismatches (e.g. `Roy` vs `Ray`).
- Added comprehensive unit and integration tests across `PlayerNameMatcherTest` and `StatsPlayerLinkerTest`.

### Key decisions
- Normalization splits camelCase tokens (`(?<=[a-z])(?=[A-Z])`) so concatenated `SortNamePart` entries in the stats database are properly tokenized into given-name and surname components.
- First-name tokens in `checkTokensCloseEnoughMatch` are compared using relaxed fuzzy and phonetic similarity metrics, ensuring variations like `Mehedi Maruf` vs `Mehdi Hasan` match during process of elimination.

### Gotchas
- The single-candidate elimination fallback in `matchCloseEnough` enforces a strict short-surname guard to prevent false positives between distinct short names like `Roy` and `Ray`.

### Test coverage areas
- `PlayerNameMatcherTest`: camelCase normalization splitting, `Mehedi Maruf` $\leftrightarrow$ `Mehdi Hasan` with `MehdiHasan` sort name, and short-surname guard validation.
- `StatsPlayerLinkerTest`: end-to-end match-scoped elimination resolution and cache persistence for players with composite sort names.

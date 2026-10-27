# Expanded Name Middle-Token Matching

### Title
Expanded Name Middle-Token Matching

### Date/time completed
2026-10-21 21:54

### What was shipped
- Added a Tier 2 expanded-name matching rule that resolves a source surname occurring anywhere in a CricketArchive `ExpandedFullName` when each supplied given-name token is an exact prefix of an expanded-name token.
- Resolved the verified match-roster forms `Ruvishan Perera` to `Gammanage Sachintha Ruvishan Perera` and `T Dilshan` to `Wijesinghe Archchilage Dilshan Tharinda Wijesinghe`.
- Added a focused regression test that rejects the conflicting `HGK Dilshan` roster candidate while accepting the unique intended match.

### Key decisions
- The new rule is restricted to exact surname-token inclusion and given-name token prefixes, preserving the existing multi-tier ambiguity rejection rather than introducing broad fuzzy matching.
- Verified unmatched officials such as `C Ackerman`, `J Chikange`, and `PA Redhead` are absent from their CricketArchive match rosters, which instead list different officials; they are intentionally left unmatched.

### Gotchas
- CricketArchive `ExpandedFullName` may place familiar source surnames in the middle of a patronymic or extended legal name, so treating only its final token as a surname loses valid links.
- A matching `CaId` does not guarantee matching official assignments between Cricsheet and CricketArchive source records.

### Test coverage areas
- `PlayerNameMatcherTest`: middle-token expanded-name identity matching, including ambiguity protection against another `Dilshan` candidate.
- Full `./gradlew clean check --no-daemon` across all modules.
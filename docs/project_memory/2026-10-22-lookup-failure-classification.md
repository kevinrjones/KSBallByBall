# Lookup Failure Classification

### Title
Lookup Failure Classification

### Date/time completed
2026-10-22 07:59

### What was shipped
- Added match-scoped resolution for reordered names where a source token contracts adjacent `ExpandedFullName` tokens, resolving `Yebin Ka` to `Ka Ye Bin`.
- Enhanced unresolved-person entries in `lookup_errors.log` with the reason that no unique compatible roster identity remained, plus up to five remaining candidate names.

### Key decisions
- The contracted-token rule accepts only source tokens of at least two characters that exactly match either one expanded-name token or two adjacent expanded-name tokens; normal multi-tier uniqueness checks still apply.
- Retained unresolved entries when CricketArchive has no roster for the source match or contains multiple plausible people, rather than introducing an unsafe global database search.

### Gotchas
- The log contains duplicate records from multiple Cricsheet competition directories for the same underlying person and match.
- Source and CricketArchive official assignments can differ despite sharing a match `CaId`.

### Test coverage areas
- `PlayerNameMatcherTest`: reordered, contracted expanded-name resolution for `Yebin Ka`.
- Full parser suite and project-wide Gradle checks.
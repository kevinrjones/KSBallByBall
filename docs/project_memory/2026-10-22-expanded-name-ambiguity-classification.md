# Expanded Name Ambiguity Classification

### Title
Expanded Name Ambiguity Classification

### Date/time completed
2026-10-22 08:22

### What was shipped
- Added shared tiered compatible-candidate evaluation to `PlayerNameMatcher`.
- Enhanced unmatched lookup logging to identify ambiguous compatible candidates by name.
- Added a regression test covering `T Dilshan`, which matches both `WADT Wijesinghe` and `WKTD Wickrama` through their expanded names.

### Key decisions
- Retained strict ambiguity rejection: `T Dilshan` is not linked because both `Dilshan Tharinda` and `Thusith Dilshan` are valid matches in the same CricketArchive roster.

### Gotchas
- The prior generic remaining-roster list obscured actual ambiguity by including unrelated candidates.

### Test coverage areas
- `PlayerNameMatcherTest`: expanded-name ambiguity candidates and rejection.
- Full `./gradlew clean check --no-daemon` across all modules.
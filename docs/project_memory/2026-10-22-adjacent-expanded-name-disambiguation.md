# Adjacent Expanded-Name Disambiguation

### Title
Adjacent Expanded-Name Disambiguation

### Date/time completed
2026-10-22 10:08

### What was shipped
- Added high-precedence matching for a source name that is an ordered, adjacent fragment of a CricketArchive `ExpandedFullName`.
- Resolved `T Dilshan` in match `a1353038` to `WKTD Wickrama` because `Thusith Dilshan` is a consecutive identity fragment of `Wickrama Kaluthotage Thusith Dilshan Wickrama`.
- Retained the broader expanded-name fallback for names with non-adjacent source tokens.

### Key decisions
- Adjacent expanded-name evidence is evaluated before broad token-anywhere matching, so it safely disambiguates an abbreviated source name without a global search.
- `WADT Wijesinghe` is not selected because its `Dilshan` and `Tharinda` tokens do not form the source-order fragment `T Dilshan`.

### Gotchas
- A legal expanded name can contain another player's abbreviated source form as unrelated middle tokens; membership alone is insufficient identity evidence.

### Test coverage areas
- `PlayerNameMatcherTest`: real roster-shaped `T Dilshan` regression with competing `WADT Wijesinghe` and `WKTD Wickrama` candidates.
- Full `bbb-parse-cricsheet` test suite and project-wide Gradle checks.
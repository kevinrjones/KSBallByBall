# Split lookup logs by severity

### Title

Split lookup logs by severity

### Date/time completed

2026-10-13 11:26

### What was shipped

- Lookup `ERROR` events now write to `logs/lookup_errors.log`.
- Non-error lookup events now write to `logs/lookup_general.log`; console output
  continues unchanged.
- Both files retain the existing 10 MB rotation, 60-day history, and 20 GB
  total-size cap.
- Updated parser developer documentation to describe the split.

### Key decisions

- Used complementary Logback `LevelFilter`s: the errors file accepts only
  `ERROR`, and the general file denies `ERROR` while accepting other events.
- Set the lookup logger threshold to `INFO` so informational lookup events are
  retained in the general log.

### Gotchas

- The logger threshold applies before appender filters: `INFO`, `WARN`, and
  `ERROR` reach the lookup appenders; `ERROR` is excluded from the general file.
- Lookup events continue to appear in the console as before.

### Test coverage areas

- Logback configuration assertions for both file paths and rolling patterns.
- Appender-specific assertions for the complementary `ERROR` filters and
  `INFO` logger threshold.
- Test logging configuration remains free of file appenders.
- `./gradlew :bbb-parse-cricsheet:test --tests com.knowledgespike.ballbyball.parsecricsheet.LogbackConfigurationTest --no-daemon` passed.
- `./gradlew clean check --no-daemon` passed across all modules.
# Update Cricsheet Archive Location

## Title

Update Cricsheet archive path in IntelliJ run configurations and project documentation

## Date/time completed

2026-11-01 17:05

## What was shipped

- Updated all IntelliJ IDEA Gradle run configurations in `.idea/workspace.xml` to use the new archive location at `/Users/kevinjones/Dropbox/projects/cricket/CricSheet/Archive/cricsheet`.
- Updated `.idea/applications.iml` and `.idea/gradle.xml` module references.

## Key decisions

- Pointed all `-bd` arguments in IntelliJ IDEA run configurations (`:bbb-bulkupdate:run`, `:bbb-parse-cricsheet:run (nightly)`, `:bbb-parse-cricsheet:run`, `:bbb-update-database:run (csv)`, `:bbb-update-database:run (nightly-csv)`, `:bbb-update-database:run (nightly-db)`, `:bbb-update-database:run (sql_file)`) to `/Users/kevinjones/Dropbox/projects/cricket/CricSheet/Archive/cricsheet/current` or `/Users/kevinjones/Dropbox/projects/cricket/CricSheet/Archive/cricsheet/nightly`.

## Gotchas

- None.

## Test coverage areas

- Verified configuration consistency and ran `./gradlew clean check --no-daemon` to confirm all test suites across all modules pass.

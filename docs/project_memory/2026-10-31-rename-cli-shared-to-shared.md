# Rename `bbb-cli-shared` to `bbb-shared`

## Title

Rename `bbb-cli-shared` module to `bbb-shared` and update dependent projects

## Date/time completed

2026-10-31 09:25

## What was shipped

- Renamed module directory `bbb-cli-shared` to `bbb-shared`.
- Updated Gradle configuration in `settings.gradle.kts` to register `bbb-shared`.
- Updated subproject dependencies in `bbb-update-database/build.gradle.kts`, `bbb-parse-cricsheet/build.gradle.kts`, `bbb-get-cricsheet-data/build.gradle.kts`, and `bbb-bulkupdate/build.gradle.kts` to depend on `:bbb-shared`.
- Updated `bbb-update-database/Dockerfile` to copy `bbb-shared`.
- Updated documentation across `AGENTS.md`, `.junie/AGENTS.md`, `README.md`, `UBIQUITOUS_LANGUAGE.md`, `docs/architecture/applications.md`, `docs/architecture/database.md`, and `docs/GENERATING_KEYS.md`.

## Key decisions

- Standardized the shared module name as `bbb-shared` for shared domain models, tiny types, identity contracts, and database utilities across all remaining CLI and batch applications.

## Gotchas

- None.

## Test coverage areas

- `bbb-shared`: Unit tests for `DatabaseConnectionTest`, `PublicMatchIdTest`, `SourceCaIdTest`, `SourcePersonIdTest`, `DeterministicIdentityTest`.
- `bbb-update-database`: SQL, CSV, and JDBC adapter tests.
- `bbb-parse-cricsheet`: Parser and linker tests.
- `bbb-get-cricsheet-data`: Download and extraction tests.
- `bbb-bulkupdate`: Bulk update tests.
- Full verification passed with `./gradlew clean check --no-daemon`.

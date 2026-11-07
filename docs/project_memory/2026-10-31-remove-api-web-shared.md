# Removal of `bbb-api`, `bbb-web`, and `bbb-shared`

## Title

Remove `bbb-api`, `bbb-web`, and `bbb-shared` applications and update documentation/configuration

## Date/time completed

2026-10-31 08:44

## What was shipped

- Removed `bbb-api`, `bbb-web`, and `bbb-shared` modules and directories from the repository.
- Migrated shared essential classes (`DatabaseConnection`, `DataSourceManager`, `Logging`, `InvalidStateException`, `PublicMatchId`, `DeterministicIdentity`, `IdentityIds`) and their tests into `bbb-cli-shared`.
- Updated all remaining modules (`bbb-update-database`, `bbb-parse-cricsheet`, `bbb-get-cricsheet-data`, `bbb-bulkupdate`) and `settings.gradle.kts` to remove dependencies on `bbb-shared`.
- Removed Ktor, Node.js, and Angular build configurations from root `build.gradle.kts` and `settings.gradle.kts`.
- Updated Docker configurations: removed `api` and `web` services from `compose.yaml` and updated `bbb-update-database/Dockerfile`.
- Deleted obsolete GitHub workflows (`build-bbb-api.yml`, `build-bbb-web.yml`).
- Updated all documentation (`README.md`, `README-DEV.md`, `AGENTS.md`, `UBIQUITOUS_LANGUAGE.md`, `docs/architecture/applications.md`, `docs/architecture/database.md`, `docs/GENERATING_KEYS.md`).

## Key decisions

- Consolidated all common CLI models, database connection management, and deterministic identity types into `bbb-cli-shared`.
- Streamlined Docker Compose stack to focus on database provisioning and database loading tools.

## Gotchas

- Any external scripts referencing the REST API ports (8081/8080) should be aware that the project now focuses solely on CLI ingestion, parsing, database updating, and bulk loading.

## Test coverage areas

- `bbb-cli-shared`: `DatabaseConnectionTest`, `PublicMatchIdTest`, `SourceCaIdTest`, `SourcePersonIdTest`.
- `bbb-update-database`: all unit and integration tests across SQL, CSV, and JDBC adapters.
- `bbb-parse-cricsheet`: parser, linking, and policy tests.
- `bbb-get-cricsheet-data`: archive download and registry tests.
- `bbb-bulkupdate`: bulk update database loader tests.
- Full verification passed with `./gradlew clean check --no-daemon`.

# Repository guidance

## Project overview

BallByBall is a Java 21 / Kotlin Gradle multi-module project for retrieving,
normalizing, storing, and loading cricket ball-by-ball data.

- `bbb-get-cricsheet-data`: download Cricsheet archives and player registers.
- `bbb-parse-cricsheet`: convert raw Cricsheet data into canonical envelopes.
- `bbb-shared`: shared schemas, utilities, and deterministic identity contracts.
- `bbb-update-database`: load canonical envelopes through SQL, CSV, or JDBC adapters.
- `bbb-bulkupdate`: bulk-load exported CSV files into supported databases.

Module registration is in `settings.gradle.kts`; dependency versions are in
`gradle/libs.versions.toml`. Follow the existing conventions in the affected module.

## Build and verification

Run commands from the repository root using the Gradle wrapper and Java 21:

```bash
./gradlew clean check --no-daemon
./gradlew :bbb-update-database:test --no-daemon
./gradlew :bbb-parse-cricsheet:test --no-daemon
./gradlew :bbb-bulkupdate:test --no-daemon
```

The first command matches CI. Prefer the affected module's tests during iteration.
Kotlin tests use JUnit Platform. Some database integration tests require Docker/Testcontainers;
consult the database setup guide.

Use `.env.example` and `compose.yaml` for configuration guidance. Keep local
credentials and `.env` contents out of commits and logs.

## Architecture and domain constraints

- The relational loader accepts canonical envelopes. Cross-provider reconciliation
  belongs in a separate application.
- Keep source record IDs, canonical match IDs, public match IDs, content digests,
  and relational keys distinct.
- Preserve transactional match writes, rollback behavior, and success-only SQL
  publication when changing output adapters. Verify relevant adapter parity tests.
- Preserve support for MariaDB/MySQL, PostgreSQL, and SQLite where applicable.
  Generated SQL supports `mariadb`, `postgres`, and `sqlite`; the generated SQL
  dialect does not support `mysql`, although direct MySQL JDBC and migrations do.
- Keep stats-link exclusions and naming exceptions in their existing policy
  resources under `bbb-parse-cricsheet` and `bbb-shared`.

## Documentation

- `README.md`: architecture overview and quick start.
- `README-DEV.md`: developer commands and loader workflows; update it when tasks,
  parser options, adapters, migrations, or loading workflows change.
- `UBIQUITOUS_LANGUAGE.md`: domain terminology.
- `docs/architecture/`: application and database design.
- `docs/adr/`: architectural decisions.
- `docs/setup/SETUP-DB.md`: reproducible database setup.
- `bbb-bulkupdate/README.md`: bulk-loader options and examples.

<!-- jbcontext-instructions-start -->
## Semantic code search (jbcontext)

Start with `jbcontext search` before planning, editing, or exact search in unfamiliar
code when the right file, subsystem, implementation, or related test is unknown.
Do not start with grep, ripgrep, or find for semantic or exploratory discovery.

```bash
jbcontext search "Find match envelope parser logic"
jbcontext search -p bbb-update-database "Find the database loading transaction handling"
```

Use one focused, descriptive natural-language query per search. Paths passed to
`-p` must be relative to the project root. Once a relevant hit is found, switch to
direct file reads and exact searches. If authentication is unavailable, report
that limitation and inspect known documentation and source files directly.

For broader or multi-step exploration, delegate to `context_explorer` rather than
performing repeated searches inline:

```text
spawn_agent(agent_type="context_explorer", fork_turns="none", message="<intent>")
```

The explorer is read-only and returns verified file/line references, inline code
snippets, and a confidence note. While it runs, read only already-known relevant
files or check the environment; do not duplicate its exploration. Always call
`wait_agent` after that independent work to receive its report. If discovery is
clearly multi-step, delegate immediately; otherwise try one CLI search first and
delegate if the result is insufficient.
<!-- jbcontext-instructions-end -->

# Ball by Ball

[![CI](https://github.com/kevinrjones/acs_ball_by_ball/actions/workflows/ci.yml/badge.svg)](https://github.com/kevinrjones/acs_ball_by_ball/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-3.5-087CFA?logo=ktor&logoColor=white)](https://ktor.io/)
[![Angular](https://img.shields.io/badge/Angular-19-DD0031?logo=angular&logoColor=white)](https://angular.dev/)
[![Gradle](https://img.shields.io/badge/Gradle-build-02303A?logo=gradle&logoColor=white)](https://gradle.org/)

Gradle multi-module applications for retrieving cricket ball-by-ball data,
normalizing it into canonical match envelopes, loading a relational database, and serving
match search and scoresheet APIs through a Ktor backend and Angular web UI.

| | |
| --- | --- |
| **Repository** | [`kevinrjones/acs_ball_by_ball`](https://github.com/kevinrjones/acs_ball_by_ball) |
| **Root project** | `BallByBall` |
| **Default web port** | `8080` |
| **Default API port** | `8081` |
| **Database dialects** | MariaDB / MySQL, PostgreSQL, SQLite |

## Contents

- [What this repository contains](#what-this-repository-contains)
- [Data pipeline](#data-pipeline)
- [Modules](#modules)
- [Tech stack](#tech-stack)
- [Match identity and public URLs](#match-identity-and-public-urls)
- [API surface](#api-surface)
- [Quick start](#quick-start)
- [Docker Compose](#docker-compose)
- [CI/CD](#cicd)
- [Documentation map](#documentation-map)
- [Design notes](#design-notes)

## What this repository contains

- Command-line tools to download Cricsheet archives and player registers
- A Cricsheet adapter that emits source and canonical match envelopes
- A relational loader that writes SQL scripts, CSV files, or JDBC inserts, with
  transactional match writes across all output adapters
- A read-only Ktor REST API over normalized match data
- A Ktor-hosted, feature-sliced Angular SPA with OIDC BFF login and API proxying
- Shared contracts, tiny types, Flyway migrations, and architecture docs

## Data pipeline

```text
bbb-get-cricsheet-data
        │
        ▼
  Raw Cricsheet JSON + people/names CSV
        │
        ▼
bbb-parse-cricsheet
        │
        ▼
  parsed/ normalized envelopes
        │
        ├─ current: single-source canonicalizer
        └─ future: multi-provider reconciliation app
        │
        ▼
  CanonicalMatchEnvelope
        │
        ▼
bbb-update-database
        │
        ▼
  Relational match data (MariaDB / PostgreSQL / SQLite, SQL, or CSV)
        │
        ▼
bbb-api  ──proxied by──▶  bbb-web (Angular)
```

Relational loading accepts **canonical envelopes only**. Cross-provider
reconciliation is intentionally a separate future application.

`bbb-parse-cricsheet` writes normalized envelopes below its parsed output
directory whenever conversion succeeds, including when stats lookup fails; in
that case `match.sourceCaId` remains `null`. Lookup failures are reported per
file, processing continues, and the command returns non-zero after the
accumulated error summary. Conversion or output failures are also reported and
do not stop remaining files. Expected stats-link outcomes are represented as
sealed values and mapped at the per-file boundary. Female aliases use raw
Cricsheet team names; stats-name suffixes and link exclusions are owned by
their policy resources, linked below.
Stats linking tries the source date with margin when present and then without;
if all exact-date attempts miss, it retries ±1 day then ±2 days (earlier before
later at each distance), trying margin before plain on each date. Ambiguous and
invalid-alias results stop the fallback.

Player and official linking first uses match-roster evidence. Unresolved names
can fall back to an exact stats `FullName` lookup across player and official
tables only when it yields one distinct stats ID; ambiguous exact names remain
unresolved.

The linker batches the primary date and all four alternate dates into one
exact-team lookup and, only when needed, one alias lookup; team IDs are
preloaded and date/margin priority is applied in memory. The CricketArchive
stats schema has no `Matches.MatchStartDate` index, so see the
[application architecture notes](docs/architecture/applications.md) for the
optional operator-managed composite index recommendation.
Match-link failures show the source alias key and include the stats team name
when policy changes it. Cricsheet input is decoded once into typed source DTOs
and explicitly mapped into the shared match contract. A durable-commit failure
rolls match participants back before the failure is propagated.

JDBC and CSV match writes share a strict `beginMatch` → prepare → durable commit
→ identity finalization protocol. SQL scripts use the same transaction machinery
for staging and are journal-published only after a successful import; closing an
incomplete script abandons its staged output. Generated SQLite scripts are
applied and read back in the adapter parity tests; generated MariaDB and
PostgreSQL schemas are applied in Testcontainers to verify their tables, indexes,
alias uniqueness, and preservation of operator aliases on reapplication.
Generated rebuild SQL also seeds the 48 baseline Cricsheet team-name aliases
from the initial migrations, while keeping additional operator aliases intact.

The authoritative stats-link exclusion data is versioned in
[`bbb-parse-cricsheet/src/main/resources/policies/`](bbb-parse-cricsheet/src/main/resources/policies/).
Women's competition and team-name naming exceptions are maintained in
[`bbb-cli-shared/src/main/resources/policies/`](bbb-cli-shared/src/main/resources/policies/).

## Modules

| Module | Type | Responsibility |
| --- | --- | --- |
| `bbb-get-cricsheet-data` | CLI | Download raw Cricsheet JSON archives and player registers |
| `bbb-parse-cricsheet` | CLI | Convert raw Cricsheet JSON into source/canonical envelopes |
| `bbb-cli-shared` | Library | Shared CLI match schema and deterministic identity contracts |
| `bbb-update-database` | CLI | Load canonical envelopes into normalized SQL, CSV, or JDBC output |
| `bbb-shared` | Library | Shared HTTP contracts and validated tiny types |
| `bbb-api` | Service | Read-only relational REST API (default port `8081`) |
| `bbb-web` | Service | Feature-sliced Angular SPA, OIDC BFF, and API proxy (default port `8080`) |

Module registration lives in `settings.gradle.kts`. Dependency versions live in
`gradle/libs.versions.toml`.

## Tech stack

| Area | Choices in this repo |
| --- | --- |
| Language / build | Kotlin, Java 21 toolchain, Gradle |
| HTTP services | Ktor |
| Frontend | Angular 19 SPA hosted by `bbb-web` |
| Functional types / validation | Arrow (`Either`, boundary parsing) |
| Serialization | `kotlinx.serialization` |
| Persistence access | jOOQ (API), JDBC adapters (loader) |
| Schema migration | Flyway (`bbb-update-database/migrations`) |
| Databases | MariaDB / MySQL, PostgreSQL, SQLite |
| Auth | JWT on the API; OIDC BFF sessions on the web app |
| Local runtime | Docker Compose (`compose.yaml`) |
| CI | GitHub Actions (`.github/workflows`) |

Generated SQL scripts accept `--database mariadb`, `postgres`, or `sqlite`;
`--database mysql` is rejected because the generated MariaDB dialect is not
MySQL-8-safe. This does not restrict Flyway migrations under
`bbb-update-database/migrations/mysql/` or direct `jdbc:mysql:` output. CSV
output refuses non-empty target directories unless `--force` is supplied.
The MySQL, PostgreSQL, and SQLite migration directories each contain a single
complete `1__initial_tables.sql`, including the unique team-alias triple key;
see [database architecture](docs/architecture/database.md) for cutover details.

## Match identity and public URLs

Matches use layered deterministic identifiers. They are not interchangeable.

| Identifier | Purpose | Exposed in URLs? |
| --- | --- | --- |
| `sourceRecordId` | Provider-scoped UUIDv5 for one source record | No |
| `rawContentDigest` | SHA-256 of exact downloaded bytes; revision evidence only | No |
| `canonicalMatchId` | Stable UUID of the reconciled/canonical match | No |
| `publicMatchId` | Ten-digit URL identifier derived from the canonical UUID | Yes |
| `matchKey` | Generated internal relational key for SQL joins | No |

Public scoresheet URLs look like:

```text
/api/matches/{publicMatchId}/scoresheet
```

`publicMatchId` is deterministic and unique-constrained. Collisions fail loudly
rather than probing another value.

For generation details, clash probability, and recovery guidance, see:

- **[Generating match keys](docs/GENERATING_KEYS.md)**

Related decisions:

- [ADR 0003: Deterministic source and canonical match identity](docs/adr/0003-deterministic-match-identity.md)
- [ADR 0004: Deterministic public match identifiers](docs/adr/0004-deterministic-public-match-identifiers.md)

## API surface

Authenticated match routes (JWT bearer):

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/matches` | Recent matches (`days` / `limit`) |
| `GET` | `/api/matches/search` | Paginated match search |
| `GET` | `/api/matches/{publicMatchId}/scoresheet` | Match scoresheet |

Operational routes:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Database health |
| `GET` | `/api/heartbeat/alive` | Liveness heartbeat |

JSON request/response/error contracts are defined in `bbb-shared`.

## Quick start

### Prerequisites

- Java 21
- Docker (Compose stack and optional dual-database test setup)
- MariaDB client tools when loading SQL into MariaDB
- A Cricsheet working directory for download/parse/load workflows

### Clone and verify

```bash
git clone git@github.com:kevinrjones/acs_ball_by_ball.git
cd acs_ball_by_ball

./gradlew clean check --no-daemon
```

### Common developer workflows

Full command examples for retrieval, parsing, relational loading, API, and web
are in:

- **[README-DEV.md](README-DEV.md)**

Database container and migration setup:

- **[docs/setup/SETUP-DB.md](docs/setup/SETUP-DB.md)**

Typical local data path (details and flags in the developer runbook):

```bash
# 1. Download raw Cricsheet archives + registers
./gradlew :bbb-get-cricsheet-data:run --no-daemon \
  --args="--base-directory /path/to/data --data-directory cricsheet"

# 2. Normalize into canonical envelopes
./gradlew :bbb-parse-cricsheet:run --no-daemon --args="..."

# 3. Load relational output (SQL / CSV / JDBC)
./gradlew :bbb-update-database:run --no-daemon --args="..."

# 4. Run API and web against a migrated database
./gradlew :bbb-api:run --no-daemon
./gradlew :bbb-web:run --no-daemon
```

### Environment defaults

Copy `.env.example` when using Compose:

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_NAME` | `acs_ball_by_ball` | Database name |
| `DB_USER` | `ballbyball` | Application DB user |
| `DB_PASSWORD` | required | Application DB password |
| `DB_ROOT_PASSWORD` | required | MariaDB root password |
| `DB_PORT` | `3306` | Host MariaDB port |
| `API_PORT` | `8081` | Host API port |
| `WEB_PORT` | `8080` | Host web port |
| `DATA_DIR` | `./data` | Data mount for the updater container |
| `JWT_JWKS_URL` | required | API signing-key endpoint |
| `JWT_ISSUER` | required | API token issuer |
| `JWT_AUDIENCE` | required | API token audience |
| `OIDC_AUTHORITY` | required | BFF identity-provider authority |
| `OIDC_CLIENT_ID` | required | BFF OIDC client ID |
| `OIDC_CLIENT_SECRET` | required | BFF OIDC client secret |
| `OIDC_REDIRECT_URI` | required | BFF callback URI |
| `OIDC_POST_LOGOUT_REDIRECT_URI` | required | BFF post-logout URI |
| `OIDC_REGISTRATION_URL` | required | Account registration URL |

The database passwords, JWT settings, and OIDC settings are required by
Compose; fill them in locally or through a secret manager before starting it.
Do not commit real secrets. Prefer environment variables over hard-coded
credentials in source files.

For local `bbb-parse-cricsheet` runs, copy
`bbb-parse-cricsheet/.env.example` to `bbb-parse-cricsheet/.env` and set the
stats and ball-by-ball database passwords. The CLI loads that file when started
from the repository root (or `.env` from the current directory otherwise).
Reference the keys by name with `--stats-password-environment-variable
STATS_DB_PASSWORD` and `--bbb-password-environment-variable BBB_DB_PASSWORD`;
OS environment values take precedence over `.env` values. The local `.env` is
ignored by Git.

## Docker Compose

`compose.yaml` starts:

- MariaDB with init scripts from `docker/mariadb/init/`
- `bbb-api` on port `8081`
- `bbb-web` on port `8080`
- `bbb-update-database` on demand via the `tools` profile

```bash
docker compose up -d --build
```

- Web UI: `http://localhost:8080`
- API: `http://localhost:8081`

Run the updater container after placing data under `./data` (or `DATA_DIR`):

```bash
docker compose run --rm update-database \
  --outputType DATABASE \
  --baseDirectory /data \
  --playerRegistry people.csv \
  --connectionString jdbc:mariadb://mariadb:3306/acs_ball_by_ball \
  --userName ballbyball \
  --password "$DB_PASSWORD"
```

Stop / reset:

```bash
docker compose down
docker compose down -v   # also remove the MariaDB volume
```

## CI/CD

Workflows live in `.github/workflows/`:

| Workflow | Trigger | What it does |
| --- | --- | --- |
| `ci.yml` | PRs and pushes to `main`, version tags, manual | `./gradlew clean check --no-daemon` |
| `build-bbb-api.yml` | Version tags / manual | Build/check API; push multi-arch images on `v*.*.*` |
| `build-bbb-web.yml` | Version tags / manual | Build/check web; push images on version tags |
| Reusable workflows | Called by the above | Gradle setup and Docker publish helpers |

Composite actions under `.github/workflows/actions/` provide JDK setup, Gradle
execution, and Docker push steps.

## Documentation map

| Document | Contents |
| --- | --- |
| [README-DEV.md](README-DEV.md) | Developer runbook and command-line workflows |
| [docs/GENERATING_KEYS.md](docs/GENERATING_KEYS.md) | Match key generation, collision risk, and recovery |
| [docs/architecture/applications.md](docs/architecture/applications.md) | Module boundaries and runtime flows |
| [docs/architecture/database.md](docs/architecture/database.md) | Relational schema and replay cutover |
| [UBIQUITOUS_LANGUAGE.md](UBIQUITOUS_LANGUAGE.md) | Shared domain, contract, identity, and security vocabulary |
| [docs/setup/SETUP-DB.md](docs/setup/SETUP-DB.md) | Local database container setup |
| [docs/project_memory.md](docs/project_memory.md) | Shipped work, decisions, and gotchas |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [docs/sprints/](docs/sprints/) | Sprint task notes |

### Architecture decision records

| ADR | Topic |
| --- | --- |
| [0001](docs/adr/0001-historical-match-search.md) | Historical match search |
| [0002](docs/adr/0002-cricsheet-data-cli.md) | Cricsheet data CLI |
| [0003](docs/adr/0003-deterministic-match-identity.md) | Deterministic source and canonical identity |
| [0004](docs/adr/0004-deterministic-public-match-identifiers.md) | Deterministic public match identifiers |
| [0005](docs/adr/0005-relational-database-schema.md) | Normalized relational database schema |
| [0006](docs/adr/0006-persistent-team-name-aliases.md) | Persistent operator-owned team aliases |
| [0042](docs/adr/0042-relational-match-session-and-sinks.md) | Relational match session and output sinks |
| [0043](docs/adr/0043-relational-session-jdbc-identity-store.md) | Single write pipeline with JDBC identity store |
| [0050](docs/adr/0050-stats-match-date-heuristic-lookup.md) | Sequential stats match date fallback |

## Design notes

- Provider adapters emit source identity plus a raw-content digest.
- `bbb-parse-cricsheet` derives Cricsheet identity from the safe basename stem,
  not the full path.
- Relational loading accepts **canonical envelopes only**.
- JDBC, SQL-script, and CSV writes share `RelationalMatchSession`; adapter-specific
  identity stores and sinks handle live database keys or output serialization.
- Cross-provider reconciliation is intentionally a separate future application.
- `publicMatchId` is deterministic and unique-constrained; collisions fail
  loudly rather than probing another value.
- Internal relational joins continue to use generated `matchKey` values.
- API feature code is organized in vertical slices under
  `feature.<name>` (`presentation` / `domain` / `data`).

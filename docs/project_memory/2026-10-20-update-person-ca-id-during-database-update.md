# Update Person CA ID During Database Update

## Title
Update Person CricketArchive ID during database update

## Date/time completed
2026-10-20 09:30

## What was shipped
- Enhanced `MatchIdentityStore` and `IdentityCore` to track person `ca_id` values in a transaction-aware manner (`peopleCaId`).
- Updated `JdbcIdentityStore` to fetch and record existing `ca_id` values when querying person entities from the database.
- Updated `RelationalMatchSession.upsertPerson` to detect when an existing person has `ca_id = 0` and a non-zero `caId` is provided, emitting an `UPDATE people SET ca_id = ? WHERE id = ?` and updating the in-memory identity store.
- Updated `CsvOutputState` to buffer `people` rows in memory, apply `people` table updates, and write the updated `people.csv` upon `complete()`.
- Updated `Translate.kt` (`getPlayers` and `getOfficials`) to pass matching `caId` from the registry to `Person`.
- Added unit and integration tests across `RelationalMatchSessionTest`, `CsvOutputAdapterTest`, `SqlScriptOutputAdapterTest`, and `DatabaseTest`.

## Key decisions
- When a person already exists in the identity store or database with `ca_id == 0` and subsequent match parsing discovers a non-zero `peopleCaId`, the system updates the record's `ca_id`.
- If a person already has a non-zero `ca_id`, redundant updates are avoided.
- In `CsvOutputState`, `people` rows are tracked in-memory (`peopleRows`) similar to `matches` and `innings` so that updates can be applied prior to flushing `people.csv` on publication.

## Gotchas
- When bootstrapping people using `people.csv` with zero CA IDs prior to importing matches, `people` records previously retained the zero CA ID because `upsertPerson` exited early if the key already existed. Emitting an explicit update resolves this.

## Test coverage areas
- `RelationalMatchSessionTest`: Emitting updates when person CA ID changes from 0 to non-zero, and omitting duplicate updates.
- `CsvOutputAdapterTest`: Verifying CSV output contains updated `ca_id` when person was initially inserted with 0.
- `SqlScriptOutputAdapterTest`: Verifying SQL script contains `UPDATE people SET ca_id = ...` when person CA ID changes.
- `DatabaseTest`: Pre-seeding people with `writeAllPeople` and verifying end-to-end match parsing updates player CA IDs in both SQL and CSV outputs.

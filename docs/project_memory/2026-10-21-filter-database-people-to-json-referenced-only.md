# Filter Database People Insertions to JSON-Referenced Persons Only

### Title
Filter Database People Insertions to JSON-Referenced Persons Only

### Date/time completed
2026-10-21 07:32

### What was shipped
- Removed the upfront pre-population of all players from the CSV player registry into the `people` table.
- Filtered `people` inserts across SQL scripts, JDBC database updates, and CSV exports to only include players and officials referenced in match JSON files.
- Maintained the CSV player registry as an in-memory lookup map to enrich CricketArchive IDs (`ca_id`) for JSON-referenced persons when `ca_id` is not present or 0 in the JSON match registry.
- Added comprehensive unit and integration tests verifying that players appearing only in CSV files are omitted from the database while players in JSON files are inserted with enriched `ca_id`.

### Key decisions
- The CSV player registry is passed to `Database` as an in-memory lookup map rather than bulk-inserted into the database upfront.
- When match persons are upserted from JSON match data, if the person record has `ca_id = 0`, `Database` falls back to the in-memory CSV registry mapping.

### Gotchas
- `players.csv` remains required by CLI arguments to provide fallback `ca_id` values, but its entries are no longer dumped wholesale into target database tables or CSV outputs.

### Test coverage areas
- `ApplicationTest`: verifying only JSON-referenced players are included in generated SQL scripts and `people.csv` outputs.
- `DatabaseTest`: verifying match processing only writes JSON-referenced persons with registry fallback for `ca_id`.

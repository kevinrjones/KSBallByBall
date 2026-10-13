# ADR 0006: persistent source team-name aliases

## Status

Accepted

## Context

Cricsheet and the CricketArchive stats database describe the same matches, but
their team names are not always identical. The parser must be able to reuse a
known source-name correction across matches without changing the stats database,
which is the source of truth for canonical team and match identities.

## Decision

- Store reusable team-name aliases in the ball-by-ball database because they
  describe the integration between the two sources.
- Identify the source with `source_system`; a `source_team_name` may have
  multiple targets within that system because the same source name can refer to
  different stats teams in different match contexts.
- For Cricsheet, use the provider namespace UUID
  `f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b` as `source_system`.
- Store the stable CricketArchive `Teams.TeamId` in `stats_team_id`, rather than
  the stats database's local `Teams.Id`.
- Validate the target against the stats `Teams` table when parsing; do not add a
  cross-database foreign key.
- Keep aliases manual and fail fast when a match is missing, ambiguous, or an
  alias target cannot be resolved.
- Keep exact team-name matching as the first lookup path, and use aliases only
  when the exact lookup cannot resolve the match.

## Consequences

The parser needs a read-only ball-by-ball database connection in addition to its
stats database connection. Alias mappings survive future parses and are not
coupled to local stats surrogate keys, while operators remain responsible for
adding and reviewing mappings before retrying a failed parse.

The alias table is intentionally integration data and has no foreign key into
the stats database. A rebuilt or corrected stats database must still contain a
unique row for every referenced `Teams.TeamId` before the alias can be used.
When a source name has multiple targets, the parser validates every target and
tries individual and combined candidates in deterministic order.
The generated SQL-script bootstrap creates `team_name_aliases` and its target
index only when absent; it does not drop or recreate the table, so operator-owned
mappings survive repeated schema-script applies. Other match-data tables retain
their existing destructive rebuild behavior.

Each dialect's `1__initial_tables.sql` migration creates `team_name_aliases`
with `IF NOT EXISTS`, enforces the unique alias triple, and does not drop or
recreate the table. The baseline migrations remain rebuild-oriented for the
other match-data tables and should not be manually replayed against a populated
database. New schema changes must be additive, and mappings are still inserted
and reviewed separately by operators. Before a rebuild cutover, operators must
export the three alias columns outside the database, create a fresh target and
apply the current dialect migration, restore the mappings, and verify
the restored row count before replaying canonical envelopes. Exact duplicate
triples must be reviewed before restore; distinct stats-team targets for one
source name are valid.

## Rejected alternatives

- Storing aliases in the stats database would make integration corrections part
  of the source-of-truth schema.
- Storing `Teams.Id` would couple aliases to one stats database's local
  surrogate-key lifetime.
- Fuzzy or automatic alias creation could persist an incorrect mapping without
  operator review.
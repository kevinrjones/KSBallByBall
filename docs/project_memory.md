# Project memory

## Current task entry

See [`project_memory/2026-10-16-duplicate-source-ca-id-warning.md`](project_memory/2026-10-16-duplicate-source-ca-id-warning.md) for the current task's shipped changes, decisions, gotchas, and test coverage. The preceding entry is [`project_memory/2026-10-15-stats-match-lookup-batching.md`](project_memory/2026-10-15-stats-match-lookup-batching.md), followed by [`project_memory/2026-10-15-stats-match-date-heuristic-plus-minus-two.md`](project_memory/2026-10-15-stats-match-date-heuristic-plus-minus-two.md), followed by [`project_memory/2026-10-15-stats-match-date-heuristic.md`](project_memory/2026-10-15-stats-match-date-heuristic.md), [`project_memory/2026-10-13-database-alias-seeds.md`](project_memory/2026-10-13-database-alias-seeds.md), [`project_memory/2026-10-13-single-parsed-output.md`](project_memory/2026-10-13-single-parsed-output.md), [`project_memory/2026-10-12-parse-cricsheet-dotenv.md`](project_memory/2026-10-12-parse-cricsheet-dotenv.md), [`project_memory/2026-10-12-fixme-p2-resolution.md`](project_memory/2026-10-12-fixme-p2-resolution.md), [`project_memory/2026-10-12-fixme-p2-docs-source-system.md`](project_memory/2026-10-12-fixme-p2-docs-source-system.md), [`project_memory/2026-10-12-open-fixme-p1-p2.md`](project_memory/2026-10-12-open-fixme-p1-p2.md), [`project_memory/2026-10-12-fixme-p2-p3.md`](project_memory/2026-10-12-fixme-p2-p3.md), [`project_memory/2026-10-11-fixme-p2-closure.md`](project_memory/2026-10-11-fixme-p2-closure.md), [`project_memory/2026-10-10-fixme-p1-p2-remediation.md`](project_memory/2026-10-10-fixme-p1-p2-remediation.md), and [`project_memory/2026-10-09-write-path-unification.md`](project_memory/2026-10-09-write-path-unification.md).

The historical log below is retained for traceability. At more than 5,700 lines it should be archived by dated task rather than extended with future long-form completion reports.

# Project Memory

## Task: Fix CSV journal durability P1

### Title

Force CSV publication journal directory metadata

### Date/time completed

2026-10-09 07:27

### What was shipped

- CSV publication journal replacement now forces the journal's parent directory
  after the durable temporary-file rename.
- Added an injectable directory-sync seam and regression coverage asserting that
  each publication journal phase synchronizes the containing directory.

### Key decisions

- Reused the existing SQL replacement durability pattern: force journal file
  contents, replace the journal, then force its parent directory where the file
  system supports directory synchronization.
- Kept custom journal writers and existing publication recovery semantics
  unchanged.

### Gotchas

- Directory synchronization is best-effort on file-system providers that do not
  support opening directories for `FileChannel.force`.

### Test coverage areas

- CSV publication journal directory synchronization across all three journal
  phases.
- Existing CSV publication rollback and restart-recovery behavior.
- Focused CSV tests passed; full project check is run as final verification.

## Task: Fix alias and SQL replacement P1s

### Title

Prevent ambiguous alias links and destructive SQL recovery

### Date/time completed

2026-10-09 07:17

### What was shipped

- Stats match linking now evaluates every alias-derived candidate and rejects
  conflicting CricketArchive IDs as ambiguous instead of selecting the first.
- SQL replacement recovery now preserves a target when a `PREPARED` journal has
  no backup, while active replacement failures still remove a partially moved
  new file when no previous target existed.
- Added regression tests for conflicting alias candidates and recovery after a
  staged file has moved without a backup.

### Key decisions

- Exact team-name lookup remains authoritative and alias candidates are only
  aggregated after exact lookup fails.
- Crash recovery is conservative when journal evidence is incomplete; the
  synchronous rollback path retains its stronger cleanup guarantee.

### Gotchas

- A no-backup journal with an existing target is treated as potentially
  published output and is not deleted during startup recovery.

### Test coverage areas

- Conflicting alias candidates and existing stats-linking ambiguity behavior.
- SQL replacement fallback, active rollback, and no-backup restart recovery.
- Full `./gradlew clean check --no-daemon` passed.

## Task: Consolidate migrations and close adapter rollback gaps

### Title

Consolidate dialect schemas and harden transactional file adapters

### Date/time completed

2026-10-09 06:51

### What was shipped

- Transactional map and set views now track mutations made through entries,
  keys, values, and iterators so rollback restores the complete prior state.
- SQL replacement journals are written through forced temporary files and an
  atomic replacement before the parent directory is forced where supported.
- CSV temporary, backup, source, and output directory cleanup now fails loudly
  instead of silently retaining stale files.
- MySQL, PostgreSQL, and SQLite each now use one consolidated
  `1__initial_tables.sql` containing aliases and source-match uniqueness; the
  obsolete follow-up migration files were removed.

### Key decisions

- The consolidated initial schemas express the final clean-database contract
  directly rather than carrying the inert alias-index removal migration.
- Mutable collection views remain compatible with the `MutableMap` and
  `MutableSet` contracts, but all mutation paths pass through transaction
  tracking.

### Gotchas

- The consolidated migration is intended for the branch's rebuild/fresh-schema
  workflow; already-applied production migration histories must not be edited
  without an explicit database migration plan.

### Test coverage areas

- Transactional collection view mutation and rollback.
- SQLite, MySQL, and PostgreSQL consolidated migration constraints and alias
  behavior, subject to Docker availability for container-backed tests.
- SQL replacement and CSV publication recovery paths.

## Task: Make file publication and import rollback scalable

### Title

Restore failed publications and replace full import snapshots with deltas

### Date/time completed

2026-10-08 20:07

### What was shipped

- SQL fallback replacement now records whether the target existed and removes a
  newly moved output when journal persistence fails without a backup.
- CSV bundle publication restores the previous bundle when the journal fails
  after the new directory has been moved into place.
- File-backed relational state and source-ID state now use first-write undo
  deltas instead of copying all accumulated maps and delivery keys per match.

### Key decisions

- A completed adapter commit finalizes collection undo logs only after the
  external transaction or bundle publication succeeds.
- CSV publication rollback is distinct from crash recovery: a synchronous
  failure restores the prior bundle, while restart recovery retains its
  existing journal-phase semantics.

### Gotchas

- Failure-injection tests must fail journal advancement after the source bundle
  has moved; failures before replacement do not cover the consistency defect.
- JDBC source-ID undo logs are finalized only after the database commit.

### Test coverage areas

- SQL fallback with and without a previous output file, including interrupted
  journal recovery.
- CSV journal failure after publication, bundle cleanup, source-ID rollback,
  and existing adapter transaction paths.
- Full `./gradlew clean check --no-daemon` passed.

## Task: Resolve parser and database P2 quality findings

### Title

Unify adapter transactions and harden parser operations

### Date/time completed

2026-10-08 16:18

### What was shipped

- Shared relational state snapshots and a non-reentrant transaction coordinator
  now serve the JDBC, SQL-script, and CSV adapters.
- SQL-script replacement uses a journaled backup fallback with restart recovery;
  parser file processing and stats retry coverage were split into focused seams.
- Parser database passwords are referenced by environment-variable name rather
  than passed as plaintext command-line arguments.
- Added MariaDB repository integration coverage, transaction lifecycle tests,
  and documentation for nullable CricketArchive enrichment and ambiguity rules.

### Key decisions

- File adapters retain adapter-specific staged output in their snapshots while
  sharing the common relational model and rollback coordinator.
- SQL replacement restores the prior file when a fallback publication is
  interrupted; atomic move remains preferred when supported.
- MariaDB integration tests skip only when Docker is unavailable locally and run
  the production-style query assertion in container-enabled environments.

### Gotchas

- Parser invocations must use `--stats-password-environment-variable` and
  `--bbb-password-environment-variable`; the named variables must be exported.
- The MariaDB integration test requires Docker to execute rather than skip.

### Test coverage areas

- Full `./gradlew clean check --no-daemon` passed; the MariaDB test was skipped
  locally because Docker was unavailable.
- SQL fallback recovery, CSV/JDBC/SQLite rollback and reconciliation, parser
  path validation, and margin retry behavior are covered.

## Task: Preserve Women's T20 Challenge Match team names

### Title

Do not suffix female Women's T20 Challenge Match team names

### Date/time completed

2026-10-06 09:48

### What was shipped

- Added `Women's T20 Challenge Match` to the female competition exceptions used
  by `StatsMatchLinker`.
- Added a SQLite-backed regression proving that both source team names are
  matched without the standard ` Women` suffix.

### Key decisions

- The exception is an exact event-name match and applies only to female matches;
  other events retain the normal suffix behavior.

### Gotchas

- A differently formatted event name will not receive this exception.

### Test coverage areas

- Female `Women's T20 Challenge Match` linking against unsuffixed stats team
  names.

## Task: Try duplicate source-team aliases during stats linking

### Title

Support multiple persistent alias targets for one source team name

### Date/time completed

2026-10-06 09:29

### What was shipped

- `JdbcTeamNameAliasRepository` now returns every alias target for a source
  system and source team name.
- `JdbcMatchLinker` validates each target and tries all individual and combined
  alias candidates in deterministic target order.
- Alias schemas no longer enforce unique `(source_system, source_team_name)`;
  compatibility migrations remove the old constraint from existing databases.

### Key decisions

- Duplicate source names are valid integration data because one source name can
  map to different stats teams in different match contexts.
- Exact matching remains first, followed by individual aliases and then all
  combinations of aliases; duplicate stats match rows still use the first ID.

### Gotchas

- Apply `3__allow_duplicate_team_name_aliases.sql` to databases created with the
  earlier unique alias schema before inserting duplicate source names.
- Every alias target must still resolve to exactly one stats `Teams.TeamId`.

### Test coverage areas

- A SQLite linker regression inserts two targets for one source name and proves
  that a later target can resolve the match.
- Generated SQLite schema accepts duplicate source names while retaining the
  `stats_team_id` index.

## Task: Preserve the opponent suffix for The Blaze matches

### Title

Leave only The Blaze unsuffixed in female matches

### Date/time completed

2026-10-06 09:14

### What was shipped

- Female matches containing the exact source team name `The Blaze` now skip the
  standard ` Women` suffix only for that team during stats linking.
- Opposing teams continue to receive the standard suffix when their stats name
  requires it.
- Updated the SQLite-backed linker regression to require an opponent named
  `Team B Women`.

### Key decisions

- The exception is applied to each team name independently: only the exact
  source name `The Blaze` bypasses suffixing.
- The existing event-specific exceptions and normal female suffix behavior for
  other matches remain unchanged.

### Gotchas

- The exception uses the exact source team name `The Blaze`; differently named
  or formatted teams continue through the normal suffix rule.

### Test coverage areas

- Female `The Blaze` matches resolve with an unsuffixed `The Blaze` and a
  suffixed opponent.

## Task: Parse Nepal Premier League without stats lookup

### Title

Allow Nepal Premier League files to parse successfully without a stats representation

### Date/time completed

2026-10-05 16:36

### What was shipped

- `bbb-parse-cricsheet` now bypasses stats linking for the exact `Nepal Premier
  League` event name in all seasons.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- `Nepal Premier League` is an exact event-name exception because its matches
  have no stats representation.
- The normal conversion and output flow remains unchanged for bypassed matches.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- A parser regression verifies that `Nepal Premier League` skips the stats
  linker, writes the normalized file to `parsed`, leaves `not-parsed` empty, and
  sets `sourceCaId` to null.

## Task: Parse Major League Cricket 2023 without stats lookup

### Title

Allow Major League Cricket 2023 files to parse successfully without a stats representation

### Date/time completed

2026-10-05 16:30

### What was shipped

- `bbb-parse-cricsheet` now bypasses stats linking for `Major League Cricket`
  matches in season `2023`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The exception requires the exact event name and exact season; other Major
  League Cricket seasons continue through normal stats linking.
- The normal conversion and output flow remains unchanged for bypassed matches.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name or season will not receive the
  bypass.

### Test coverage areas

- A parser regression verifies that the 2023 event skips the stats linker,
  writes the normalized file to `parsed`, leaves `not-parsed` empty, and sets
  `sourceCaId` to null.

## Task: Parse Continental Cup without stats lookup

### Title

Allow Continental Cup files to parse successfully without a stats representation

### Date/time completed

2026-10-05 14:53

### What was shipped

- `bbb-parse-cricsheet` now bypasses stats linking for the exact `Continental
  Cup` event name in all seasons.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- `Continental Cup` is an exact event-name exception because no season was
  specified and the event has no stats representation across its occurrences.
- The normal conversion and output flow remains unchanged for bypassed matches.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- A parser regression verifies that `Continental Cup` skips the stats linker,
  writes the normalized file to `parsed`, leaves `not-parsed` empty, and sets
  `sourceCaId` to null.

## Task: Parse additional competitions without stats lookup

### Title

Allow additional competitions without stats representations to parse successfully

### Date/time completed

2026-10-05 14:39

### What was shipped

- `bbb-parse-cricsheet` now bypasses stats linking for `East Asia Cup` matches
  in seasons `2016/17` and `2018`, `ICC World Twenty20 Africa Region Qualifier
  C` in `2018/19`, `ICC World Cricket League Americas Region Division One
  Twenty20` in `2010`, and `ACC Twenty20 Cup` in `2014/15`.
- `Stan Nagaiah Trophy` matches bypass stats linking in all seasons.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- Season-specific exceptions require both an exact event name and exact season;
  other seasons continue through normal stats linking.
- `Stan Nagaiah Trophy` is an exact event-name exception because no season was
  specified and the event has no stats representation across its occurrences.
- The normal conversion and output flow remains unchanged for bypassed matches.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name or season will not receive a
  season-scoped bypass.

### Test coverage areas

- Parser regressions verify that each requested exception skips the stats linker,
  writes the normalized file to `parsed`, leaves `not-parsed` empty, and sets
  `sourceCaId` to null.
- Existing control coverage verifies that non-exempt seasons still attempt stats
  linking and preserve failed raw files.

## Task: Parse additional regional qualifiers without stats lookup

### Title

Allow ICC World Twenty20 Europe Region Qualifier C, Americas Sub Regional
Qualifier A, and Asia Region Qualifier B files to parse successfully

### Date/time completed

2026-10-05 14:29

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet events
  `ICC World Twenty20 Europe Region Qualifier C`, `ICC World Twenty20 Americas
  Sub Regional Qualifier A`, and `ICC World Twenty20 Asia Region Qualifier B`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The three events extend the parser's exact-name set for competitions without
  a stats representation, preserving the normal conversion and output flow.
- All other event names continue through the configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- Parser regressions verify that the stats linker is not called for each event,
  each file is written to `parsed`, `not-parsed` remains empty, and
  `sourceCaId` is null.

## Task: Parse Asian Games Men's Cricket Competition 2014/15 without stats lookup

### Title

Allow Asian Games Men's Cricket Competition files from season 2014/15 to parse successfully

### Date/time completed

2026-10-05 14:23

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact event
  `Asian Games Men's Cricket Competition` when the season is `2014/15`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The bypass is scoped to both the exact event name and season, preserving
  normal stats linking for the event in other seasons.
- The normal conversion and output flow remains unchanged for the bypassed
  matches.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name or season will not receive the
  bypass.

### Test coverage areas

- Parser regressions verify that the stats linker is not called for season
  `2014/15`, the file is written to `parsed`, `not-parsed` remains empty, and
  `sourceCaId` is null.
- A control regression verifies that another season still attempts stats
  linking and preserves the failed raw file.

## Task: Parse ICC World Twenty20 Europe Region Qualifiers without stats lookup

### Title

Allow ICC World Twenty20 Europe Region Qualifier A and B files to parse successfully

### Date/time completed

2026-10-05 14:20

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet events
  `ICC World Twenty20 Europe Region Qualifier A` and `ICC World Twenty20 Europe
  Region Qualifier B`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- Both events are part of the parser's exact-name set for competitions without
  a stats representation, preserving the normal conversion and output flow.
- All other event names continue through the configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- Parser regressions verify that the stats linker is not called for either
  qualifier, each file is written to `parsed`, `not-parsed` remains empty, and
  `sourceCaId` is null.

## Task: Parse ICC World Twenty20 East Asia-Pacific Region Qualifiers without stats lookup

### Title

Allow ICC World Twenty20 East Asia-Pacific Region Qualifier A and B files to parse successfully

### Date/time completed

2026-10-05 14:14

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet events
  `ICC World Twenty20 East Asia-Pacific Region Qualifier A` and `ICC World
  Twenty20 East Asia-Pacific Region Qualifier B`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- Both events are part of the parser's exact-name set for competitions without
  a stats representation, preserving the normal conversion and output flow.
- All other event names continue through the configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- Parser regressions verify that the stats linker is not called for either
  qualifier, each file is written to `parsed`, `not-parsed` remains empty, and
  `sourceCaId` is null.

## Task: Parse ICC World Twenty20 Africa Region Qualifier B without stats lookup

### Title

Allow ICC World Twenty20 Africa Region Qualifier B files to parse successfully

### Date/time completed

2026-10-05 14:09

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet event
  `ICC World Twenty20 Africa Region Qualifier B`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The event is part of the parser's exact-name set for competitions without a
  stats representation, preserving the normal conversion and output flow.
- All other event names continue through the configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- A parser regression verifies that the stats linker is not called, the file is
  written to `parsed`, `not-parsed` remains empty for it, and `sourceCaId` is
  null.

## Task: Parse ICC World Twenty20 Africa Region Qualifier A without stats lookup

### Title

Allow ICC World Twenty20 Africa Region Qualifier A files to parse successfully

### Date/time completed

2026-10-05 14:06

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet event
  `ICC World Twenty20 Africa Region Qualifier A`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The event is part of the parser's exact-name set for competitions without a
  stats representation, preserving the normal conversion and output flow.
- All other event names continue through the configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- A parser regression verifies that the stats linker is not called, the file is
  written to `parsed`, `not-parsed` remains empty for it, and `sourceCaId` is
  null.

## Task: Parse ICC World Twenty20 Asia Region Qualifier A without stats lookup

### Title

Allow ICC World Twenty20 Asia Region Qualifier A files to parse successfully

### Date/time completed

2026-10-05 14:01

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet event
  `ICC World Twenty20 Asia Region Qualifier A`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The event is part of the parser's exact-name set for competitions without a
  stats representation, preserving the normal conversion and output flow.
- All other event names continue through the configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- A parser regression verifies that the stats linker is not called, the file is
  written to `parsed`, `not-parsed` remains empty for it, and `sourceCaId` is
  null.

## Task: Parse East Asia Cup 2016/17 without stats lookup

### Title

Allow the East Asia Cup 2016/17 files to parse successfully

### Date/time completed

2026-10-05 13:55

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet event
  `East Asia Cup` when its season is `2016/17`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The exception is constrained by both event name and season; other East Asia
  Cup seasons continue through normal stats linking.
- The bypass remains at the parser linking boundary so conversion,
  canonicalization, and output handling are unchanged.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed event or differently formatted season will not receive the bypass.

### Test coverage areas

- A parser regression verifies that the exact event and season skip the stats
  linker and are written to `parsed` with a null `sourceCaId`.
- A second regression verifies that another East Asia Cup season still invokes
  stats linking and is preserved under `not-parsed` when linking fails.

## Task: Parse Kenya T20 Quadrangular Series without stats lookup

### Title

Allow Kenya T20 Quadrangular Series files to parse successfully

### Date/time completed

2026-10-05 13:52

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet
  `event.name` `Kenya T20 Quadrangular Series`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The event is part of the parser's exact-name set for competitions without a
  stats representation, preserving the normal conversion and output flow.
- All other event names continue through the configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- A parser regression verifies that the stats linker is not called, the file is
  written to `parsed`, `not-parsed` remains empty for it, and `sourceCaId` is
  null.

## Task: Parse ICC World Cricket League Europe Region Division Two Twenty20 without stats lookup

### Title

Allow unsupported stats competition files to parse successfully

### Date/time completed

2026-10-05 13:46

### What was shipped

- `bbb-parse-cricsheet` now skips stats linking for the exact Cricsheet
  `event.name` `ICC World Cricket League Europe Region Division Two Twenty20`.
- These files are written to the parsed output with an empty nullable
  `sourceCaId` instead of being copied to `not-parsed`.

### Key decisions

- The exception is implemented at the parser linking boundary so the normal
  conversion, canonicalization, and parsed-output flow remains unchanged.
- The event name comparison is exact; all other events continue through the
  configured stats linker.

### Gotchas

- These matches intentionally have no CricketArchive `CaId` and will therefore
  remain unlinked when loaded into the ball-by-ball database.
- A renamed or differently formatted event name will not receive the bypass.

### Test coverage areas

- A parser regression verifies that the stats linker is not called, the file is
  written to `parsed`, `not-parsed` remains empty for it, and `sourceCaId` is
  null.

## Task: Accept duplicate stats match rows

### Title

Use the first stats match when duplicate rows are found

### Date/time completed

2026-10-05 09:17

### What was shipped

- Duplicate exact or alias-assisted stats matches now log a warning and use the
  first `CaId` ordered by `CaId`.
- Missing matches and invalid or ambiguous alias targets continue to fail.

### Key decisions

- Duplicate match rows are treated as a data-quality issue rather than a parse
  failure so the rest of the source files can be processed.
- The warning includes the lookup criteria and all duplicate `CaId` values.

### Gotchas

- The first result is deterministic by ascending `CaId`, not by database row
  insertion order.
- Duplicate rows may still require cleanup in the stats database; accepting the
  first result does not merge or modify those records.

### Test coverage areas

- Exact-name duplicate matches return the first `CaId`.
- Alias-assisted duplicate matches return the first `CaId`.

## Task: Preserve Rachael Heyhoe Flint Trophy team names

### Title

Skip the women’s team-name suffix for Rachael Heyhoe Flint Trophy matches

### Date/time completed

2026-10-04 17:52

### What was shipped

- Added Rachael Heyhoe Flint Trophy to the female competition exceptions used
  by stats match linking.
- Female Rachael Heyhoe Flint Trophy team names now remain unchanged instead
  of receiving a ` Women` suffix before the stats lookup.

### Key decisions

- The exception is keyed by the Cricsheet `event.name` value
  `Rachael Heyhoe Flint Trophy`, leaving the normal female naming rule
  unchanged for other competitions.

### Gotchas

- This applies to the `odms_json` competition naming convention and does not
  replace persistent aliases for other team-name mismatches.

### Test coverage areas

- Female Rachael Heyhoe Flint Trophy matches link successfully when stats team
  names omit the ` Women` suffix.

## Task: Preserve Charlotte Edwards Cup team names

### Title

Skip the women’s team-name suffix for CEC matches

### Date/time completed

2026-10-04 16:44

### What was shipped

- Added Charlotte Edwards Cup to the female competition exceptions used by
  stats match linking.
- Female CEC team names now remain unchanged instead of receiving a ` Women`
  suffix before the stats lookup.

### Key decisions

- The exception is keyed by the Cricsheet `event.name` value
  `Charlotte Edwards Cup`, leaving the normal female naming rule unchanged for
  other competitions.

### Gotchas

- This handles competition naming convention differences; it does not replace
  persistent aliases for other team-name mismatches.

### Test coverage areas

- Female Charlotte Edwards Cup matches link successfully when stats team names
  omit the ` Women` suffix.

## Task: Retry stats lookups without victory margin

### Title

Recover matches whose stats victory margin differs from Cricsheet

### Date/time completed

2026-10-04 16:27

### What was shipped

- Added a second stats-match lookup when the date, teams, and `Matches.HowMuch`
  criteria find no row; the retry keeps date and interchangeable team matching
  but omits the margin.
- Added a dedicated lookup-failure logger that reports the margin miss and any
  final failure to the console and `logs/lookup_failures.log`.
- Configured the lookup log with the existing parser rotation policy: 10 MB
  file size, 60-day history, and 20 GB total-size cap.

### Key decisions

- Only `StatsMatchNotFoundException` triggers the no-margin retry; ambiguous
  matches and invalid alias targets remain explicit failures.
- The existing per-file continuation and raw-file preservation behavior is
  unchanged when both lookup attempts fail.

### Gotchas

- A no-margin match is accepted only after the margin-constrained lookup logs a
  miss; this fallback is not fuzzy matching and does not create aliases.
- Lookup logs are emitted by the parser-specific logger and are not duplicated
  into `application.log`.

### Test coverage areas

- Exact match recovery after a victory-margin mismatch.
- Final no-margin failure message and existing ambiguity/alias-target failures.
- Parser Logback configuration for the dedicated console and rotating file
  appenders.

## Task: Separate parsed and not-parsed Cricsheet output

### Title

Continue parsing after per-file failures and preserve failed raw inputs

### Date/time completed

2026-10-04 15:05

### What was shipped

- Added the required `--not-parsed` parser directory alongside `--output` for
  successful normalized files.
- Changed per-file conversion, linking, and output failures to be logged and
  accumulated without stopping later files.
- Copied failed raw JSON files to the matching relative path below
  `--not-parsed` and logged a final accumulated error summary.

### Key decisions

- Failed inputs are copied rather than moved so the original raw archive remains
  available for correction and retry.
- A non-zero process result is retained when any file fails, even though the
  parser completes the rest of the batch.

### Gotchas

- `--not-parsed` is a required safe relative child directory of
  `--base-directory`; rerun the parser after correcting aliases or source data.

### Test coverage areas

- Generic conversion failures and missing stats matches continue to later files.
- Parsed output is separated from failed raw input, including mirrored nested
  paths and exact raw file contents.
- CLI parsing captures the additional not-parsed directory.

## Task: Persist team-name aliases for stats linking

### Title

Store reusable Cricsheet-to-stats team aliases in the ball-by-ball database

### Date/time completed

2026-10-04 14:29

### What was shipped

- Added `team_name_aliases` migrations for MySQL, PostgreSQL, and SQLite, plus
  generated relational-schema support with duplicate source names and an
  indexed stable stats-team target.
- Added alias-aware `JdbcMatchLinker` resolution using a second ball-by-ball
  JDBC connection, while preserving exact matching and `sourceCaId` output.
- Added parser options and runtime wiring for separate stats and ball-by-ball
  connections, with operator documentation for manual alias insertion and
  retrying failed parses.

### Key decisions

- The ball-by-ball database owns integration aliases; the stats database remains
  the source of truth and aliases target stable `Teams.TeamId` values.
- Exact matching runs first. Aliases are manual, reusable by source system and
  source team name, and are used only when exact matching finds no row; all
  targets for a source name are considered.
- Alias targets are validated at parse time and must resolve to exactly one stats
  team; no cross-database foreign key or automatic fuzzy mapping is used.
- Cricsheet aliases use provider namespace UUID
  `f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b` as `source_system`.

### Gotchas

- Parser invocations now require both `--stats-connection-string` and
  `--bbb-connection-string`; credentials are supplied through the corresponding
  optional username and password options and are not logged.
- Migrations `2__team_name_aliases.sql` and
  `3__allow_duplicate_team_name_aliases.sql` must be applied before manual alias
  inserts. An invalid or ambiguous target remains a fatal parse error.

### Test coverage areas

- Generated SQLite schema columns, duplicate source names, and target index.
- Exact, partial, two-sided reverse-order, missing, ambiguous, and invalid-target
  JDBC linker behavior.
- Parser argument capture for both database connection configurations.

## Task: Link parsed matches to CricketArchive stats

### Title

Resolve `source_ca_id` during Cricsheet parsing

### Date/time completed

2026-10-04 08:22

### What was shipped

- Added a JDBC stats matcher to `bbb-parse-cricsheet` using the two team names,
  match date, and victory margin, then writes the resolved `Matches.CaId` to
  `match.sourceCaId`.
- Propagated `sourceCaId` through `bbb-update-database` into
  `matches.source_ca_id` and added a unique constraint for the one-to-one link
  in all supported relational dialects.
- Added parser, JDBC lookup, and relational column-order tests plus CLI and
  architecture documentation for the required stats database connection.

### Key decisions

- Victory type is deliberately excluded from matching because the two systems
  use different victory-type vocabularies.
- Missing or ambiguous stats matches are logged and terminate parsing rather
  than producing an unlinked normalized match.

### Gotchas

- The parser now requires `--stats-connection-string`; credentials are passed
  with the optional `--stats-user-name` and `--stats-password` options.

### Test coverage areas

- Successful and missing JDBC match lookups, parser envelope linking, and
  relational `source_ca_id` column ordering.

## Task: Consolidate relational migrations

### Title

Use one initial migration per database dialect

### Date/time completed

2026-10-03 14:38

### What was shipped

- Merged the complete relational cutover schema into
  `1__initial_tables.sql` for MySQL, PostgreSQL, and SQLite.
- Removed obsolete warehouse and incremental identity migration files so each
  dialect now has one authoritative migration.
- Updated the SQLite migration test and database setup guidance to use the
  single migration and normalized table names.

### Key decisions

- The single initial migration retains the rebuild reset order and all
  relational identity, provenance, association, and foreign-key definitions.
- Existing databases remain backup-and-replay cutover targets rather than
  in-place data-copy targets.

### Gotchas

- Existing Flyway installations with the old migration history must be
  recreated or handled as an explicitly coordinated cutover; the consolidated
  files describe the fresh relational target.

### Test coverage areas

- SQLite migration coverage now applies `1__initial_tables.sql` and verifies
  warehouse-table removal and relational schema creation.

## Task: Replace warehouse schema with relational match data

### Title

Normalized relational database cutover and API/updater refactor

### Date/time completed

2026-10-03 14:25

### What was shipped

- Added a complete `1__initial_tables.sql` migration for MariaDB, PostgreSQL,
  and SQLite, replacing warehouse tables with normalized match, entity, event,
  association, and provenance tables.
- Updated JDBC, SQL-script, and CSV updater outputs to write relational tables,
  with match measures merged into `matches` and delivery measures merged into
  `deliveries`.
- Regenerated relational jOOQ bindings and updated match summary, search, and
  scoresheet readers without changing public HTTP contracts.
- Added ADR `0005-relational-database-schema.md` and refreshed database and
  application architecture documentation.

### Key decisions

- Existing databases are backup-first rebuild targets; canonical envelopes are
  replayed instead of translating warehouse surrogate keys in place.
- `matches.id`, `canonical_match_id`, and `public_match_id` remain distinct;
  source digests continue to reject changed provenance.
- Official and player assignments use `match_people`, while wicket and fielder
  relationships use explicit association tables.

### Gotchas

- Internal numeric IDs can change after replay; API and ingestion code must use
  canonical or public identities.
- Live integration tests require a MariaDB instance already populated with the
  relational schema; connection settings are configurable through test system
  properties or `TEST_DB_*` environment variables.

### Test coverage areas

- SQLite cutover validation covers legacy-table removal, all normalized tables,
  foreign-key integrity, and the complete `matches` column contract.
- Updater tests cover JDBC, SQL-script, CSV, identity/provenance, duplicate
  suppression, and relational column ordering; API production compilation and
  non-live tests pass.

## Task: Expert code review and ubiquitous language

### Title

Expert review of Kotlin, Angular, contracts, and application boundaries

### Date/time completed

2026-10-03 12:35

### What was shipped

- Added `UBIQUITOUS_LANGUAGE.md` defining the project vocabulary for source and
  canonical envelopes, deterministic identities, warehouse keys, HTTP
  contracts, feature slices, BFF authentication, and match operations.
- Added the glossary to the root documentation map in `README.md`.
- Reviewed `docs/architecture/applications.md` against the implementation; it
  already provides sufficient module, runtime, security, contract, and feature
  boundary detail, so it was intentionally left unchanged.

### Key decisions

- Keep `matchKey`, `publicMatchId`, `canonicalMatchId`, and `sourceRecordId`
  distinct in all documentation and code discussions.
- Treat a contract as the complete boundary agreement: wire shape, semantics,
  validation, status/error behavior, versioning, and identity meaning.
- Keep this task review-only for application behavior; findings are recorded
  for follow-up rather than changing production logic opportunistically.

### Gotchas

- The Compose deployment defaults the web host port to `8080`, while the
  standalone Ktor application defaults to `9999`; documentation should always
  state which execution mode it describes.
- Production configuration still has placeholder OIDC/database defaults, and
  JWT configuration merges configured audiences/scopes with permissive defaults;
  deployment hardening is required before treating those defaults as safe.

### Test coverage areas

- Static review covered API bootstrap/security/routes, shared contracts and
  tiny types, Angular routing/services/components, the Cricsheet retrieval and
  normalization pipeline, warehouse adapters, migrations, tests, and build
  configuration.
- Tests were not run because this task added documentation only; the review
  identified follow-up coverage for route-parameter reuse, guard failure
  states, destructive CSV targets, changed source digests, unsupported envelope
  versions, empty input, and non-zero CLI exit propagation.

## Task: Implement review follow-ups

### Title

Harden ingestion, authentication, and client navigation findings

### Date/time completed

2026-10-03 12:45

### What was shipped

- Prevented CSV output from overlapping or deleting the base, match-data, or
  player-registry paths, and made empty or uppercase-extension input explicit.
- Corrected player-register CSV parsing, enforced canonical envelope versions,
  detected changed source digests where provenance is available, and propagated
  CLI failures through a non-zero exit status.
- Removed permissive production authentication defaults, made JWT audiences and
  scopes configuration-driven, and preserved explicit test-only fallback values.
- Reloaded scoresheets on reused Angular route parameters, distinguished BFF
  outages from anonymous sessions, and replaced misleading or inert navigation
  affordances with honest unavailable states.

### Key decisions

- A changed source digest is rejected during incremental import rather than
  silently leaving warehouse facts and provenance inconsistent.
- Development-only defaults remain available through explicit development mode;
  production OIDC, JWT, and database values must be supplied by configuration.
- Existing legacy/minimal JDBC fixtures without provenance tables retain their
  duplicate-match behavior while current schemas perform correction detection.

### Gotchas

- Existing warehouses must be rebuilt or migrated before corrected source
  records can be imported safely.
- The Angular client still mirrors the shared wire contracts manually; contract
  drift should be addressed by future generated-client work.

### Test coverage areas

- Added coverage for CSV target overlap, quoted registry fields, empty input,
  uppercase JSON files, unsupported envelope versions, BFF outages, and route
  parameter reuse.
- Verified with `./gradlew :bbb-api:test :bbb-web:test :bbb-update-database:test
  :bbb-parse-cricsheet:test :bbb-cli-shared:test --no-daemon`.

## Overall

### What was shipped

- Second thermo-nuclear structural pass finished warehouse write policy, identity-era
  jOOQ models, match read decomposition, shared identity ownership, nullable match
  summary contracts, explicit opposing-team bowling, and Angular codec colocation.
- Public contracts no longer expose warehouse keys as `sourceMatchId`.
- Warehouse writers share row values and collision decisions; JDBC match loads are
  transactional.
- Fresh Compose MariaDB init and committed jOOQ include identity-era warehouse columns
  (`match_key`, `canonical_match_id`, `public_match_id`, `match_source_reference`).
- Identity IDs and deterministic generation live in `bbb-shared`; envelopes remain in
  `bbb-cli-shared`.
- Match repository formatting lives in `MatchResponseMapper`; scoresheet loading is a
  dedicated loader; the empty `MatchService` hop is gone.
- Scoresheet presenter is split and the Angular match feature is colocated under
  `feature/matches`.

### Key decisions

- Hash-and-reject public IDs and internal `matchKey` joins remain unchanged.
- `MatchSummary` optional display fields are nullable `String?`, aligned with search and
  scoresheet contracts.
- Schema migrations stay immutable history; Compose init, SQL generators, and jOOQ
  codegen carry the current end-state warehouse shape.
- jOOQ generation maps `match_key` to `MATCH_KEY` (not `ID`) and can regenerate from a
  temporary identity-era schema while emitting the production schema name.

### Gotchas

- Detekt/Ktlint/cyclomatic Gradle tasks are not configured on these modules.
- `bbb-shared` Pitest still lacks a JUnit 5 plugin (pre-existing).
- Live MariaDB integration tests still depend on a migrated local database when
  not using the refreshed Compose init volume.
- Existing Compose volumes need `docker compose down -v` to pick up identity-era init.

## Task: Thermo-nuclear structural follow-up

### Title

Finish warehouse write unification, identity-era jOOQ, match read split, and shared identity

### Date/time completed

2026-10-02 13:59

### What was shipped

- Shared `matchWriteDecision` plus ordered match/delivery/source-reference value lists
  in `WarehouseWriteSupport`; JDBC/SQL/CSV adapters encode only.
- Regenerated committed jOOQ with `MATCH_KEY`, `CANONICAL_MATCH_ID`, and
  `MatchSourceReference`; forced `match_key` to BIGINT and stable output schema name.
- Extracted `JooqMatchScoresheetLoader`; removed pass-through `MatchService`; routes use
  `MatchRepository`.
- Moved identity IDs and `DeterministicIdentity` into `bbb-shared`; cli-shared exposes
  shared as `api`.
- Nullable `MatchSummary` display fields; named `requireOpposingTeam`; Angular
  `match-search-query.codec` under `feature/matches/domain`.

### Key decisions

- Keep envelope DTOs in `bbb-cli-shared` next to `BbbMatchData`.
- Prefer nullable optional display fields over empty-string sentinels on match summaries.
- Document two-team warehouse bowling as an explicit opposing-team rule.

### Gotchas

- jOOQ regeneration against a temporary schema requires `outputSchema=acs_ball_by_ball`
  so generated catalog names stay stable for API imports.
- Temporary codegen database was dropped after regeneration.

### Test coverage areas

- `WarehouseWriteSupportTest` collision and ordered value lists.
- Updater, shared, cli-shared, parse-cricsheet, API, and web checks.
- Full `./gradlew clean check --no-daemon` green, including 70 Angular tests.

## Task: Split match repository mapping and clear unused imports

### Title

Match response mapper extraction and unused-import cleanup

### Date/time completed

2026-10-02 13:10

### What was shipped

- Extracted `MatchResponseMapper` for score/result/date/format/completeness
  formatting from `JooqMatchRepository`.
- Collapsed repeated repository try/log/rethrow wrappers into `withMatchQuery`.
- Removed unused imports in API/web entry points, shared tests, and the loader
  `Database` class.
- Confirmed full `./gradlew clean check --no-daemon` green after the combined
  thermo-nuclear fixes.

### Key decisions

- Mapping lives next to data reads rather than inside a 670-line companion object;
  the later structural pass removed the empty service hop entirely.
- Leave `matchKey` private to repository joins.

### Gotchas

- Labeled early returns inside `withMatchQuery` lambdas are local to the lambda
  and do not require `inline`.

### Test coverage areas

- `:bbb-api:test` including mapper unit tests and API module tests.
- Full multi-module `clean check` after import cleanup.


## Task: Unify warehouse output writers and make JDBC match writes atomic

### Title

Shared warehouse output policy and JDBC match transactions

### Date/time completed

2026-10-02

### What was shipped

- Added `WarehouseWriteSupport` for warehouse column contracts, insert SQL
  generation, date-dimension values, and shared public-match-ID collision policy.
- Updated JDBC, SQL-script, and CSV adapters to consume the shared contracts
  while retaining their binding, literal, and CSV-cell serialization behavior.
- Wrapped each `Database.writeMatch` operation in begin/commit/rollback hooks;
  JDBC adapters disable auto-commit for the match and restore its prior state.
- Replaced output-selection branching with sealed database, script, and CSV
  targets, and removed the unused empty exception-filter plumbing.
- Documented transactional JDBC writes in the root README.

### Key decisions

- Kept transaction hooks default no-ops so CSV and generated-script adapters
  preserve their output lifecycle while JDBC owns its actual transaction.
- Canonical re-import and public-ID backfill both finish through the commit path;
  a failed match write rolls back and preserves the original exception.
- Shared insert columns include explicit generated keys for file output and
  derive JDBC insert subsets by dropping generated-key columns.

### Gotchas

- The MariaDB schema test's original count pattern excluded `fact_match` because
  its `match_key` is declared as a primary key; the assertion now includes that
  form while still checking all five match-key tables.
- `bbb-update-database/build.gradle.kts` does not apply Kover, so no module
  coverage report is configured for this task.

### Test coverage areas

- `./gradlew :bbb-update-database:test --no-daemon`: 40 tests passed.
- `./gradlew clean check --no-daemon`: passed all modules; Angular client tests
  completed with 70 successes.
- Covers JDBC match rollback, SQLite transaction rollback, canonical import
  idempotency/public-ID backfill, collision rejection, CSV output, and MariaDB,
  PostgreSQL, and SQLite SQL-script generation.

## Task: Remove false `sourceMatchId` response contract

### Title

Remove the internal source identifier from public match contracts

### Date/time completed

2026-10-02 12:53

### What was shipped

- Removed `sourceMatchId` from the shared recent-match, historical-search, and
  scoresheet-context contracts, API domain models, and route/repository mapping.
- Removed the unused `SourceMatchIdError`; the `SourceMatchId` value-class
  source was already absent from the current tree.
- Changed missing recent-summary display strings to empty values and updated
  repository formatting/fallbacks to return empty strings or null as appropriate.
- Replaced home and historical-results `MISSING` labels with an em dash while
  keeping `publicMatchId` public and `match_key` internal.

### Key decisions

- Required `MatchSummary` display strings remain non-nullable and default to
  `""` to keep serialization stable; optional search and scoresheet fields stay
  nullable.
- Database `match_key` remains available only for internal joins, ordering, and
  scoresheet access.

### Gotchas

- Earlier full-check reds in updater rollback/schema tests were resolved by the
  concurrent warehouse-writer unification work; later full checks are green.

### Test coverage areas

- `:bbb-shared:test`: passed contract serialization and empty summary-default
  assertions.
- `:bbb-api:test`: passed API and repository tests; `:bbb-api:compileKotlin`
  passed.
- Angular `:bbb-web:check`: all 70 browser tests passed during the full check.

## Task: Colocate match web contracts and split scoresheet presentation

### Title

Feature-sliced match data and scoresheet helpers

### Date/time completed

2026-10-02 12:52

### What was shipped

- Removed `sourceMatchId` from the Angular match models and affected fixtures.
- Moved the match model to `feature/matches/domain` and the service/spec to
  `feature/matches/data`, updating all ClientApp imports.
- Split scoresheet calculations into formatting, batting-lane, bowling, ledger,
  and orchestration modules; retained `scoresheet-presenter.ts` as a façade.
- Left the Kotlin `WebModuleTest` contract unchanged because its
  `MatchSearchResult` is a separate server-side type.

### Key decisions

- Preserved the existing presentation function and output types through the
  `scoresheet-presenter.ts` compatibility façade.
- Kept the change inside the Angular feature slice; no API contract was changed.

### Gotchas

- The match search query codec remains in `models` and now imports match types
  from the feature domain.

### Test coverage areas

- Angular match/home fixtures and services, query behavior, selected-match UI,
  scoresheet batting lanes, bowling figures, extras ledger, formatting, and
  wicket attribution.
- `npm test -- --watch=false --browsers=ChromeHeadless`: 70 tests passed.
- `./gradlew :bbb-web:check --no-daemon`: passed.

## Task: Align fresh and generated warehouse schemas

### Title

One current warehouse shape for MariaDB

### Date/time completed

2026-10-02 12:44

### What was shipped

- Updated Compose's fresh-install warehouse DDL to include the signed `BIGINT`
  match key, nullable unique canonical/public IDs, `match_source_reference`, and
  signed match foreign keys.
- Aligned the generated schema with the same key and identity shape, plus the
  MariaDB `DATETIME` and unsigned `match_count` types from migrations 2–5.
- Documented that these identity columns are initialized for fresh Compose
  volumes without requiring Flyway.

### Key decisions

- Kept Flyway migrations 1–5 immutable and folded the end-state into the
  existing Compose warehouse init script.
- Kept the general warehouse surrogate keys unsigned while modeling match keys
  separately as signed values, matching the MySQL migration contract.

### Gotchas

- Compose init scripts run only for an empty MariaDB volume; existing volumes
  still require normal migration handling.
- The post-change `:bbb-update-database:test` run is blocked before test
  execution by an unrelated `bbb-shared:compileKotlin` error: its
  `SourceMatchId.kt` source is missing (an earlier attempt reported unresolved
  `SourceMatchIdError`). The schema regression test was observed failing against
  the old generated SQL before the schema fix.

### Test coverage areas

- Generated MariaDB schema assertions for nullable unique canonical/public IDs,
  fixed-width source references, signed match keys, and MySQL timestamp/count
  types.

## Task: Split match identity declarations by concern

### Title

Separate match identity types, algorithms, and envelopes

### Date/time completed

2026-10-02 12:45

### What was shipped

- Split the shared match identity source into `IdentityIds.kt`,
  `DeterministicIdentity.kt`, and `Envelopes.kt` in the same package.
- Preserved the existing value types, serializers, namespace constants, UUIDv5
  derivation, public match ID mapping, SHA-256 hashing, and envelope contracts.
- Removed the original combined `MatchIdentity.kt` source after extraction.

### Key decisions

- Kept public names and package unchanged so existing module imports remain valid.
- Kept algorithm implementations and serialization contracts unchanged; added no
  pass-through wrapper types.

### Gotchas

- `Envelopes.kt` retains its file-level serialization opt-in for `@EncodeDefault`.

### Test coverage areas

- Existing golden vectors for UUIDv5, public match IDs, unsigned mapping, and
  SHA-256; envelope serialization round trips; tiny-type input validation.

## Task: Widen deterministic public match identifiers

### Title

Ten-digit canonical match IDs for public URLs

### Date/time completed

2026-10-02 09:06

### What was shipped

- Widened `PublicMatchId` validation and serialization to the inclusive range
  `1_000_000_000..9_999_999_999`.
- Preserved the fixed UUIDv5 namespace and canonical UUID input while expanding
  the deterministic modulo mapping to nine billion possible values.
- Added migration 5 to clear obsolete seven-digit persisted values so canonical
  envelope replay assigns the new ten-digit URLs.

### Key decisions

- Hash-and-reject remains the collision policy; widening reduces collision
  probability without making IDs dependent on insertion order.
- Canonical UUIDs, source lineage, warehouse keys, and the URL route shape are
  unchanged; existing public URLs must be regenerated by canonical replay.

### Gotchas

- Run migration 5 before replaying canonical envelopes in an existing database;
  rows without canonical identity remain unaddressable.
- The known live MariaDB full-check limitation remains until migrations 4 and 5
  are applied locally.

### Test coverage areas

- Ten-digit lower/upper bounds, malformed input, serialization, deterministic
  golden vectors, adapter output, API fixtures, Angular route validation, and
  migration reset behavior.

## Task: Add deterministic public match identifiers

### Title

Seven-digit canonical match IDs for public URLs

### Date/time completed

2026-10-02 08:50

### What was shipped

- Added the validated `PublicMatchId` type and deterministic UUIDv5-derived
  seven-digit generator based on canonical match identity.
- Added nullable legacy-safe `public_match_id` migrations for MariaDB,
  PostgreSQL, and SQLite, with persistence, replay backfill, idempotency, and
  collision rejection across JDBC, SQL-script, and CSV output.
- Replaced API and Angular scoresheet URLs with
  `/api/matches/{publicMatchId}/scoresheet` while retaining internal
  `match_key` joins.

### Key decisions

- The fixed public namespace is `0e4f5f84-3b4c-5f0b-a5c6-7d8e9f0a1b2c` and
  mapping uses unsigned UUID high bits modulo the seven-digit range.
- Hash-and-reject preserves reproducibility; probing and sequential allocation
  were rejected because insertion order would become observable.

### Gotchas

- Legacy rows without canonical identity remain without a public ID and require
  canonical-envelope replay; filenames are never used to infer one.
- The root `README.md` referenced by the plan is absent; `README-DEV.md` was
  updated instead. Full MariaDB validation still requires migration 4 locally.

### Test coverage areas

- Public-ID bounds, malformed input, serialization, golden vectors, unsigned
  behavior, migration uniqueness, backfill, adapter idempotency/collisions,
  API contract changes, and all 68 Angular tests.

## Task: Accept current Cricsheet provider keys

### Title

Allow safe non-numeric Cricsheet basename stems

### Date/time completed

2026-10-02 06:26

### What was shipped

- Updated Cricsheet provider-key validation to accept current keys such as
  `wi_201706` while preserving the basename-stem boundary.
- Added regression coverage for moved `wi_201706.json` files and unsafe stems,
  and aligned the ADR and application architecture documentation.

### Key decisions

- The unchanged provider-local basename stem remains the UUIDv5 input; only
  letters, digits, underscores, and hyphens are allowed, with an alphanumeric
  first character.

### Gotchas

- Paths, extensions, spaces, and other punctuation remain invalid provider
  keys; the parser supplies only `nameWithoutExtension` to the converter.

### Test coverage areas

- Replayed the supplied `wi_201706.json` through the CLI parser successfully;
  all `bbb-parse-cricsheet` tests pass.

## Task: Add deterministic match identity

### Title

Separate source, canonical, and warehouse match identity

### Date/time completed

2026-10-01 21:50

### What was shipped

- Added validated UUID/digest types, versioned source and canonical envelopes,
  UUIDv5 generation, raw-byte SHA-256, and explicit single-source promotion.
- Cricsheet output now carries reproducible provenance, and the updater loads
  canonical UUIDs idempotently with generated numeric warehouse join keys.
- Added source-lineage storage and immutable MariaDB, PostgreSQL, and SQLite
  migrations, plus ADR 0003 and updated architecture/runbook guidance.

### Key decisions

- Provider identity and revision evidence remain separate; reconciliation is a
  future bounded context and the warehouse accepts canonical envelopes only.
- Legacy rows retain null canonical identity until replay rather than deriving
  identity from historical filenames.

### Gotchas

- Cricsheet input basenames must be safe provider-key stems.
- Apply migration 3 before loading canonical envelopes into an existing
  warehouse; replay legacy source files to establish their canonical lineage.

### Test coverage areas

- Golden UUID/digest vectors, validation and serialization, moved files,
  corrected bytes, duplicate imports, lineage output, surrogate foreign keys,
  generated SQL/CSV, and populated SQLite migration compatibility.
- Repository `clean check` passes except live API integration tests until the
  local MariaDB applies migration 3. Kover/Pitest are not configured for the
  changed modules; the existing `bbb-shared` Pitest task runs no JUnit 5
  mutations because its JUnit 5 plugin is missing.

## Task: Make generated MariaDB SQL foreign-key compatible

### Title
Force InnoDB for generated MariaDB warehouse tables

### Date/time completed

2026-10-01 17:35

### What was shipped

- Updated the MariaDB SQL-file schema generator to declare `ENGINE = InnoDB`
  on every generated warehouse table.
- Added regression coverage that verifies all 12 generated MariaDB warehouse
  tables explicitly use InnoDB.

### Key decisions

- Keep the engine declaration dialect-specific so PostgreSQL and SQLite output
  remain unchanged.
- Make generated SQL deterministic instead of relying on the target server's
  `default_storage_engine` setting.

### Gotchas

- Existing SQL files must be regenerated; this fix affects newly generated
  scripts only.
- The SQL-file workflow drops and recreates the warehouse tables, so imports
  remain destructive to existing warehouse data.

### Test coverage areas

- MariaDB SQL generation now verifies one explicit InnoDB declaration per
  warehouse table, including `bridge_match_person`.
- `SqlScriptOutputAdapterTest` and the repository-wide `./gradlew clean check
  --no-daemon` both pass.

## Task: Add typesafe JOOQ generation to bbb-api

### Title

Generate and adopt typed MariaDB schema bindings

### Date/time completed

2026-10-01 13:21

### What was shipped

- Added the official JOOQ Gradle code-generation plugin and a checked-in Kotlin generated source set for `bbb-api`.
- Migrated `JooqMatchRepository` and `MatchSearchConditionBuilder` from string-based tables and fields to generated JOOQ table and column definitions.
- Scoped generation to the warehouse tables used by the matches API and generated only the typed table/record bindings required by the repository.

### Key decisions

- `jooqCodegen` reads MariaDB connection settings from `JOOQ_DATABASE_URL`, `JOOQ_DATABASE_USER`, and `JOOQ_DATABASE_PASSWORD`, with local defaults matching the development database.
- MariaDB unsigned warehouse key columns are forced to JOOQ `BIGINT` so they map to the API's existing `Long` identifiers; the schema's `INT` match ID remains explicitly converted at the domain boundary.
- Generated sources are stored under `bbb-api/src/generated/jooq/kotlin` and are not regenerated during ordinary compilation.

### Gotchas

- Code generation requires the target MariaDB schema to be available; run `./gradlew :bbb-api:jooqCodegen --no-daemon` with the connection overrides when regenerating.
- The current warehouse uses `dim_match.id` and integer match foreign keys, so those fields must not be included in the unsigned key forced-type mapping.

### Test coverage areas

- `bbb-api` compilation and existing repository/API integration tests cover recent matches, historical search, scoresheets, pagination, nullable fields, and database failure behavior using the generated bindings.

## Task: Extract numeric match IDs from decorated filenames

### Title

Allow filename prefixes and suffixes during database updates

### Date/time completed

2026-10-01 12:46

### What was shipped

- Match IDs now extract all numeric characters from the source filename after
  removing its directory path, allowing names such as `wi_201706_revised.json`.
- JDBC, SQL-file, and CSV duplicate detection and insertion continue to use the
  shared extracted ID.

### Key decisions

- Non-numeric filename decorations are ignored while the basename is preserved
  unchanged in `dim_match.file_name` for provenance.
- Filenames without a numeric value, or values outside the existing `Int` range,
  remain invalid and raise `InvalidStateException`.

### Gotchas

- Different decorated filenames that produce the same numeric value identify the
  same warehouse match and therefore cannot create separate `dim_match` rows.
- The live MariaDB integration database still has the pre-consolidation match
  columns, so the repository-wide integration check requires the prior schema
  migration to be applied first.

### Test coverage areas

- Regression coverage verifies that prefixed and suffixed filenames resolve to
  their numeric match ID.

## Task: Consolidate match identity columns

### Title

Use the numeric JSON filename stem as the warehouse match ID

### Date/time completed

2026-10-01 11:11

### What was shipped

- Replaced `dim_match.match_key`, `source_match_id`, and `source_file_name` with
  the integer `id` derived from the source filename without `.json`.
- Updated JDBC, SQL-file, CSV, generated-schema, Docker bootstrap, API query,
  and dialect migration definitions to use the shared ID.
- Removed the separate `3__add_source_file_name.sql` migrations; the complete
  schema is now defined by each dialect's `2__initial_warehouse.sql`.

### Key decisions

- The fully qualified source path remains in `dim_match.file_name` for
  provenance, while duplicate detection extracts the numeric filename stem and
  queries `dim_match.id`.
- Match IDs are validated at the output boundary and reject filenames whose
  stem is not an integer JSON match identifier.

### Gotchas

- Existing databases using the removed three-column identity contract need to
  be recreated or migrated before running the updated loader; the new
  `2__initial_warehouse.sql` files describe the target schema for fresh loads.

### Test coverage areas

- Duplicate detection works when the same numeric filename is loaded from a
  different directory.
- SQL-file, CSV, and SQLite outputs emit and reuse the numeric match ID.
- `:bbb-update-database:test` passes; the full repository `clean check` is the
  final validation for this task.

## Task: Keep future dismissals out of earlier scorecard rows

### Title

Render dismissal details only on the dismissal row

### Date/time completed

2026-10-01 10:30

### What was shipped

- Batter dismissal status and score details are now limited to the row where
  the wicket occurs.
- A batter who is dismissed in a later over keeps their earlier cumulative
  score without an `[OUT]` marker or dismissal styling in preceding rows.

### Key decisions

- Retained the cumulative batting totals and global dismissal-score lookup,
  but determine `dismissed` from the current row's deliveries.
- Kept the existing next-delivery inference for identifying the dismissed
  batter, including non-striker run outs.

### Gotchas

- The API still reports wickets at delivery level and does not provide a
  dismissed-player field, so attribution remains dependent on the existing
  transition inference and fallback behavior.

### Test coverage areas

- Presenter regression verifies a later Kapp dismissal does not mark Kapp out
  in the earlier row, while the dismissal row still shows the final score.
- Component regression verifies the earlier HTML row has no dismissal marker;
  all 67 Angular tests pass.

## Task: Render non-striker wickets against the dismissed batter

### Title

Keep wicket symbols and delivery runs with the correct batter

### Date/time completed

2026-10-01 10:26

### What was shipped

- The scorecard now renders a non-striker run-out wicket beside the dismissed
  batter rather than the batter who faced the delivery.
- The facing batter keeps the delivery outcome, such as Kapp's `1`, while the
  dismissed batter receives the `W` symbol and dismissal details.

### Key decisions

- Reused the existing next-delivery dismissal inference for cell-level symbol
  ownership so status, notes, scores, and symbols remain consistent.
- Suppressed only the wicket portion of a non-dismissed striker's symbol; run,
  extra, boundary, and alignment behavior remains unchanged.

### Gotchas

- The API still identifies wickets only at delivery level, so the next delivery
  remains the evidence used to identify a non-striker dismissal.

### Test coverage areas

- The ball-88 Kapp/Verma regression verifies Kapp renders `1` and Verma renders
  `W`, alongside dismissal status, score, notes, and lane continuity.
- The pre-fix regression failed; all 65 Angular tests pass after the fix.

## Task: Attribute wickets to the replaced batter

### Title

Use the next delivery to identify non-striker dismissals

### Date/time completed

2026-10-01 10:19

### What was shipped

- The scoresheet now identifies the dismissed batter by comparing the wicket
  delivery's batting pair with the next delivery's pair.
- Non-striker run outs now receive the dismissal score, `[OUT]` marker, lane
  release, and wicket note instead of incorrectly marking the striker.

### Key decisions

- Select the sole player from the wicket delivery who is absent on the next
  delivery; retain the striker as a safe fallback when the next delivery is
  unavailable or the player transition is ambiguous.
- Apply the same inferred player consistently to lane assignment, dismissal
  scores, and wicket notes so the scorecard remains internally consistent.

### Gotchas

- The API contract does not contain a dismissed-player field, so an innings-end
  wicket or incomplete next delivery cannot always be attributed beyond the
  striker fallback.

### Test coverage areas

- Regression coverage reproduces ball 88's Kapp/Verma run out followed by
  Penna/Kapp and verifies status, score, note, and subsequent lane continuity.
- The pre-fix regression failed; all 65 Angular tests pass after the fix.

## Task: Start the displayed over count at one

### Title

Display zero-based delivery overs as one-based scorecard labels

### Date/time completed

2026-10-01 10:10

### What was shipped

- The scorecard's leftmost over column now displays the first zero-based
  delivery over as `1`, followed by `2`, `3`, and so on.

### Key decisions

- Applied the one-based conversion only in the Angular presentation template;
  delivery grouping, lane allocation, and domain data remain unchanged.

### Gotchas

- The source/API contract uses zero-based `overNumber` values, so tests must
  distinguish raw delivery numbers from displayed scorecard labels.

### Test coverage areas

- Added a component regression using source overs `0` and `1` and asserting
  visible labels `1` and `2`.
- The baseline regression failed before the fix; all 64 Angular tests passed
  after the fix.

## Task: Keep all scorecard names consistently bold

### Title

Use the same readable name styling for repeated bowlers and batters

### Date/time completed

2026-10-01 10:04

### What was shipped

- Bowler names now retain the same bold, readable styling every time they
  appear in the Left or Right lane.
- Batter names now use the same consistent styling across all scorecard rows,
  while delivery symbols and cumulative figures remain unchanged.

### Key decisions

- Removed only the repeated-name class bindings and muted CSS rules; the base
  scorecard typography remains the single authoritative style for both name
  types.
- Kept the presenter’s appearance state and lane calculations intact because
  they do not affect the scorecard data or layout after the styling change.

### Gotchas

- The repeated-name selector names remain referenced only by regression
  assertions to ensure those visual variants are not rendered.

### Test coverage areas

- Angular component tests cover repeated bowler and batter names using the
  base classes only.
- All 63 Angular tests pass after the change.

## Task: Improve scorecard grid text contrast

### Title

Make scorecard matrix text clearer against the grid background

### Date/time completed

2026-10-01 10:00

### What was shipped

- Darkened the matrix's inherited body text and standard run symbols so the
  scorecard grid is easier to read.
- Preserved the intentional faded styling for repeated player names, score
  summaries, and empty non-striker slots.

### Key decisions

- Limited the visual change to the scorecard component stylesheet rather than
  changing the application-wide typography or layout.
- Matched the darker slate tones used by the Stitch reference design.

### Gotchas

- Muted states remain lighter by design so repeated names and unused delivery
  positions retain their visual hierarchy.

### Test coverage areas

- Full Gradle `check` passed, including all 62 Angular tests and the production
  web bundle.

## Task: Place batter scores after the last played ball

### Title

Keep batter score labels adjacent to the final delivery symbol

### Date/time completed

2026-10-01 09:57

### What was shipped

- Batter runs-and-balls labels now appear immediately after each batter's last
  played ball in the over.
- Empty non-striker delivery slots remain rendered after the score label so
  the existing matrix spacing is preserved.

### Key decisions

- Added the final played delivery index to the presenter model so the template
  can place the score without changing delivery symbols or their alignment.
- Kept dismissed-batter scores and the `[OUT]` marker together at the same
  insertion point.

### Gotchas

- A batter with no played delivery in the row receives the score before all
  placeholder slots; this avoids placing a score after invisible content.

### Test coverage areas

- Component regression coverage verifies score, final ball, and trailing
  placeholder DOM order.
- Angular tests, production build, and repository checks validate the change.

## Task: Show cumulative batter runs and balls

### Title

Display running batter scores in every over row

### Date/time completed

2026-10-01 09:08

### What was shipped

- Batter notation now shows each batter's cumulative runs and balls faced at
  the end of the current over rather than only that over's contribution.
- Delivery symbols remain scoped to the current over, while the adjacent
  score label reflects the running innings total.

### Key decisions

- Maintained a per-innings batter score map alongside the existing cumulative
  bowler and ledger state.
- Preserved the existing dismissed-batter boundary formatting and wide-ball
  ball-counting behavior.

### Gotchas

- A batter's name and symbols can repeat in each row, but the score label is
  intentionally cumulative so later rows represent the current innings state.

### Test coverage areas

- Presenter regression tests cover cumulative runs and balls across overs.
- Angular component tests verify the cumulative value is rendered in the
  scorebook table.
- Client tests, build, and repository checks pass.

## Task: Remove trailing zero from bowler overs

### Title

Format completed bowler overs without a `.0` suffix

### Date/time completed

2026-10-01 09:03

### What was shipped

- Completed bowler overs now display as whole numbers, for example `1` rather
  than `1.0`.
- Partial overs continue to display their legal-ball remainder, for example
  `0.1`.

### Key decisions

- Kept the existing cumulative legal-ball calculation and changed only the
  presentation formatter.

### Gotchas

- The decimal notation represents balls within the current over, not a
  fractional decimal value; therefore only a zero remainder omits the suffix.

### Test coverage areas

- Presenter regression tests cover completed one- and two-over figures and
  retain existing partial-over coverage.
- Web tests, build, and repository checks pass after this change.

## Task: Add alternating cumulative bowler lanes

### Title

Display bowlers by end with running bowling figures

### Date/time completed

2026-10-01 08:25

### What was shipped

- Replaced the combined per-over bowler listing with explicit Left and Right
  end columns in the scorebook matrix.
- Bowlers now occupy alternating end columns by over and show cumulative
  `(overs-maidens-runs-wickets)` figures, with a dash for the unused end.
- Repeated bowler names use the same softer styling established for repeated
  batter names.

### Key decisions

- Odd-numbered overs map to the Left end and even-numbered overs map to the
  Right end, matching the supplied reference layout.
- Bowler overs count legal balls, maidens count completed six-ball overs with
  no conceded runs, and byes and leg-byes are excluded from bowler runs.
- Existing innings ledger, batter lanes, and end-of-over event handling remain
  unchanged.

### Gotchas

- A row can retain multiple summaries in one end lane if malformed source data
  contains more than one bowler in an over; normal deliveries still render one
  bowler per end column.
- The Impeccable detector remains in degraded regex mode because optional
  HTML/CSS parser modules are unavailable; the final scan reported no findings.

### Test coverage areas

- Presenter tests cover alternating lanes, cumulative overs, maidens, conceded
  runs, wickets, and repeated-bowler appearance state.
- Angular component tests cover Left/Right headers, empty-end placeholders, and
  inline cumulative figures.
- `npm test` (60 tests), `npm run build`, and `./gradlew clean check --no-daemon`
  pass.

## Task: Remove empty batter delivery dots

### Title

Keep non-striker spacing without rendering placeholder dots

### Date/time completed

2026-10-01 08:18

### What was shipped

- Non-striker delivery slots remain present in the inline batter notation, but
  no longer render pale grey dot glyphs.
- Real dot-ball symbols remain unchanged, so the notation still distinguishes a
  batter's own scoreless delivery from a delivery faced by the other batter.

### Key decisions

- Kept the existing placeholder spans, minimum width, and flex gap so the
  over-by-over columns retain their alignment and rhythm.
- Represented the visual absence with an empty placeholder value while keeping
  the existing accessible non-striker label on each slot.

### Gotchas

- The Impeccable detector runs in degraded regex mode because optional HTML/CSS
  parser modules are unavailable; the final scan reported no findings.

### Test coverage areas

- Presenter tests verify non-striker placeholders are empty while retaining
  their semantic class.
- Angular component tests verify placeholder spans remain counted and empty.
- `npm test`, `npm run build`, and `./gradlew clean check --no-daemon` pass.

## Task: Prevent duplicate database match inserts

### Title

Use the normalized JSON filename as the database match identity

### Date/time completed

2026-09-30 17:35

### What was shipped

- Added `dim_match.source_file_name` for the unqualified JSON filename while
  retaining the fully qualified path in `dim_match.file_name`.
- Direct JDBC database updates now find an existing match by the indexed source
  filename before inserting it, so dependent innings, facts, and bridge rows
  are not regenerated for a match already present in the database.
- Added Flyway version 3 migrations for MariaDB, PostgreSQL, and SQLite, plus
  matching generated-schema and container-bootstrap definitions.

### Key decisions

- The normalized JSON filename is the available source-stable identity; the
  fully qualified path remains provenance and is not used as the primary
  duplicate key because it can change between runs.
- The lookup is indexed but not declared unique, avoiding a destructive schema
  assumption if two source collections ever contain the same basename.
- SQL-file and CSV output remain file-generation workflows and continue to
  produce complete outputs without querying an existing database.

### Gotchas

- Existing databases must run `3__add_source_file_name.sql` before direct
  database updates. SQLite cannot change an existing column's nullability in
  place, so its migrated column is nullable although new writes always supply
  the value.
- The duplicate check is basename-based; a future source with colliding
  basenames will need a stronger source identifier.

### Test coverage areas

- JDBC lookup returns the existing match key for a changed fully qualified path.
- A repeated database import leaves the existing `dim_match` row unchanged and
  does not insert dependent rows.
- SQL and CSV output include both the full path and unqualified source filename.
- SQLite Flyway migration syntax was applied against a fresh in-memory schema.

## Task: Store register CSVs at the base directory

### Title

Align register download and database lookup paths

### Date/time completed

2026-09-30 17:00

### What was shipped

- `bbb-get-cricsheet-data` now stores `people.csv` and `names.csv` directly
  under the configured base directory in both full and nightly modes.
- `bbb-update-database` now resolves the player registry directly beneath the
  base directory for both modes.
- The legacy `--names-directory` option remains validated and accepted for
  command compatibility but no longer changes the register location.

### Key decisions

- Register files are shared base-level artifacts, separate from the configured
  match-data directory and its extracted JSON files.
- Existing data-directory preflight and nightly overwrite behavior remain
  unchanged; only register path resolution moved.

### Gotchas

- Existing invocations that pass `--names-directory` can continue to run, but
  the option is deprecated and its directory is not created or read.
- The updater still requires `--player-registry` as a filename, now resolved
  relative to `--base-directory`.

### Test coverage areas

- Retrieval tests cover full and nightly register placement, overwrite
  preservation, and request continuation.
- Updater argument tests cover base-level registry resolution with and without
  the compatibility names-directory option.

## Task: Add parser application logging

### Title

Make `bbb-parse-cricsheet` progress and failures observable

### Date/time completed

2026-09-30 14:55

### What was shipped

- Added parser lifecycle, file discovery, per-file progress, failure, and completion logging.
- Added a CLI completion summary while preserving the existing non-zero result for failed files.

### Key decisions

- Kept logging in the parser service as well as the CLI entry point so library-level parser runs are observable.
- Used debug-level messages for individual successful files and warning-level messages for parse exceptions to avoid noisy normal CLI output.

### Gotchas

- Empty input directories remain valid and now emit a warning rather than changing parser behavior.

### Test coverage areas

- Existing parser normalization, recursive output, and continue-and-report tests pass with logging enabled.

## Task: Make the shared schema source-neutral

### Title

Remove Cricsheet metadata and rename normalized match details

### Date/time completed

2026-09-30 14:44

### What was shipped

- Removed the Cricsheet-specific `meta` field from shared `BbbMatchData` JSON.
- Renamed the shared document's `info` property to `match`.
- Updated the parser conversion, updater consumers, tests, and contributor architecture documentation.

### Key decisions

- Raw Cricsheet `meta` and `info` remain owned by `bbb-parse-cricsheet` only; normalized output exposes source-neutral `match` data.
- The parser continues to decode source snake-case fields and maps them into the shared schema before writing output.

### Gotchas

- Raw parser fixtures still contain `meta` and `info` because they test Cricsheet input decoding; normalized output must not contain either property name.

### Test coverage areas

- Shared-schema serialization asserts `match` is present and `meta`/`info` are absent.
- Parser output and updater decoding tests cover the revised normalized contract.

## Task: Normalize Cricsheet data through a shared CLI schema

### Title

Add `bbb-cli-shared` and `bbb-parse-cricsheet`, then migrate the updater to `BbbMatchData`

### Date/time completed

2026-09-30 14:21

### What was shipped

- Added the JVM `bbb-cli-shared` module with the complete serializable
  `BbbMatchData` document graph and Kotlin-compatible camelCase property names.
- Added `bbb-parse-cricsheet`, which owns the annotated raw Cricsheet model,
  recursively converts JSON files, maps source format and gender to warehouse
  match types, and writes normalized documents atomically.
- Migrated `bbb-update-database` to decode only shared-schema JSON and removed
  its raw Cricsheet model and archive-directory metadata mapping.

### Key decisions

- The parser is the source adapter; the shared module is source-neutral and
  contains no `@SerialName` annotations.
- Parser input and output use required absolute `--base-directory` plus safe
  relative `--input` and `--output` directories, with relative-tree mirroring.
- Supported mappings include `Test` → `t`, `T20` → `tt`, `IT20` → `itt`,
  `ODI` → `o`, `ODM` → `a`, and `MDM` → `f`; female matches receive the `w`
  prefix. Unknown match types fail the individual file while other files run.

### Gotchas

- Normalized JSON uses camelCase names under `match` such as `matchType`,
  `nonStriker`, and `cameIn`; Cricsheet-only `meta` is omitted and raw
  snake-case JSON is no longer an updater input contract.
- The updater retains `--nightly` compatibility, but both full and nightly
  modes recursively read the configured normalized data directory.

### Test coverage areas

- Shared-schema serialization and round-trip coverage.
- Parser path validation, recursive mirroring, nested key conversion, source
  field preservation, match-type mapping, atomic output, and continue/report
  behavior.
- Updater database output and direct normalized-schema decoding.

## Task: Derive updater metadata from JSON

### Title

Remove archive-directory metadata dependency from `bbb-update-database`

### Date/time completed

2026-09-30 13:35

### What was shipped

- Full and nightly imports now recursively discover JSON files below the
  configured data directory, without using an archive-directory catalog.
- Competition names come from `info.event.name`, with `Unknown` as the missing
  event fallback.
- Cricsheet `info.match_type` and `info.gender` values are converted to
  warehouse codes such as `t`, `tt`, `f`, `wtt`, and `wa` before database
  output is written.
- Nightly updater data and register paths now match the downloader's direct
  `[base]/[data]` layout.

### Key decisions

- Recursive JSON discovery is shared by full and nightly modes; directory names
  are no longer a source of match type or competition metadata.
- Female matches receive a `w` prefix after source-format mapping, while the
  existing team-name exception rules remain in the database layer.

### Gotchas

- Full downloads may still retain archive-named extraction directories, but
  those names are now operational layout only and are not interpreted.
- Nightly `people.csv` and `names.csv` are read beside nightly JSON, so a
  nightly updater invocation does not use a separate names directory.

### Test coverage areas

- Parser tests cover direct full/nightly paths and recursive flat/nested JSON
  discovery.
- Metadata tests cover missing events, male and female T20/one-day formats,
  Test and international T20 mappings.
- Database tests verify that the derived `wtt` value is emitted in SQL; the
  complete `bbb-update-database` test suite passes.

## Task: Remove the implicit nightly retrieval directory

### Title

Place nightly archives, JSON, and register CSVs under the configured data path

### Date/time completed

2026-09-30 12:50

### What was shipped

- Nightly retrieval now resolves ZIPs and flat extracted JSON directly under
  `[base]/[data]`, with ZIPs in the `zips` child.
- Nightly `people.csv` and `names.csv` are written beside the JSON files,
  without an implicit `[base]/nightly` directory.
- Full retrieval continues to use its separate configured names directory and
  archive-named extraction directories.

### Key decisions

- The `--nightly` flag selects sources and merge behavior only; it no longer
  changes the configured data path.
- `--names-directory` remains syntactically validated for compatibility but
  nightly output is intentionally colocated with the configured data directory.

### Gotchas

- Existing updater nightly path handling must consume the same direct data
  layout when that planned workflow is updated.
- Existing files outside the managed nightly artifacts remain untouched.

### Test coverage areas

- Parser tests cover short and long nightly flags, direct data-path resolution,
  safe nested paths, and compatibility with an explicit names directory.
- Retrieval tests cover fixed request ordering, flat extraction, overwrite
  precedence, directory reuse, stale-file preservation, and CSV colocation.

## Task: Include fully qualified JSON paths in generated output

### Title

Emit absolute source JSON paths in SQL and CSV output

### Date/time completed

2026-09-30 11:40

### What was shipped

- Match imports now pass each normalized absolute JSON path into the warehouse
  output contract.
- Generated SQL scripts and CSV files therefore retain the fully qualified
  source path in `dim_match.file_name`.

### Key decisions

- The path is normalized and made absolute at the application file-discovery
  boundary, so all output adapters receive the same provenance value.
- Match duplicate detection uses that same fully qualified value to remain
  consistent with the emitted source path.

### Gotchas

- Existing exception matching continues to use the discovered basename because
  the current exception list is defined in terms of source filenames.

### Test coverage areas

- SQL-script and CSV adapter regression tests assert that absolute JSON paths
  are emitted in `dim_match.file_name`.
- The complete `bbb-update-database` test suite passes.

## Task: Document database imports for generated output

### Title

Add database loading commands to `README-DEV.md`

### Date/time completed

2026-09-30 11:33

### What was shipped

- Documented CSV loading commands for MariaDB, PostgreSQL, and SQLite.
- Documented PostgreSQL `NULL '\N'` handling and the SQLite limitation with
  the generated CSV null marker.
- Kept SQL-file import commands for all supported database types and aligned
  CSV paths with the base-directory-relative output contract.

### Key decisions

- PostgreSQL uses client-side `\copy` with `NULL '\N'` and the existing
  foreign-key load order.
- SQLite CSV import is documented as limited; the generated SQLite SQL file is
  the recommended path when nullable values must be preserved.

### Gotchas

- Generated CSV files contain warehouse keys and should be loaded into an
  empty or explicitly coordinated warehouse.
- CSV output paths are resolved beneath `CRICSHEET_ROOT`/`baseDirectory`.

### Test coverage areas

- Reviewed all documented MariaDB, PostgreSQL, and SQLite SQL/CSV import
  commands and passed `git diff --check`.

## Task: Resolve updater CSV output beneath the base directory

### Title

Make `-cd` output paths relative to `bd`

### Date/time completed

2026-09-30 11:20

### What was shipped

- CSV output directories supplied through `-cd`/`--csvDir` are now resolved
  beneath the validated base directory.
- Nested relative CSV output paths are supported, and the existing adapter
  continues to clear the resolved directory before writing warehouse files.

### Key decisions

- CSV output uses the same safe relative-path contract as SQL output; absolute
  paths and traversal outside `bd` are rejected.
- `-ot` remains the output-type selector and is not treated as a filesystem
  path.

### Gotchas

- The value passed to `-cd` is relative to `bd`, not the process working
  directory.

### Test coverage areas

- Nested CSV output resolution and traversal rejection, alongside existing SQL
  output path coverage.

## Task: Resolve updater SQL output beneath the base directory

### Title

Make `-o` and `-sf` output paths relative to `bd`

### Date/time completed

2026-09-30 11:08

### What was shipped

- SQL script paths supplied through `-o`/`--outputFile` and `-sf`/`--sqlFile`
  are now resolved beneath the validated base directory.
- Nested relative output paths are supported, and SQL output parent directories
  are still created by the existing output adapter.

### Key decisions

- Output paths use the same safe relative-path contract as the updater’s input
  directories; absolute paths and traversal outside `bd` are rejected.
- The `-sf` alias follows the same resolution behavior as `-o`.

### Gotchas

- The value passed to either SQL output option is a path relative to `bd`, not
  the process working directory.

### Test coverage areas

- Relative nested SQL output resolution for both option names and rejection of
  traversal-escaping paths.
- Full `bbb-update-database` test suite and repository-wide Gradle checks.

## Task: Add nightly database imports

### Title

Make `bbb-update-database` consume full and nightly Cricsheet layouts

### Date/time completed

2026-09-30 09:23

### What was shipped

- Added validated base, data, names, and player-registry input paths to the
  database updater, plus `--nightly`/`-n` selection.
- Full imports continue reading archive-named directories, while nightly
  imports scan only flat JSON files under `[base]/nightly/[data]`.
- Nightly match metadata is derived from each JSON document and register files
  remain at `[base]/[names]`.

### Key decisions

- The updater shares the downloader’s path contract so scheduled retrieval and
  import commands use the same base/data/names arguments.
- Nightly metadata uses `info.event.name`, an `Unknown` fallback, and warehouse
  mappings for Cricsheet match types; `mixedGender` remains enabled for female
  records.
- Full-mode archive metadata and output adapters remain unchanged.

### Gotchas

- Nightly mode expects extracted JSON files directly below the nightly data
  directory and does not recurse into archive-named child directories.
- The player registry is resolved independently of nightly JSON input, so a
  default names directory is `[base]/[data]`, not `[base]/nightly/[data]`.

### Test coverage areas

- CLI path resolution and traversal rejection, flat nightly file selection,
  format mapping, missing-event fallback, women’s metadata handling, and all
  existing database/output adapter tests.

## Task: Add nightly Cricsheet retrieval

### Title

Add fixed-source nightly Cricsheet refreshes

### Date/time completed

2026-09-30 07:57

### What was shipped

- Added `--nightly`/`-n` mode with fixed, ordered downloads of
  `recently_added_7_json.zip` and `recently_added_2_json.zip`.
- Nightly ZIPs are stored below `[base]/nightly/[data]/zips` and safely merged
  directly into `[base]/nightly/[data]`; register CSVs continue to refresh in
  `[base]/[names]`.
- Added deterministic coverage for CLI parsing, source order, independent
  failure continuation, directory reuse, stale-file preservation, flat layout,
  and collision precedence.

### Key decisions

- Nightly bypasses matches-page discovery and processes archive `7` before
  archive `2`, making the second archive the winner for duplicate paths.
- Nightly reuses existing directories and overwrites managed artifacts without
  requiring `--force`; `--force` remains accepted for script compatibility.
- Full retrieval retains archive-named extraction directories and its existing
  preflight behavior.

### Gotchas

- Nightly does not delete stale or unrelated files from the nightly data tree.
- `people.csv` and `names.csv` are shared register outputs rather than a
  nightly-specific copy.

### Test coverage areas

- Nightly aliases and path resolution, fixed request order, no page discovery,
  retry/atomic validation reuse, failure aggregation, ZIP safety, flat staged
  extraction, overwrite ordering, and full-mode regression behavior.

## Task: Group extracted Cricsheet archives by archive name

### Title

Extract each Cricsheet archive into its own named directory

### Date/time completed

2026-09-29 21:24

### What was shipped

- JSON archive contents are now extracted beneath a data-directory child named after the archive without its `.zip` suffix.
- Added regression coverage for the archive-specific extraction layout.
- Updated the developer runbook with the resulting directory structure.

### Key decisions

- `bbl_json.zip` is extracted into `bbl_json`, preserving separation between archive datasets.
- Existing staged extraction, ZIP safety validation, and replacement behavior remain unchanged.

### Gotchas

- Consumers must look below the archive-named directories rather than directly below the configured data directory.

### Test coverage areas

- Successful retrieval verifies both archive-named directories and their extracted files.

## Task: Implement Cricsheet bulk retrieval

### Title

Download Cricsheet JSON archives and register CSV files

### Date/time completed

2026-09-29 20:52

### What was shipped

- Added required `bd` and `dd` CLI options, optional `nd`, and `--force` with safe relative-path validation.
- Implemented sequential discovery and download of Cricsheet `_json.zip` archives, excluding `all_json.zip`.
- Implemented atomic ZIP/CSV writes, staged safe extraction, register-header validation, retries, per-file logging, and partial-failure reporting.
- Added deterministic parser, workflow, ZIP-safety, and HTTP-retry tests.

### Key decisions

- `bd` must be absolute; `dd` and `nd` resolve beneath it, with `nd` defaulting to `dd`.
- Existing target directories require `--force`; force overwrites managed files but does not delete unrelated content.
- Register files are fetched from `/register/people.csv` and `/register/names.csv`; network tests use local fixtures and an injectable transport.

### Gotchas

- A successful run requires every discovered archive and both register CSVs to pass validation; partial runs return exit status `1`.
- The command is option-driven and no longer accepts the old `--run`/`--output-directory` skeleton options.

### Test coverage areas

- CLI requirements, aliases, defaults, absolute/relative path validation, and traversal rejection.
- Archive filtering, deterministic order, atomic outputs, staged extraction, ZIP-slip rejection, CSV validation, force behavior, failure continuation, and transient HTTP retries.

## Task: Add Cricsheet data retrieval project

### Title

Add the `bbb-get-cricsheet-data` Kotlin command-line project

### Date/time completed

2026-09-28 16:07

### What was shipped

- Registered a standalone Gradle application for future Cricsheet data retrieval.
- Added Commons CLI support for help, version, and output-directory arguments.
- Added shared logging support with console and rolling-file Logback appenders.

### Key decisions

- Kept retrieval behavior out of this initial skeleton so source and network requirements can be defined separately.
- Reused the existing `bbb-update-database` CLI conventions and `bbb-shared` logging delegate, with a module-local runtime configuration.

### Gotchas

- Running the application with a non-help command currently logs the selected output directory but does not retrieve data yet.

### Test coverage areas

- Command-line parsing covers help, version, default/custom output directories, and unknown options.
- The new module is included in the repository-wide Gradle verification.

## Task: Distinguish female team names

### Title

Append `Women` to female team names during database updates

### Date/time completed

2026-09-28 14:44

### What was shipped

- Female team names now receive a ` Women` suffix when written by `bbb-update-database`.
- Team names remain unchanged for Women's Cricket Super League and Women's T20 Challenge matches.
- The normalized names are used consistently for team, innings, toss, and result references.

### Key decisions

- Competition exceptions are recognized from both the configured card directory and the JSON event name.
- Existing names ending in ` Women` are not suffixed again.

### Gotchas

- The JSON team names are also used in later lookup paths, so changing only the `dim_team` insert would break female match processing.

### Test coverage areas

- Regression tests cover ordinary female matches and both excluded competitions.
- The full Gradle `clean check` suite passed across all modules.

## Task: Show batter names only when they enter

### Title

Reduce repeated batter names in the scoresheet matrix

### Date/time completed

2026-09-28 09:14

### What was shipped

- Batter names now render as entry headers in their stable lane instead of repeating in every over row.
- Replacement batters receive a new header when they take over a released lane; delivery symbols and score details remain available on every row.

### Key decisions

- Prepared a `showName` flag in the scoresheet presenter so the Angular template remains a render-only view of the precomputed model.
- Tracked displayed players across each innings and rendered names on the first row where each player appears, including lane replacements.

### Gotchas

- A player can have multiple notation entries in one over when a wicket and replacement occur within that over; each newly entering player gets a header in the same lane.

### Test coverage areas

- Presenter coverage verifies initial names, omitted repeated names, and replacement-batter headers.
- Component coverage verifies entry headers in the DOM while preserving side-by-side lane rendering.

## Task: Use counted extra symbols in the scoresheet

### Title

Display wides, no-balls, leg-byes, and byes with counted delivery symbols

### Date/time completed

2026-09-27 21:39

### What was shipped

- Delivery notation now renders wides as `1wd`, no-balls as `4nb`, leg-byes as `2lb`, and byes as `3b`, using the corresponding extra count.
- All four counted extra symbols use the scoresheet's extra styling, and the innings notation hint documents the new abbreviations.

### Key decisions

- Applied the change to per-delivery batter notation in the prepared scoresheet view model, where the old generic `+` and `Δ` markers were generated.
- Kept the cumulative Extras ledger format unchanged because it represents running innings totals rather than individual delivery symbols.

### Gotchas

- Wicket notation still takes precedence if a delivery contains both a wicket and extra data; otherwise extra precedence follows wides, no-balls, leg-byes, and byes.

### Test coverage areas

- Presenter regression coverage verifies all four counted symbols and their extra CSS classification, alongside existing cumulative ledger behavior.
- Focused Angular presenter tests passed: 7/7.

## Task: Replace innings tabs with summary cards

### Title

Display match innings as selectable score summary cards

### Date/time completed

2026-09-27 21:32

### What was shipped

- Replaced the scoresheet innings tab strip with a responsive grid of up to four selectable summary cards.
- Cards show the batting team and ordinal innings, score, overs, run rate, completion state, and a clear `VIEWING` state for the active innings.
- Kept the delivery matrix focused on one selected innings and retained keyboard selection with arrow, Home, and End keys.

### Key decisions

- Derived overs from legal deliveries so wides do not advance the displayed over fraction or run-rate denominator.
- Displayed only metrics present in the scoresheet contract; unavailable result states such as lead, trail, and declared are not inferred.
- Used buttons with `aria-pressed` rather than tab roles because the cards are selectable summaries, not a tab interface.

### Gotchas

- Non-active cards use an `INNINGS N` badge because per-innings lead, trail, declared, and target values are not available in the response.
- The card grid changes from four to two to one columns at narrower widths while preserving DOM and keyboard order.

### Test coverage areas

- Component coverage verifies four-column summary-card rendering, labels, active state, click selection, keyboard movement, and selected scorebook content.
- Focused Angular tests passed: 16/16.

## Task: Add innings tab labels and totals

### Title

Show batting team, innings ordinal, and total on full-width scoresheet tabs

### Date/time completed

2026-09-27 21:22

### What was shipped

- Updated scoresheet tabs to display labels such as `England 1st Innings` and the prepared innings run total.
- Added the conventional `ao` suffix when the delivery ledger records ten or more wickets.
- Made tabs share the available header width while retaining a minimum width and horizontal scrolling on narrow screens.

### Key decisions

- Derived all-out status from ten recorded wickets because the current scoresheet contract has no separate all-out flag.
- Kept the total label in the prepared scoresheet view model so the template does not recalculate innings figures.

### Gotchas

- Innings with fewer than ten wickets show only their run total; the UI does not infer all-out status from a single dismissal or from the innings ending.

### Test coverage areas

- Component coverage verifies ordinal team labels, visible totals, accessible tab labels, active-panel switching, and keyboard focus movement.
- Presenter coverage verifies the `302 ao` label for a ten-wicket innings.
- Focused Angular tests passed: 16/16.

## Task: Add innings tabs to the scoresheet

### Title

Display each match innings in a labelled, selectable scoresheet tab

### Date/time completed

2026-09-27 17:35

### What was shipped

- Added one visible tab for every returned innings, labelled with its innings number and batting team.
- Rendered only the selected innings matrix instead of placing every innings linearly on the page.
- Added mouse and keyboard tab switching, including arrow, Home, and End navigation.

### Key decisions

- Kept innings selection local to the selected-match component because it is presentation state and does not belong in the API contract or URL.
- Used the first returned innings as the default and preserved the existing prepared scoresheet view model.

### Gotchas

- Inactive innings panels are removed from the DOM, so the page has one active delivery table at a time while tab labels remain available.

### Test coverage areas

- Component regression coverage verifies three labelled innings tabs, active-panel switching, ARIA state, and arrow-key focus movement.
- Focused selected-match tests passed: 11/11.

## Task: Restore match type and result controls

### Title

Preserve Match Type and Match Result values on the search page

### Date/time completed

2026-09-27 17:26

### What was shipped

- Ensured the Match Type and Match Result selects visibly restore their values from the search query.
- Preserved the existing URL-based search history and navigation behavior for all other filters.

### Key decisions

- Bound each option's selected state to the restored filter signal so the rendered controls cannot fall back to their first option.

### Gotchas

- The serialized query already contained both filters; the defect was limited to the select controls' rendered selection.

### Test coverage areas

- Match-search regression coverage verifies restored Match Type and Match Result DOM values.
- Focused Angular tests passed: 7/7.

## Task: Preserve match search filters when returning

### Title

Restore all match card search values after navigating back from results

### Date/time completed

2026-09-27 17:18

### What was shipped

- Replaced the current search history entry with the complete validated query before navigating to match results.
- Browser Back and the results-page refine links now restore team names, exact-match flags, venue, dates, match type, and result filters.

### Key decisions

- Reused the existing URL query codec and route-driven restoration instead of introducing a separate client-side state store.

### Gotchas

- Submission performs a replace navigation for the search entry followed by the normal results navigation, so browser Back returns to the populated search route.

### Test coverage areas

- Match-search regression coverage verifies every serialized filter and the replace-before-results navigation sequence.
- Focused Angular tests passed: 7/7.

## Task: Correct match search tab order

### Title

Place each Exact Match checkbox after its associated team field in keyboard navigation

### Date/time completed

2026-09-27 17:12

### What was shipped

- Reordered the match-search matchup controls so keyboard focus moves through Team Name, Exact Match, Opponents, Exact Match.
- Preserved the existing visual heading layout with feature-specific grid placement and avoided positive `tabindex` values.

### Key decisions

- Used natural DOM order for accessibility rather than manually forcing focus with `tabindex` values.

### Gotchas

- The visual checkbox placement is maintained by the matchup grid while the source order follows the desired keyboard sequence.

### Test coverage areas

- Match-search regression coverage verifies the four matchup input IDs in the required order.
- Full Gradle checks passed; Angular browser tests: 53/53.

## Task: Separate wicket notes by line

### Title

Render multiple wicket notes on separate lines within an over

### Date/time completed

2026-09-27 16:32

### What was shipped

- Changed scoresheet over-event formatting so each wicket, extra, or boundary note is separated by a newline.
- Preserved those line breaks in the notes column with feature-scoped preformatted whitespace styling.

### Key decisions

- Kept the existing prepared `row.notes` string contract and changed only its presentation formatting.

### Gotchas

- The notes column now uses `white-space: pre-line` so newline separators are visible without changing the table structure.

### Test coverage areas

- Presenter regression coverage verifies two wicket notes in one over are returned on separate lines.
- Focused Angular tests passed: 14/14.

## Task: Link home matches to scorecards

### Title

Open the selected match scorecard from the authenticated home page

### Date/time completed

2026-09-27 16:24

### What was shipped

- Updated the authenticated recent-match link in the home page to navigate to the selected-match scoresheet route.
- Added an accessible scorecard label to the icon-only link.

### Key decisions

- Reused the existing `/matches/:matchKey/scoresheet` route and scoresheet loading flow rather than adding a duplicate route.

### Gotchas

- The scorecard link remains hidden for unauthenticated visitors, matching the existing protected-route policy.

### Test coverage areas

- Home-component regression coverage verifies the match key, scoresheet route segment, and accessible link label.
- Focused Angular tests passed: 4/4.

## Task: Apply scoresheet quality review fixes

### Title

Prepare scoresheet presentation data once and isolate its styles

### Date/time completed

2026-09-27 15:50

### What was shipped

- Added a pure `scoresheet-presenter.ts` transformation that prepares grouped over rows, stable batter lanes, symbols, dismissal summaries, bowler figures, notes, and cumulative ledgers once per API response.
- Removed repeated scoresheet calculations from the Angular template and moved matrix presentation rules into the selected-match feature stylesheet.
- Separated per-over extra notes from cumulative ledger extra formatting and documented delivery-batter wicket attribution in the shared contract.

### Key decisions

- The prepared view model is immutable from the component's perspective and preserves the complete delivery list while ordering only the presentation copy.
- Until the warehouse exposes a dismissed-player field, a wicket is attributed to the delivery's `batter`, matching the existing contract behavior.
- Presenter and component tests use stable semantic classes and prepared properties rather than table-cell positions.

### Gotchas

- The shared contract still does not identify a dismissed player independently of the delivery batter; non-striker/run-out attribution remains a documented data limitation.
- The `Source ball` field remains in the response for identity and ordering but is not rendered in the matrix.

### Test coverage areas

- Presenter tests cover cumulative extra categories, over versus ledger formatting, stable batter lanes, non-striker placeholders, dismissal figures, and input immutability.
- Angular browser tests: 51/51 passed.

## Task: Remove source ball column from scoresheet

### Title

Keep source-ball identifiers out of the visible scorebook matrix

### Date/time completed

2026-09-27 15:39

### What was shipped

- Removed the `Source ball` header and source-ball values from the selected-match scoresheet matrix.
- Preserved the underlying `sourceBallId` data in the scoresheet contract for delivery identity and ordering.
- Updated table structure, empty-state span, styling, and Angular regression coverage.

### Key decisions

- This is a presentation-only change; no API, BFF, warehouse, or shared-contract fields were removed.

### Gotchas

- Batter-cell test positions shift left by one column after removing the source-ball column.

### Test coverage areas

- Angular regression coverage verifies the source-ball label is absent and both batter lanes remain side-by-side.

## Task: Show cumulative extras in the scoresheet ledger

### Title

Match the running extras presentation to the reference scorecard

### Date/time completed

2026-09-27 15:30

### What was shipped

- Changed the scoresheet Extras column from per-over values to cumulative innings totals.
- Displayed non-zero byes, leg-byes, wides, and no-balls in the reference order, followed by the cumulative total in parentheses.
- Added regression coverage across multiple overs, including an over with no new extras.

### Key decisions

- Extra totals are accumulated in the existing end-of-over ledger from the delivery-level extra fields.
- The existing `—` display is retained until the innings has recorded an extra.

### Gotchas

- Per-over extra summaries remain available for the notes column; only the dedicated Extras ledger column is cumulative.

### Test coverage areas

- Angular regression coverage verifies category accumulation, display ordering, total calculation, and persistence across an empty over.
- Angular browser tests: 48/48 passed.

## Task: Add boundary counts to dismissed batter figures

### Title

Show cumulative fours and sixes with dismissal scores

### Date/time completed

2026-09-27 11:40

### What was shipped

- Added cumulative four and six counts to dismissed batter figures in the historical scorebook.
- Matched the requested dismissal presentation with runs, legitimate balls faced, non-zero boundary counts, and a red `[OUT]` marker.
- Preserved the existing wide exclusion for balls faced and stable batter lane assignments.

### Key decisions

- Boundary counts are derived from each batter's `batterRuns` values in the complete innings delivery ledger.
- Zero-valued boundary categories are omitted so a batter with no sixes does not display `0x6`.

### Gotchas

- Dismissal scoring continues to follow the delivery batter because the scoresheet contract does not expose a separate dismissed-player field.

### Test coverage areas

- Angular regression coverage verifies cumulative runs, non-wide balls, one four, one six, and the `[OUT]` marker.
- Angular browser tests: 47/47 passed.

## Task: Show dismissed batter runs and balls

### Title

Replace dismissal labels with score-at-dismissal figures

### Date/time completed

2026-09-27 11:34

### What was shipped

- Replaced the `[OUT]` label in the historical scorebook batter lane with the dismissed batter's cumulative runs and balls faced.
- Counted every delivery except wides as a ball faced, including the dismissal delivery, and excluded wides from the displayed score.
- Added regression coverage spanning multiple overs and a wide delivery to verify the score-at-dismissal calculation.

### Key decisions

- Dismissal figures are calculated in the Angular presentation from the complete innings delivery ledger because the API already provides all required delivery data.
- Active batter rows retain their existing over-level notation; only dismissed batters show their cumulative score at dismissal.

### Gotchas

- The existing scoresheet contract associates a wicket with the delivery batter, so dismissal scoring follows that established UI attribution.

### Test coverage areas

- Angular regression coverage verifies cumulative runs, non-wide balls faced, removal of `[OUT]`, and existing single-batter wicket attribution.
- Angular browser tests: 47/47 passed; full Gradle checks and whitespace validation completed successfully.

## Task: Keep scoresheet batter lanes stable

### Title

Preserve Batter 1 and Batter 2 assignments through an innings

### Date/time completed

2026-09-27 11:09

### What was shipped

- Tracked batter lane ownership across the complete innings instead of rebuilding the pair independently for each over.
- Kept each batter in their assigned column when the strike changes and reused the dismissed batter's lane for the incoming batter.
- Added coverage for the Duckett/Bethell sequence, including the dismissal and replacement in the second lane.

### Key decisions

- Lane state is derived in the Angular presentation from delivery order because the existing scoresheet contract does not include a batting-position field.
- The delivery batter remains the dismissal association, matching the existing wicket-marker behavior.

### Gotchas

- If a single over contains both a dismissal and an incoming batter, both player entries can appear in the same lane while retaining the lane's identity.

### Test coverage areas

- Angular scoresheet regression coverage verifies stable lanes across strike changes, wickets, and incoming batters.
- Angular browser tests: 46/46 passed.

## Task: Correct scoresheet wicket attribution and totals

### Title

Show wicket markers only for the dismissed batter and cumulative wicket totals

### Date/time completed

2026-09-27 11:03

### What was shipped

- Limited the dismissed marker to the batter associated with the wicket delivery instead of also marking the non-striker as out.
- Changed the scoresheet `W` column from the current over's wicket count to the cumulative innings wicket total.
- Added regression coverage for single-batter dismissal attribution and cumulative totals across multiple overs.

### Key decisions

- The existing scoresheet contract associates wicket details with a delivery but does not provide a separate dismissed-player field, so the delivery batter is the UI's dismissal association.
- The existing running ledger remains the source of truth for cumulative wickets and runs.

### Gotchas

- Wicket events such as a non-striker run out cannot be attributed independently until the API contract exposes the dismissed player.

### Test coverage areas

- Angular scoresheet tests verify only the dismissed batter receives the wicket marker and that the ledger progresses from `1` to `2` wickets.
- Angular browser tests: 45/45 passed; full Gradle checks and whitespace validation completed successfully.

## Task: Show wicket details in the historical scorebook

### Title

Expose dismissal details in batter lanes and over notes

### Date/time completed

2026-09-27 10:58

### What was shipped

- Marked a batter as `[OUT]` when the delivery data identifies their dismissal and kept the wicket symbol in their inline batter notation.
- Added numbered wicket notes containing the dismissed batter, dismissal kind, and available fielder names; existing extras and boundary notes remain visible.
- Kept the change within the Angular scoresheet presentation because the API already returns wicket and fielder associations.

### Key decisions

- A wicket delivery marks either named batter involved in the delivery as dismissed, which also supports non-striker dismissals such as run outs.
- Missing wicket detail falls back to `Wicket`; no unsupported location or dismissal detail is fabricated.

### Gotchas

- The visual detector ran in degraded regex mode because its optional HTML/CSS parser modules are unavailable; reported warnings are pre-existing global styles and the existing wicket-row accent.

### Test coverage areas

- Angular regression coverage verifies inline dismissed-batter output and numbered notes with dismissal kind and fielder.
- Angular browser tests: 43/43 passed; `./gradlew clean check --no-daemon` and `git diff --check` passed.

## Task: Return complete historical scorecards in one roundtrip

### Title

Remove pagination from selected-match scorecard delivery responses

### Date/time completed

2026-09-27 10:02

### What was shipped

- Changed the scoresheet API, BFF, and Angular contract to return every delivery for the selected match in one response.
- Removed scorecard `page`, `pageSize`, `hasNext`, and `nextPage` metadata and removed SQL `limit`/`offset` from delivery loading.
- Updated the linear over-by-over scorebook copy and task checklist to distinguish complete scorecards from paged match search results.

### Key decisions

- Match search remains paged; selected scorecards are complete and are requested only by match key.
- Wicket and fielder bridge lookups continue to run against the complete delivery key set to avoid missing associations.

### Gotchas

- Legacy pagination query parameters are ignored for backward-compatible URLs; the BFF no longer forwards them upstream.
- Large scorecards now transfer and render as one response, so the existing horizontal table scrolling remains important on narrow screens.

### Test coverage areas

- Shared serialization and parser tests verify the non-paged scorecard contract.
- API, BFF, repository integration, Angular browser tests, and `./gradlew clean check --no-daemon` pass.

## Task: Replicate ACS match card search

### Title

Align historical match search with the ACS card-search workflow

### Date/time completed

2026-09-24 21:02

### What was shipped

- Refined the ACS-aligned team/opponent, exact-match, venue, date, match-type, and result search into a shared typed query codec and parser used by both HTTP adapters.
- Removed duplicated response rows, isolated repository filter policy, capped maximum-page continuation, and made unsupported neutral venue fail explicitly.
- Made Angular routing a stable shell with a routed recent-match home, canonical selected-match links, and complete structured search restoration through detail navigation.

### Key decisions

- Kept the ACS parameter names and result categories, but reject neutral venue because the warehouse has no proven neutral-location field.
- Kept one match collection plus metadata-only pagination and separate recent/search BFF result types.
- Required both team and opponent fields at the presentation boundary and preserved server-side token handling.

### Gotchas

- The live ACS page redirects anonymous visitors to login, so source inspection was used for the card-search behavior; the current BBB warehouse has no explicit neutral-venue flag.
- Search API tests accumulate invalid values in the shared parser; malformed finite options, unsupported neutral venue, date ranges, and paging boundaries are rejected consistently in both adapters.

### Test coverage areas

- Shared value-class and contract serialization tests.
- API authentication, validation, paging, BFF token/query forwarding, anonymous redirect, Angular route guards, card form, results states, presets, and query restoration.
- `./gradlew clean check --no-daemon`, focused API/BFF/shared tests, and 35 Angular browser tests.

## Task: Extract Angular component templates

### Title

Move Angular component HTML into external template files

### Date/time completed

2026-09-24 16:50

### What was shipped

- Extracted the inline templates from the three matches feature components into sibling `.component.html` files.
- Verified every Angular component under `bbb-web/ClientApp/src/app` uses an external `templateUrl`.
- Documented the external-template convention in `.junie/AGENTS.md`.

### Key decisions

- Kept each HTML file next to its component TypeScript file and matched the existing `AppComponent` naming convention.
- Preserved the existing markup and component behavior while changing only template location.

### Gotchas

- The repository contains an unrelated pre-existing trailing-whitespace issue in `docs/tasks/bbb-tasks-sprint-1-historical-match-scoresheet.md`.

### Test coverage areas

- Angular production build.
- All 30 Angular Karma tests.
- Repository-wide `./gradlew clean check --no-daemon`.

## Task: Remove protected profile test flow

### Title

Remove the test-only protected profile UI and API

### Date/time completed

2026-09-24 13:47

### What was shipped

- Removed the authenticated home-page profile section and `Fetch Protected Profile` action from `bbb-web`.
- Removed the Angular profile request, state, response model, and dedicated client/component tests.
- Removed the `/api/user/profile` route, profile-only authorization helpers, shared profile contract, `UserId` tiny type, and dedicated API/shared tests.
- Updated the current application architecture documentation to describe the remaining heartbeat, health, and matches slices.

### Key decisions

- Kept the BFF session endpoint and authenticated header controls because they support real login/logout behavior rather than the removed test flow.
- Kept JWT authentication and scope validation required by the matches endpoint, removing only human-profile authorization logic.

### Gotchas

- Historical authentication implementation plans retain references to the profile endpoint as records of the original scope; the current architecture map no longer advertises it.

### Test coverage areas

- Angular home-page and authentication-service tests no longer expose or request the profile flow.
- API tests continue covering public heartbeat, protected matches, validation, and health behavior.

## Task: Repair authentication navigation and signup configuration

### Title

Restore client routing and configure authentication registration explicitly

### Date/time completed

2026-09-24 12:58

### What was shipped

- Restored the standalone Angular `RouterLink` import so authenticated scorecard links compile and render correctly.
- Replaced the hardcoded frontend identity-server signup URL with a same-origin BFF redirect.
- Added an explicit `kbff.oidc.registrationUrl` setting and environment override for the registration destination.
- Moved the signup redirect into a dedicated authentication route alongside the BFF authentication routes.
- Added coverage for configuration lookup and redirect behavior independent of the OIDC authority.

### Key decisions

- Kept identity configuration server-side and no longer inferred registration from the OIDC authority.
- Used `/bff/signup` as the stable browser contract, avoiding environment-specific identity hosts in the Angular bundle.

### Gotchas

- Standalone Angular components must explicitly import every template directive, including `RouterLink`.
- Registration URLs may use a path different from the OIDC authority, so they must be configured independently.

### Test coverage areas

- Angular compilation and user-menu rendering, including authenticated scorecard links.
- BFF configuration lookup, signup redirect behavior, and existing web/API behavior.
- `./gradlew clean check --no-daemon`, `npm run build`, and `npm test -- --watch=false --browsers=ChromeHeadless` all pass.

## Task: Add dynamic application metadata to the bbb-web footer

### Title

Match the bbb-web footer to the dynamic stats-web metadata presentation

### Date/time completed

2026-09-24 10:32

### What was shipped

- Replaced the static Maiden Cricket footer with `Copyright © 2026 Kevin Jones` and dynamic data freshness, application version, and build-date text.
- Added the shared `ApplicationMetadata` contract and the bbb-web `GET /api/metadata` endpoint.
- Generated `version.properties` during Gradle resource processing from the configured release version and current build timestamp.
- Added Angular metadata loading with an unavailable fallback and regression coverage for both the BFF endpoint and rendered footer.

### Key decisions

- Reused the existing API envelope timestamp as the dynamic data freshness value, avoiding a new warehouse metadata table.
- Application versions can come from the `appVersion` Gradle property, `BBB_WEB_APP_VERSION`, or a `v*` GitHub tag, with `v0.1.0` as the local default.
- Formatting is performed server-side in the Europe/London timezone so the browser receives the exact footer text without locale-dependent rendering.

### Gotchas

- The current warehouse exposes response-generation time rather than a separate ingestion timestamp, so `Data last updated` represents the latest successful data response timestamp.
- When metadata cannot be loaded, the footer remains usable and displays `unavailable` values instead of hiding the copyright information.

### Test coverage areas

- Metadata endpoint response formatting and application-version propagation.
- Angular footer rendering of dynamic metadata and removal of the old static “Built with Angular and Ktor” text.
- `./gradlew clean check --no-daemon` across all modules, including `bbb-web:testClientApp`; all 21 Angular tests passed.

## Task: Show loading and error states instead of stale home-page matches

### Title

Prevent bbb-web from displaying fixture matches when recent match data fails to load

### Date/time completed

2026-09-22 16:56

### What was shipped

- Removed the bbb-web home-page fallback that rendered static sample matches before the API response or after a failed request.
- Added a full-page loading overlay with a blurred background and spinner while recent matches are loading.
- Preserved successfully loaded real matches during refreshes, while displaying the existing error banner and retry action when a refresh fails.
- Added regression coverage for pending requests and API failures, and removed the unused sample-match fixture.

### Key decisions

- `hasLoaded` remains false until the API returns successfully, so the initial failure state renders no match cards.
- A later refresh does not clear previously loaded real matches; the overlay communicates that replacement data is being fetched without presenting stale fixtures as current data.

### Gotchas

- The reference `acs-web` application was outside the project-scoped file inspection boundary, so the equivalent overlay behavior was implemented directly in bbb-web using the requested spinner and blur treatment.

### Test coverage areas

- Pending recent-match requests show the loading overlay and no sample teams.
- Recent-match API failures show the error banner, stop loading, and render no sample teams.
- Successful match responses and empty responses retain their existing behavior.
- `npm test -- --watch=false --browsers=ChromeHeadless`, `npm run build`, and `./gradlew clean check --no-daemon` all pass.

## Task: Fix Entrypoint Permissions and Container Image Packaging

### Title

Ensure executable permissions on container entrypoint binaries and add compose entrypoint wrappers

### Date/time completed

2026-09-21 08:00

### What was shipped

- Confirmed that published container images (`knowledgespike/bbb-api`, `knowledgespike/bbb-web`, and `knowledgespike/bbb-update-database`) had non-executable permissions (`0644`) on `/opt/app/bin/*` binaries, causing `exec: ...: permission denied` on startup when run directly.
- Updated `compose.yaml` with shell entrypoint wrappers (`chmod +x /opt/app/bin/bbb-* && exec /opt/app/bin/bbb-*`) for `api`, `web`, and `update-database` services to ensure containers run even if prebuilt images lack executable permissions.
- Updated upstream build definitions in `bbb-api/Dockerfile`, `bbb-api/Dockerfile.ci`, `bbb-web/Dockerfile`, `bbb-web/Dockerfile.ci`, `bbb-update-database/Dockerfile`, and `bbb-update-database/Dockerfile.ci` to explicitly set `chmod +x /opt/app/bin/*` before changing ownership to `appuser:appgroup`.
- Updated GitHub Actions workflow `.github/workflows/reusable-docker.yml` to restore executable permissions on downloaded distribution artifacts (`chmod -R +x ${{ inputs.artifact-path }}/bin || true`) before Docker image creation.

### Key decisions

- Fixed both layers: immediate runtime safety in docker compose via entrypoint wrappers, plus permanent upstream resolution in the Dockerfiles and CI packaging pipeline so future image builds produce executable binaries by default.

### Gotchas

- GitHub Actions `download-artifact@v4` unpacks artifact files with default runner umask (0644), stripping the executable bit set during `installDist`. Ensuring `chmod +x` in both the CI workflow before build and inside the final image Dockerfile prevents regression regardless of how distribution binaries are copied.

### Test coverage areas

- Docker container execution tests confirming non-executable permissions on published images and successful execution with the entrypoint wrapper.
- `docker compose config --dry-run` verifying YAML validity and execution parameter passing.
- Full `./gradlew check` across all modules (`bbb-shared`, `bbb-update-database`, `bbb-api`, and `bbb-web`).

## Task: Rename bb-shared to bbb-shared

### Title

Rename bb-shared module to bbb-shared

### Date/time completed

2026-09-19 09:38

### What was shipped

- Renamed `bb-shared` directory to `bbb-shared`.
- Updated `settings.gradle.kts` module inclusion list.
- Updated `bbb-api/build.gradle.kts`, `bbb-web/build.gradle.kts`, and `bbb-update-database/build.gradle.kts` project dependencies to reference `:bbb-shared`.
- Updated Dockerfiles across all three services (`bbb-api/Dockerfile`, `bbb-web/Dockerfile`, `bbb-update-database/Dockerfile`) to copy `bbb-shared/`.
- Updated GitHub Actions CI/CD workflows (`build-bbb-api.yml`, `build-bbb-web.yml`, `build-bbb-update-database.yml`) path filters.
- Updated documentation and guidelines (`docs/architecture/applications.md`, `.junie/AGENTS.md`).

### Key decisions

- Aligned all subprojects under consistent `bbb-` naming prefix (`bbb-shared`, `bbb-api`, `bbb-web`, `bbb-update-database`).

### Gotchas

- When renaming Gradle subprojects, ensure Docker build contexts and multi-stage COPY paths are updated alongside `settings.gradle.kts` and inter-project dependencies.

### Test coverage areas

- `./gradlew clean check --no-daemon`: Verified multi-module compilation and test suite execution.

## Task: Rename Projects from bb-XXX to bbb-XXX

### Title

Rename projects from bb-XXX to bbb-XXX (bbb-api, bbb-web, bbb-update-database)

### Date/time completed

2026-09-19 09:05

### What was shipped

- Renamed the three subprojects from `bb-api`, `bb-web`, and `bb-update-database` to `bbb-api`, `bbb-web`, and `bbb-update-database`.
- Updated `settings.gradle.kts` module inclusion list.
- Updated all Dockerfiles (`Dockerfile` and `Dockerfile.ci` for each of the three subprojects) with new paths and binary entrypoints.
- Updated `compose.yaml` build paths and container configurations.
- Updated GitHub Actions CI/CD workflows (`build-bbb-api.yml`, `build-bbb-web.yml`, `build-bbb-update-database.yml`).
- Updated `.gitignore` and `.idea/sqldialects.xml`.
- Updated source code references for `.env` lookup paths, HikariCP pool names, and output adapter comments.
- Updated database setup scripts and runbook documentation (`README-DEV.md`, `SETUP-DB.md`, `applications.md`, `database.md`).

### Key decisions

- Retained `bb-shared` module name as requested (renaming the three `bb-XXX` services).
- Maintained consistent `bbb-` naming across Gradle tasks, Docker build definitions, CI workflow triggers/artifacts, and documentation.

### Gotchas

- When subprojects are renamed, Gradle configuration cache must recompute and installDist distribution paths change from `build/install/bb-XXX` to `build/install/bbb-XXX`.

### Test coverage areas

- `./gradlew clean check --no-daemon`: Ran all multi-module checks and tests (JVM and Angular/Karma).
- `./gradlew :bbb-api:installDist :bbb-web:installDist :bbb-update-database:installDist --no-daemon`: Verified distribution packaging and binary generation for all three projects.

## What was shipped

- Gated the match scorecard links on potted match score cards on the home page so they are strictly visible only if the user is authenticated.
- Updated `AppComponent` and `app.component.html` in `bb-web/ClientApp` to import `RouterLink` and wrap the scorecard link in `@if (authService.isAuthenticated())`.
- Added unit tests in `app.component.spec.ts` verifying that the scorecard link is omitted for unauthenticated users and rendered with appropriate route targets when authenticated.
- Implemented real-time latest match querying in `bb-api` (`JooqMatchRepository`) selecting matches based on the dates played for the last 10 days.
- Aggregated innings totals (runs, wickets, legal balls, and completed/partial overs) across `fact_delivery` and `dim_innings` for both competing teams, supporting both single-innings and multi-innings formats.
- Formatted match results (victory margins in runs/wickets/innings, ties, draws, no results) and competition descriptors, falling back gracefully to `"MISSING"` when attributes are absent.
- Extended `MatchSummary` in `bb-shared` and `bb-web/ClientApp` to carry all match summary data while preserving full backwards compatibility through default arguments.
- Updated Angular `AppComponent` and `app.component.html` to render live matches using the full card layout on initial application load with dynamic format badge styling and date range display.
- Added unit and integration tests covering repository score calculations, date formatting, full match endpoint JSON serialization, and live MariaDB queries.
- Fixed parenthesized player name parsing bug in `Database.kt` where `substringBefore` was incorrectly called on an uninitialized empty string, ensuring player names like `Bob Willis (sub)` are properly parsed instead of erased.
- Optimized `Translate.kt` to perform direct map lookups on `cricSheet.info.registry.people` (`O(1)`), eliminating linear list filtering (`O(N)`) and imperative mutable map/list accumulation.
- Simplified powerplay calculations in `Database.kt` to use functional `mapIndexed` instead of mutable list accumulation and manual counter variables.
- Updated default Flyway database credentials, URL, and schema in `bb-update-database/build.gradle.kts` to target `acs_ball_by_ball` with user `ballbyball` and password `p4ssw0rd`.
- Aligned default database user (`ballbyball`) and password (`p4ssw0rd`) in `bb-api/src/main/resources/application.yaml` with `compose.yaml` and `.env.example`.
- Removed duplicate route registration function `registerApiRoutes` from `ApiModule.kt`, relying exclusively on direct registration in `moduleWithServices`.
- Removed redundant top-level scope aliases (`VALID_SCOPES`, `DEFAULT_VALID_SCOPES`) in `Security.kt`, referencing `JwtSettings.DEFAULT_VALID_SCOPES` directly.
- Adopted a **Sealed Class Error Hierarchy** rooted in `@Serializable sealed class Error(val message: String)` matching `acs-api`, providing a shared `message` across all domain, validation, and persistence errors.
- Updated `.junie/AGENTS.md` guidelines to prefer sealed class error hierarchies over sealed interfaces.
- Introduced **Tiny Types** via Kotlin inline value classes (`@JvmInline value class`) across `bb-shared` and `bb-api` (`Limit`, `MatchKey`, `SourceMatchId`, `MatchType`, `Season`, `UserId`).
- Implemented **Boundary Validation** at the HTTP presentation layer using **Arrow** (`Either`, `Raise`, and `zipOrAccumulate`).
- Created domain error hierarchy in `bb-shared` (`Error`, `LimitError`, `MatchKeyError`, `SourceMatchIdError`, `MatchTypeError`, `SeasonError`, `UserIdError`).
- Added routing response helpers in `bb-api` (`respondBadRequest`, `respondOk`) matching patterns from `acs-api`.
- Refactored `MatchesRoute` and `UserRoute` to validate incoming parameters at the presentation boundary before passing strongly typed values to domain services and repositories.
- Reorganized `bb-api` architecture from horizontal ports-and-adapters layering to vertical **Feature Slices** matching the structure in `acs-api`.
- Created four distinct feature slices in `bb-api`: `heartbeat` (public liveness), `health` (database health checks), `matches` (recent matches queries), and `user` (protected user profile).
- Colocated presentation (routes), domain (use-cases, services, repository interfaces), and data (jOOQ repositories) inside each feature directory.
- Updated `AGENTS.md` and `docs/architecture/applications.md` with guidelines and architectural diagrams for feature slices.
- Implemented `Envelope<T>` response wrapper across `bb-shared`, `bb-api`, `bb-web`, and the Angular client, returning data payload (`result`), error messaging (`errorMessage`), and response generation timestamp (`timeGenerated`).
- Updated all API routes (`/health`, `/api/heartbeat/alive`, `/api/matches`, `/api/user/profile`) in `bb-api` to return responses wrapped in `Envelope`.
- Updated `KtorMatchApiClient` and `WebRoutes` in `bb-web` to unpack and return `Envelope` wrapped payloads.
- Updated Angular `ClientApp` (`envelope.model.ts`, `MatchService`, `AuthenticationService`, and `AppComponent`) to consume typed `Envelope` responses and surface structured error messages.
- Implemented unit tests for `Envelope` in `bb-shared`, updated integration tests in `bb-api` and `bb-web`, and updated Angular Karma tests.
- Implemented three-tier access control model in `bb-api` distinguishing unauthenticated (`/api/heartbeat/alive`, `/health`), machine-authenticated (`/api/matches`), and user-authenticated (`/api/user/profile`) endpoints.
- Integrated `auth-jwt` in `bb-api` with JWKS key retrieval and claim inspection validating user `sub` and user roles.
- Integrated `kbff` (Backend-for-Frontend) in `bb-web` for secure HTTP-only encrypted session cookies (`bb_session`), anti-CSRF protection, and OIDC lifecycle (`/bff/login`, `/signin-oidc`, `/bff/user`, `/bff/logout`).
- Implemented `DefaultTokenService` in `bb-web` with OAuth2 client-credentials token caching to supply bearer tokens to `bb-api` for anonymous/background data calls.
- Implemented reactive `AuthenticationService` and `csrfInterceptor` in Angular `bb-web/ClientApp` using Angular signals (`session`, `isAuthenticated`, `isAnonymous`, `userName`, `email`, `logoutUrl`).
- Updated Angular header with dynamic Sign In / Sign Out controls and added user profile display component.
- Replaced HTMX front end in `bb-web` with a modern standalone Angular 19 application in `bb-web/ClientApp`.
- Preserved all original styling and layout elements (header, brand badge, hero search buttons, match cards, changelog) using Tailwind CSS.
- Implemented `MatchService` and TypeScript domain models matching shared JSON API contracts (`RecentMatchesResponse`, `MatchSummary`, `ApiError`).
- Configured Ktor `bb-web` backend with JSON `ContentNegotiation` and `singlePageApplication` resource serving.
- Wired Angular build and headless Karma testing into the Gradle lifecycle (`./gradlew check`).
- Added GitHub Actions CI/CD workflows and composite actions for multi-module Gradle verification, artifact packaging, and Docker Hub container publishing.
- Created multi-stage Dockerfiles and CI Dockerfiles for `bb-api`, `bb-web`, and `bb-update-database`.
- Created a `compose.yaml` development environment with MariaDB 11.4 (`acs_ball_by_ball`), automatic schema initialization, and connected Ktor web and API services.
- Moved module-specific Gradle configuration from the root build script into `bb-shared/build.gradle.kts` and `bb-update-database/build.gradle.kts`.
- Added MariaDB, PostgreSQL, and SQLite warehouse output adapters with dialect-specific direct JDBC and SQL-file support.

## Key decisions

- **Zero-Allocation Tiny Types**: Implemented domain types (`Limit`, `MatchKey`, `SourceMatchId`, `MatchType`, `Season`, `UserId`) as `@JvmInline value class` to eradicate primitive obsession at compile-time without runtime object allocation overhead.
- **Boundary Validation with Arrow**: Parameter parsing and validation is strictly isolated at the presentation layer (Ktor routes). Untrusted parameters are parsed using Arrow's `Raise` and `Either` DSL, returning `400 Bad Request` with `Envelope.failure(...)` immediately on validation errors, and passing only validated tiny types to domain services.
- **Encapsulated Construction**: Private constructors prevent illegal values from ever being instantiated, while companion factory methods (`invoke` with `Raise`, `of` returning `Either`, and `from` with `require`) support both boundary validation and internal mapping.
- **Inspect Claims for User Distinction**: Route authorization inspects claims (`sub` and presence of user roles) to differentiate client-credentials machine tokens from human user tokens, avoiding redundant verifier chains.
- **Backend-for-Frontend (BFF) Pattern**: Kept all access and refresh tokens out of the browser DOM/localStorage by terminating sessions in HTTP-only encrypted cookies via `kbff`.
- **Dedicated Machine Token Service**: Implemented in-memory cached client credentials token service in `bb-web` so `bb-api` can require authentication on all data endpoints while still serving public visitors.
- **CSRF Protection**: Functional Angular HTTP interceptor automatically appends `withCredentials: true` and `X-CSRF: 1` header to proxied requests.
- Each module now owns its plugins, repositories, dependencies, test configuration, and Java/Kotlin 21 configuration.
- The root build script retains root-level plugin setup, project metadata, and dependency-update version filtering.

## Gotchas

- In Ktor `testApplication`, redirect follow must be disabled (`followRedirects = false`) when testing external OIDC redirects (`/bff/login`) to prevent resolving external hosts.
- OIDC discovery responses parsed by Nimbus OIDC SDK require `issuer` and `jwks_uri` fields in mock metadata.
- The dependency update plugin uses the replacement `io.github.ben-manes.versions` ID.
- Generated SQL-file schemas must stay aligned with the dialect-specific warehouse migrations.
- When running `bb-api` locally, ensure `DB_PORT` and `DB_JDBC_URL` in `.env` and `bb-api/.env` point to port `3306` (where the `ballbyball` database runs), rather than `3307`. Port `3307` is used by the Identity Server's container (`identity-local-mariadb-1`), which rejects the `ballbyball` user credentials with `Access denied for user 'ballbyball'@'172.21.0.1'`.
- Access tokens issued by the Identity Server for client `ballbyball` contain audience `acs-bbb` and scopes `bbb.api` / `bbb.api.read`. In `bb-api`, JWT verification must accept multiple audiences (`withAnyOfAudience`) including both `acs-bbb` and `bb.api`, and scope validation must accept `bbb.api.*` alongside `bb.api.*`. If `JWT_AUDIENCE` was strictly `bb.api`, incoming bearer tokens are rejected with `401 Unauthorized`.
- In `bb-web`, `HttpClientFactory` previously hardcoded a 5000ms request timeout (`requestTimeoutMillis = 5_000`). When `bb-web` proxies requests like `/api/user/profile` to `bb-api`, `bb-api` verifies the token by fetching JWKS keys from `ids.local:8443`. On macOS, resolving `.local` domains under dual-stack DNS triggers a 5-second multicast DNS (Bonjour) wait for IPv6 unless IPv4 is explicitly preferred (`-Djava.net.preferIPv4Stack=true`), causing total request duration to exceed 5000ms (~5055ms) and `bb-web` to abort with `Request timeout has expired [url=http://localhost:8082/api/user/profile, request_timeout=5000 ms]`. Configured default HTTP client timeouts to 60s (with 30s connect timeout) via `application.yaml` / `.env`, added `-Djava.net.preferIPv4Stack=true` to Gradle JVM args and `applicationDefaultJvmArgs`, and added timeouts to `JwkProviderBuilder`.

## Feature: Scorecard Link Conditional Visibility

### Title

Conditionally render scorecard links on potted match cards only for authenticated users

### Date/time completed

2026-09-19 07:05

### What was shipped

- Gated the match scorecard link icon on each potted match card on the home page behind `@if (authService.isAuthenticated())`.
- Wrapped the scorecard link icon in an `<a class="match-card__link" [routerLink]="['/scorecard/cardbyid', match.matchKey]" title="View scorecard">` tag.
- Imported `RouterLink` into standalone `AppComponent`.
- Added unit tests in `app.component.spec.ts` testing authenticated and unauthenticated states.

### Key decisions

- **Preserve Unauthenticated Home Page**: Unauthenticated visitors can view all potted match scores and recent match data, but the link to drill down into detailed scorecards is rendered only when a user is signed in.

### Gotchas

- When using `RouterLink` in standalone Angular component tests, `provideRouter([])` must be configured in `TestBed`.

### Test coverage areas

- `app.component.spec.ts`: Unit tests verifying that `.match-card__link` is absent when anonymous and present with correct href when authenticated.

## Feature: List Latest Matches from Database

### Title

List latest matches based on played date for the last 10 days using JOOQ

### Date/time completed

2026-09-18 22:05

### What was shipped

- Implemented real-time latest match querying in `bb-api` (`JooqMatchRepository`) selecting matches based on the dates played for the last 10 days.
- Aggregated innings totals (runs, wickets, legal balls, and completed/partial overs) across `fact_delivery` and `dim_innings` for both competing teams, supporting both single-innings and multi-innings formats.
- Formatted match results (victory margins in runs/wickets/innings, ties, draws, no results) and competition descriptors, falling back gracefully to `"MISSING"` when attributes are absent.
- Extended `MatchSummary` in `bb-shared` and `bb-web/ClientApp` to carry all match summary data while preserving full backwards compatibility through default arguments.
- Updated Angular `AppComponent` and `app.component.html` to render live matches using the full card layout on initial application load with dynamic format badge styling and date range display.
- Added unit and integration tests covering repository score calculations, date formatting, full match endpoint JSON serialization, and live MariaDB queries.

### Key decisions

- **Two-Phase Query for Match Descriptors and Delivery Aggregation**: Queried match metadata on the most recent 10 dates first, followed by grouped delivery aggregation on those match keys. This prevents Cartesian joins across deliveries and delivers sub-20ms response times.
- **Graceful Fallback to MISSING**: Any absent match fields (competition, teams, scores, results, overs) fall back to `"MISSING"` in both the API layer and the UI template.

### Gotchas

- Java's `DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)` formats September as `"Sep"`, not `"Sept"`.

### Test coverage areas

- `JooqMatchRepositoryTest` in `bb-api`: 7 unit tests covering team score calculations, result formatting, format mapping, and date parsing.
- `JooqMatchRepositoryIntegrationTest` in `bb-api`: Live query execution against `acs_ball_by_ball` in MariaDB.
- `ApiModuleTest` in `bb-api`: Verifying `/api/matches?days=10` JSON envelope serialization.
- `AppComponent` and `MatchService` in `bb-web/ClientApp`: 18 Karma unit tests passing with ChromeHeadless.

## Database Parser and Configuration Cleanup

### Title

Fix parenthesized player name parsing, optimize registry lookup, and align database configuration defaults

### Date/time completed

2026-09-18 17:36

### What was shipped

- Fixed critical name erasure bug in `Database.kt:getNameParts` by evaluating `substringBefore("(")`.trim() on `personName` instead of an uninitialized empty string.
- Replaced linear list iteration in `Translate.kt` (`getPlayers`, `getOfficials`) with direct `O(1)` map lookups on `cricSheet.info.registry.people`.
- Converted powerplay calculation in `Database.kt` from imperative mutable list loops to functional `mapIndexed`.
- Updated default Flyway connection details in `bb-update-database/build.gradle.kts` to target `jdbc:mysql://localhost:3306/acs_ball_by_ball` with user `ballbyball` and schema `acs_ball_by_ball`.
- Aligned default database user and password in `bb-api/src/main/resources/application.yaml` with `compose.yaml` and `.env.example` (`ballbyball` / `p4ssw0rd`).

### Key decisions

- **Direct Map Lookups over Linear Scans**: `PlayersRegistry.people` is already a `Map<String, String>`; querying the map directly provides `O(1)` performance and eliminates list allocations.
- **Unified Local DB Defaults**: Standardized credentials across `compose.yaml`, `.env.example`, `application.yaml`, and `build.gradle.kts` to `ballbyball` / `p4ssw0rd` and `acs_ball_by_ball` schema.

### Test coverage areas

- `DatabaseTest` in `bb-update-database`: Added unit tests for parenthesized names in `getNameParts`, missing registry entries in `Translate.getPlayers`, and powerplay storage in `fact_delivery`.
- Full build and test suite passes cleanly across all modules (`./gradlew check --no-daemon`).

## Code Review Cleanup in bb-api

### Title

Remove duplicate route registration and unused scope aliases in bb-api

### Date/time completed

2026-09-18 17:15

### What was shipped

- Removed unused `Route.registerApiRoutes` extension in `ApiModule.kt` that duplicated route registration in `moduleWithServices`.
- Removed unused top-level scope aliases (`DEFAULT_VALID_SCOPES`, `VALID_SCOPES`) in `Security.kt` in favor of `JwtSettings.DEFAULT_VALID_SCOPES`.
- Added missing import for `userPrincipal` in `UserRoute.kt`.

### Key decisions

- **Consolidate Route Registration**: Centralized route setup in `moduleWithServices` to prevent duplicate route declaration drift.
- **Single Source of Truth for Scopes**: Scopes are configured via `JwtSettings` instead of duplicate top-level constants.

### Test coverage areas

- `ApiModuleTest` in `bb-api`: All 14 tests passing covering all routes and security checks.

## Sealed Class Error Hierarchy in bb-shared

### Title

Adopt Sealed Class Error Hierarchy matching acs-api

### Date/time completed

2026-09-18 16:55

### What was shipped

- Refactored domain error hierarchy in `bb-shared` from `sealed interface Error` to `sealed class Error(val message: String)` matching `acs-api`.
- Retained domain-specific error classes (`LimitError`, `MatchKeyError`, `SourceMatchIdError`, `MatchTypeError`, `SeasonError`, `UserIdError`, `ValidationError`) and added `DatabaseError(val stackTrace: String, message: String)`.
- Updated `.junie/AGENTS.md` to document the sealed class error hierarchy pattern with common `message` property.

### Key decisions

- **Sealed Class over Sealed Interface**: Standardizes `val message: String` at the root of the hierarchy so all error instances expose `message` without needing custom interface property implementations.
- **Subclass Constructor Forwarding**: Subclasses forward the descriptive message to `Error(message)` while optionally storing specialized error context (such as invalid input values or database stack traces).

### Test coverage areas

- `ValueClassesTest` in `bb-shared`: Validates error types and messages returned from Arrow Raise boundary validation.
- `EnvelopeTest` in `bb-shared`: Validates error message aggregation (`NonEmptyList<Error>`) in `Envelope.failure`.
- `ApiModuleTest` in `bb-api`: Validates 400 Bad Request error envelopes with domain error messages.

## Tiny Types and Boundary Validation with Arrow in bb-api and bb-shared

### Title

Implement Tiny Types with Kotlin value classes and Arrow boundary validation

### Date/time completed

2026-09-18 16:35

### What was shipped

- Introduced domain **Tiny Types** using Kotlin inline value classes (`@JvmInline value class`) in `bb-shared`:
  - `Limit`: Encapsulates query limit constraints (1..100, default 10).
  - `MatchKey`: Encapsulates positive database match surrogate keys (> 0).
  - `SourceMatchId`: Encapsulates non-negative external match IDs (>= 0).
  - `MatchType`: Encapsulates non-blank match format types.
  - `Season`: Encapsulates non-blank cricket season strings.
  - `UserId`: Encapsulates non-blank user subject identifiers.
- Added typed `Error` hierarchy in `bb-shared` (`Error`, `LimitError`, `MatchKeyError`, `SourceMatchIdError`, `MatchTypeError`, `SeasonError`, `UserIdError`).
- Configured Arrow (`arrow-core 2.2.3`) across `bb-shared` and `bb-api`.
- Implemented **Boundary Validation** at the presentation layer in `MatchesRoute` and `UserRoute` using Arrow `fold` and `Raise` DSL.
- Updated `MatchService` and `MatchRepository` to accept validated `Limit` instances, making invalid arguments unrepresentable in the domain layer.
- Added route response helpers in `bb-api` (`respondBadRequest` and `respondOk`).
- Updated `Envelope.failure` to support `NonEmptyList<Error>`.
- Updated `AGENTS.md` and `docs/architecture/applications.md` with guidelines on tiny types and boundary validation with Arrow.

### Key decisions

- **Zero Runtime Allocation**: Inlined value classes compile to primitive types on the JVM while preventing primitive obsession at compile time.
- **Boundary Validation Only**: Domain use cases and repository ports receive pre-validated value classes; HTTP query/path parameter parsing occurs strictly in route adapters.
- **Arrow Raise DSL**: Value class companion objects implement `operator fun invoke(value: RawType?)` in `Raise<Error>` context, allowing functional composition via `fold` and `zipOrAccumulate`.
- **Wire Compatibility**: Value classes serialize directly as primitive values under `kotlinx.serialization`, maintaining wire and JSON schema compatibility with Angular and external clients.

### Test coverage areas

- `ValueClassesTest` in `bb-shared`: Unit tests for range validation, blank rejection, JSON serialization, and `zipOrAccumulate` multi-error accumulation.
- `EnvelopeTest` in `bb-shared`: Tests for `NonEmptyList<Error>` error message formatting and typed envelope deserialization.
- `ApiModuleTest` in `bb-api`: 14 tests covering route authorization, valid/invalid limits (400 responses), and user profile extraction.
- Full `./gradlew check` clean across all modules including Karma Angular tests.

## Feature Slices Architecture in bb-api

### Title

Reorganize bb-api into vertical Feature Slices

### Date/time completed

2026-09-18 16:15

### What was shipped

- Reorganized `bb-api` codebase from horizontal ports-and-adapters layering into vertical **Feature Slices** matching the structure in `acs-api`.
- Created four distinct feature packages under `com.knowledgespike.ballbyball.api.feature`:
  - `feature.heartbeat`: Public unauthenticated liveness check (`GET /api/heartbeat/alive`).
  - `feature.health`: Contains `DatabaseHealth` domain interface, `JooqDatabaseHealth` repository implementation, and `HealthRoute` (`GET /health`).
  - `feature.matches`: Contains `MatchRepository` domain interface, `MatchService` use-case handler, `JooqMatchRepository` data adapter, and `MatchesRoute` (`GET /api/matches`).
  - `feature.user`: Contains `UserRoute` (`GET /api/user/profile`) secured with JWT and `userProtected` route plugin.
- Colocated presentation, domain, and data layers within each feature slice, removing monolithic global adapter and application directories.
- Updated `AGENTS.md` and `docs/architecture/applications.md` documenting Feature Slices rules and layer conventions.
- Kept cross-cutting infrastructure (database connection pooling, JWT security, server bootstrap) in top-level `config` and `bootstrap` packages.

### Key decisions

- **Colocation by Feature**: Rather than separating controllers, services, and repositories into disparate packages across the codebase, everything unique to a feature is placed in that feature's directory (`feature.<feature_name>`).
- **Independent Repositories**: Split `JooqMatchRepository` into `JooqMatchRepository` (under `feature.matches.data.repository`) and `JooqDatabaseHealth` (under `feature.health.data.repository`) so health checking is independent of match domain queries.

### Gotchas

- Strikt's assertion builder returned from an expression-body test method (e.g. `= runBlocking { expectThat(...).isFalse() }`) causes JUnit 5 to skip test execution because JUnit 5 requires test methods to return `void`/`Unit`. Explicitly typing the test method as `: Unit` resolves this.

### Test coverage areas

- `ApiModuleTest`: 13 tests covering all four feature routes (`heartbeat`, `health`, `matches`, and `user`) under authenticated, unauthenticated, and error scenarios.
- `DatabaseResourcesTest`: Integration test verifying database health reporting when database is unavailable.
- Full `./gradlew check` clean across all modules.

## Envelope response structure across bb-api, bb-web, and Angular client

### Title

Implement Envelope response contract with payload, error message, and generation timestamp

### Date/time completed

2026-09-18 10:55

### What was shipped

- Added `Envelope<T>` contract in `bb-shared` (`com.knowledgespike.ballbyball.contracts.Envelope`) with `result: T`, `errorMessage: String`, and `timeGenerated: Instant` using `kotlin.time.Clock.System.now()`.
- Implemented `Envelope.success(...)` and `Envelope.failure(...)` companion factory functions.
- Wrapped all endpoints in `bb-api` (`/health`, `/api/heartbeat/alive`, `/api/matches`, `/api/user/profile`, and security/authorization failures) in `Envelope`.
- Updated `KtorMatchApiClient` in `bb-web` to unpack `Envelope<RecentMatchesResponse>` and updated `WebRoutes` to wrap responses in `Envelope`.
- Added `envelope.model.ts` in Angular `ClientApp`, updated `MatchService` and `AuthenticationService` to return `Observable<Envelope<T>>`, and updated `AppComponent` to unwrap `response.result` and display `err?.error?.errorMessage`.
- Added unit tests for `Envelope` in `bb-shared`, updated integration tests in `ApiModuleTest` and `WebModuleTest`, and updated Karma tests in `ClientApp`.

### Key decisions

- **Standardized envelope structure**: Matched the pattern established in `acs-api` and `acs-web`, ensuring every response includes `result`, `errorMessage`, and `timeGenerated`.
- **Zero extra datetime libraries**: Used Kotlin's built-in `kotlin.time.Instant` and `kotlin.time.Clock.System.now()`, which are natively serialized to ISO-8601 strings by `kotlinx.serialization`.

### Gotchas

- None.

### Test coverage areas

- `bb-shared`: `EnvelopeTest` (5 tests verifying success, failure, default results, JSON serialization/deserialization with `Instant`).
- `bb-api`: `ApiModuleTest` (13 tests verifying public, machine, and user routes return valid `Envelope` bodies with `result`, `errorMessage`, and `timeGenerated`).
- `bb-web`: `WebModuleTest` (12 tests verifying client parsing and route serving).
- `bb-web/ClientApp`: Karma specs (17 tests verifying `MatchService`, `AuthenticationService`, and `AppComponent` with `Envelope`).
- Full `./gradlew check` clean across all 4 Gradle modules.

## Authentication and authorization across bb-web and bb-api

### Title

Implement three-tier JWT authentication, BFF session management with kbff, and reactive Angular authentication controls

### Date/time completed

2026-09-18 08:30

### What was shipped

- Implemented three-tier access control model in `bb-api` distinguishing unauthenticated (`/api/heartbeat/alive`, `/health`), machine-authenticated (`/api/matches`), and user-authenticated (`/api/user/profile`) endpoints.
- Integrated `auth-jwt` in `bb-api` with JWKS key retrieval and claim inspection validating user `sub` and user roles.
- Added public `/api/heartbeat/alive` route in `bb-api` returning status `{ "message": "Heartbeat: Alive" }` and secured `/api/matches` for machine/user bearer tokens.
- Added user-protected `/api/user/profile` route returning user profile details.
- Integrated `kbff` (Backend-for-Frontend) in `bb-web` for secure HTTP-only encrypted session cookies (`bb_session`), anti-CSRF protection, and OIDC lifecycle (`/bff/login`, `/signin-oidc`, `/bff/user`, `/bff/logout`).
- Implemented `DefaultTokenService` in `bb-web` with OAuth2 client-credentials token caching to supply bearer tokens to `bb-api` for anonymous/background data calls.
- Implemented reactive `AuthenticationService` and `csrfInterceptor` in Angular `bb-web/ClientApp` using Angular signals (`session`, `isAuthenticated`, `isAnonymous`, `userName`, `email`, `logoutUrl`).
- Updated Angular header with dynamic Sign In / Sign Out controls and added user profile display component.
- Updated documentation in `docs/architecture/applications.md`.
- Refactored security and claim extraction: consolidated duplicate `extractScopes`/`extractRoles` logic into functional `Payload.extractStringOrListClaims` extension, introduced domain `UserPrincipal`, encapsulated user authorization into `userProtected` route-scoped plugin, and made accepted API scopes configurable via `jwt.validScopes`.
- Optimized `DefaultTokenService` to cache OIDC discovery metadata (`token_endpoint`) using mutex protection and filter OIDC identity scopes automatically instead of hardcoding one-off conditionals.
- Hardened Angular `csrfInterceptor` to only attach credentials and `X-CSRF` headers to internal/relative requests, protecting external endpoints.
- Extracted static mock data out of `AppComponent` into dedicated fixture `sample-matches.fixture.ts`.
- Deduplicated `apiBaseUrl` configuration between `KbffConfigFactory` and `WebModule`, and bound HTTP client lifecycle in `moduleWithApiClient`.

### Key decisions

- **Inspect Claims for User Distinction**: Route authorization inspects claims (`sub` and presence of user roles) to differentiate client-credentials machine tokens from human user tokens, avoiding redundant verifier chains.
- **Backend-for-Frontend (BFF) Pattern**: Kept all access and refresh tokens out of the browser DOM/localStorage by terminating sessions in HTTP-only encrypted cookies via `kbff`.
- **Dedicated Machine Token Service**: Implemented in-memory cached client credentials token service in `bb-web` so `bb-api` can require authentication on all data endpoints while still serving public visitors.
- **CSRF Protection**: Functional Angular HTTP interceptor automatically appends `withCredentials: true` and `X-CSRF: 1` header to proxied requests.

### Gotchas

- In Ktor `testApplication`, redirect follow must be disabled (`followRedirects = false`) when testing external OIDC redirects (`/bff/login`) to prevent resolving external hosts.
- OIDC discovery responses parsed by Nimbus OIDC SDK require `issuer` and `jwks_uri` fields in mock metadata.
- Angular's default production optimization enables Beasties `inlineCritical: true`, which inlines critical CSS and rewrites external stylesheets to `media="print" onload="this.media='all'"`. When combined with strict default Content Security Policy headers from `kbff` (`style-src 'self'`, `script-src 'self'`), inline styles and the `onload` handler are blocked, preventing all CSS from applying. Resolving this requires setting `"optimization": { "styles": { "inlineCritical": false } }` in `angular.json` and configuring CSP in `KbffConfigFactory` (`style-src 'self' 'unsafe-inline' fonts.googleapis.com`, `font-src 'self' data: fonts.gstatic.com`).
- Ktor `monitor` events (`ApplicationStopped`) are hosted on `ApplicationEnvironment` and shared across auto-reloads. Subscribing without checking `if (app == this)` causes the listener from the newly created application to execute when the previous application is stopped during development auto-reload, immediately closing the new `HttpClient` (or `DatabaseResources`) and causing subsequent calls to fail with `JobCancellationException: Parent job is Completed`. The handler must check `if (app == this)` and dispose the registration handle.

### Test coverage areas

- `ApiModuleTest` (11 unit/integration tests): Public `/api/heartbeat/alive`, unauthenticated 401 rejections, machine token access to `/api/matches`, machine token 403 Forbidden on `/api/user/profile`, user token 200 OK with profile claims, and user token without explicit roles 200 OK.
- `WebModuleTest` (11 unit/integration tests): `DefaultTokenService` caching & single discovery resolution, `KtorMatchApiClient` bearer header injection, anonymous `/bff/user` 401, session claim extraction with CSRF tokens, `/bff/login` OIDC redirect, CSP headers, and auto-reload stopping event isolation.
- Angular unit tests (17 specs): `AuthenticationService` signals under 401/session responses, profile proxy requests, `AppComponent` anonymous Sign In rendering, authenticated Sign Out + profile display, and `csrfInterceptor` relative/external request filtering.
- Clean `./gradlew check` across all modules.

## Angular frontend migration

### Title

Transition web front end from HTMX to Angular

### Date/time completed

2026-09-17 16:16

### What was shipped

- Stripped out HTMX templates and `MatchHtmlRenderer` in `bb-web`.
- Created modern Angular application in `bb-web/ClientApp` using standalone components, Angular Signals, and Tailwind CSS.
- Preserved all original HTMX layout styling (header, brand identity, navigation, hero buttons, match cards, and changelog).
- Implemented `MatchService` with `HttpClient` consuming `/api/matches` returning typed JSON contracts (`RecentMatchesResponse`).
- Configured Gradle `bb-web` build with `node-gradle` (`npmInstallClientApp`, `buildClientApp`, `testClientApp`, `syncClientAppResources`) and Ktor `singlePageApplication`.

### Key decisions

- Used modern standalone Angular components (`bootstrapApplication`) without legacy `AppModules`.
- Configured Ktor `singlePageApplication` resource serving for client SPA and fallback routing while servicing `/api/` endpoints.
- Maintained `FAIL_ON_PROJECT_REPOS` compliance by using installed system Node runtime (`download.set(false)`).
- Included Angular Karma unit testing in the Gradle `check` lifecycle.

### Gotchas

- Angular control flow syntax requires `@` inside text (like email addresses) to be written as HTML entity `&#64;`.
- Gradle resource processing requires duplicate handling strategy (`DuplicatesStrategy.INCLUDE`) when merging generated client assets.

### Test coverage areas

- Angular unit tests (`app.component.spec.ts`, `match.service.spec.ts`) running headless in Karma / ChromeHeadless (8 specs).
- Ktor `WebModuleTest` verifying SPA shell serving at `/` and typed JSON response / error status mappings for `/api/matches`.
- Full project `./gradlew check` across all modules.

## Gradle configuration ownership

### Title

Move project configuration into module build files

### Date/time completed

2026-09-07 16:35

### What was shipped

- `bb-shared` and `bb-update-database` now configure themselves independently of root `allprojects` and `project` blocks.

### Key decisions

- Kotlin plugin versions remain declared at the root with `apply(false)` to avoid loading the Kotlin Gradle plugin multiple times.

### Gotchas

- Neither module currently contains test sources, so Gradle reports their test tasks as `NO-SOURCE`.

### Test coverage areas

- Clean compilation and `check` execution for both modules.

## Build configuration best-practices update

### Title

Align Gradle configuration with Java toolchain and reproducibility best practices

### Date/time completed

2026-09-07 16:38

### What was shipped

- Centralized plugin and dependency repositories in `settings.gradle.kts` with project repository declarations rejected.
- Applied the Foojay toolchain resolver through the settings plugin DSL.
- Standardized both modules on the Java 21 Kotlin toolchain and removed redundant Java compatibility settings.
- Externalized Flyway connection credentials to Gradle properties or environment variables.
- Replaced the deprecated dependency update plugin ID in the version catalog.

### Key decisions

- `jvmToolchain(21)` is the single source of truth for the Java and Kotlin compilation toolchain.
- `mavenLocal()` was removed to keep dependency resolution reproducible across environments.
- Test configuration uses lazy `Test` task configuration so future test tasks inherit the same JUnit Platform setup.

### Gotchas

- Flyway uses local development defaults for the URL and user, while the password defaults to empty and should be supplied through `flyway.password` or `FLYWAY_PASSWORD` when migration tasks run.

### Test coverage areas

- `./gradlew clean check --no-daemon` passed for both modules.
- No test sources currently exist, so Gradle reported `NO-SOURCE` for test tasks.

## Cricket warehouse schema

### Title

Add dimensional warehouse DDL

### Date/time completed

2026-09-07 17:14

### What was shipped

- Added `bb-update-database/migrations/mysql/2__initial_warehouse.sql`.
- Added conformed date, team, person, ground, match, innings, and wicket dimensions.
- Added match and delivery facts plus bridges for match person roles and delivery wickets.

### Key decisions

- Warehouse surrogate keys are separate from source identifiers, which remain on dimensions for ETL traceability.
- Fact grains are one row per match and one row per ball delivery.
- Many-to-many relationships are represented as factless bridge tables rather than duplicated fact rows.

### Gotchas

- The date dimension must be populated before loading facts; nullable date foreign keys preserve source matches with no start date.
- The migration creates schema only; ETL loading and role-code standardisation remain application responsibilities.

### Test coverage areas

- The migration executed successfully in an isolated MariaDB 12.3.2 database and created 11 tables.
- `./gradlew check --no-daemon` passed; modules currently contain no test sources.

## Warehouse persistence loader

### Title

Migrate database statements to the dimensional warehouse schema

### Date/time completed

2026-09-07 17:32

### What was shipped

- Reworked `Database` persistence from the operational tables to `dim_*`, `fact_*`, and `bridge_*` tables.
- Added surrogate-key lookups and inserts for teams, people, grounds, dates, matches, innings, deliveries, and wickets.
- Replaced role-specific person-match tables with `bridge_match_person` role assignments.

### Key decisions

- Source identifiers are allocated from warehouse tables while warehouse-generated keys are used for foreign-key columns.
- Delivery rows retain the source grain and link to innings, date, teams, and people through warehouse keys.
- Existing match-file detection uses `dim_match.file_name` so repeated loads remain idempotent.

### Gotchas

- The application entry point still contains pre-existing commented database invocation code; this change updates the active `Database` persistence implementation.
- Both modules currently have no test sources, so Gradle reports `NO-SOURCE` for test tasks.

### Test coverage areas

- `./gradlew :bb-update-database:compileKotlin --no-daemon` passed.
- `./gradlew check --no-daemon` passed.
- SQL search confirmed no active Kotlin statements reference the original operational tables.

## Warehouse output adapters

### Title

Add SQL script output using the adapter model

### Date/time completed

2026-09-07 18:05

### What was shipped

- Introduced `OutputAdapter` so warehouse persistence is independent of its destination.
- Added `SqlOutputAdapter` for direct MariaDB writes and `SqlScriptOutputAdapter` for executable SQL bulk-load scripts.
- Added `SQL`, `DATABASE`, and `SQL_FILE` output selection with `-o/--outputFile` and `-sf/--sqlFile` support.

### Key decisions

- `SQL` remains the default direct-database mode, matching the reference application; providing an output file selects offline SQL generation.
- Offline scripts use explicit surrogate keys and a single transaction so foreign-key relationships remain valid when loaded into an empty warehouse schema.
- SQL literals are escaped centrally in the script adapter, and duplicate source entities are suppressed in adapter state.

### Gotchas

- Generated scripts target the warehouse schema from `2__initial_warehouse.sql` and should be loaded after that migration has run.
- Offline key allocation starts at one and is intended for a new warehouse; loading into a populated database should use direct `SQL` mode or an explicitly coordinated merge process.

### Test coverage areas

- SQL literal escaping and duplicate person suppression.
- Explicit match/fact surrogate-key relationships in generated scripts.
- `./gradlew :bb-update-database:test --no-daemon` passed.

## CSV warehouse output

### Title

Add CSV output using the adapter model

### Date/time completed

2026-09-07 21:05

### What was shipped

- Added `CsvOutputAdapter` with one UTF-8 CSV file for each warehouse dimension, fact, and bridge table.
- Added `CSV` output selection and the `-cd/--csvDir` command-line argument.
- Added CSV escaping, MariaDB `\N` null values, stable surrogate keys, and duplicate relationship suppression.

### Key decisions

- CSV headers and column order follow `2__initial_warehouse.sql` so the files can be bulk-loaded with the warehouse DDL.
- CSV output uses the same in-memory key model as SQL and SQL-script output, preserving foreign-key relationships in a new warehouse.

### Gotchas

- The selected CSV output directory is cleared before generation, matching the reference project and preventing stale rows from previous runs.
- CSV files should be loaded after `2__initial_warehouse.sql`; nullable values are represented as MariaDB's `\N` marker.

### Test coverage areas

- CSV headers, escaping, duplicate suppression, null handling, and foreign-key values.
- `./gradlew :bb-update-database:test --no-daemon` and CLI help verification passed.

## Developer command runbook

### Title

Document CSV generation and database loading commands

### Date/time completed

2026-09-07 21:13

### What was shipped

- Added `README-DEV.md` with build, migration, parser, CSV, SQL-script, direct-database, and MariaDB bulk-load commands.
- Documented the foreign-key-safe CSV load order and the optional CSV files that may not be generated for empty source data.
- Added a maintenance checklist requiring new tools and command-line options to be documented as they are added.

### Key decisions

- CSV loading is documented with `LOAD DATA LOCAL INFILE` in dependency order so generated surrogate keys and foreign keys remain valid.
- Database credentials are shown as shell variables and Flyway environment variables rather than committed values.

### Gotchas

- The warehouse schema must be migrated before loading CSV output; generated SQL files recreate the warehouse schema themselves.
- CSV output is intended for a new or explicitly coordinated warehouse because the adapter writes its own surrogate keys.

### Test coverage areas

- Documentation examples were checked against the current CLI options, Flyway configuration, migration filenames, and CSV adapter output order.

## Dialect-specific warehouse adapters

### Title

Add PostgreSQL and SQLite output adapters

### Date/time completed

2026-09-08 07:00

### What was shipped

- Moved output adapters into dialect-specific packages under `database.adapter`.
- Added MariaDB, PostgreSQL, and SQLite direct JDBC adapters with shared warehouse persistence behavior.
- Added dialect-specific SQL-file adapters and schema generation for identity columns, conflict handling, transactions, and connection setup.
- Added `--database` selection for SQL-file output and automatic direct-adapter selection from JDBC URLs.
- Updated `README-DEV.md` and the database architecture documentation with adapter structure and commands.

### Key decisions

- Shared JDBC persistence remains in `JdbcOutputAdapter`; only dialect-specific conflict syntax and connection initialization are specialized.
- SQL-file generation uses separate wrapper classes for each dialect while sharing the warehouse row/key model.
- PostgreSQL uses the `cricsheet` schema, while SQLite uses its `main` database and enables foreign keys.

### Gotchas

- SQL-file output resets the warehouse tables and is destructive to existing warehouse data.
- PostgreSQL direct connections set `search_path` to `cricsheet`; SQLite direct connections enable foreign keys before loading rows.
- PostgreSQL runtime migration execution was not available locally because no PostgreSQL server or container image was present.

### Test coverage areas

- MariaDB, PostgreSQL, and SQLite SQL-file syntax and conflict clauses.
- SQLite execution of the generated warehouse schema against an in-memory database.
- Existing CSV and MariaDB SQL-file adapter behavior.

## Self-contained SQL-file warehouse reset

### Title

Drop and recreate warehouse tables in generated SQL files

### Date/time completed

2026-09-07 21:24

### What was shipped

- Updated `SqlScriptOutputAdapter` to emit foreign-key-safe `DROP TABLE IF EXISTS` statements and the complete warehouse `CREATE TABLE` definitions before generated rows.
- Added a regression test covering reset order, schema recreation order, and transaction placement.
- Updated `README-DEV.md` to document that SQL-file loads replace the existing warehouse data.

### Key decisions

- Only offline SQL-file output resets the schema; direct `DATABASE` output retains its existing upsert and insert behavior.
- DDL is emitted before `START TRANSACTION` because MariaDB implicitly commits DDL statements.

### Gotchas

- Loading a generated SQL file is destructive to all warehouse tables and requires the target database itself to already exist.
- The generated schema must remain aligned with `2__initial_warehouse.sql` when warehouse migrations change.

### Test coverage areas

- Foreign-key-safe table drop order and parent-before-child create order.
- Existing SQL literal escaping and generated foreign-key relationship tests.
- `./gradlew :bb-update-database:test --tests 'com.knowledgespike.ballbyball.parse.database.SqlScriptOutputAdapterTest' --no-daemon` passed.

## Warehouse database documentation

### Title

Document warehouse schema and relationships

### Date/time completed

2026-09-08 06:41

### What was shipped

- Added `docs/architecture/database.md` as the reference guide for the
  dimensional warehouse created by `2__initial_warehouse.sql`.
- Documented all dimensions, facts, bridges, grains, keys, columns, indexes,
  foreign keys, nullable relationships, and loading order.
- Added Mermaid entity-relationship, analytical-path, and load-order diagrams.

### Key decisions

- The documentation treats `2__initial_warehouse.sql` as the authoritative
  schema source and distinguishes source identifiers from warehouse surrogate
  keys.
- The table guide explicitly records role-specific joins and fact grains to
  help prevent double-counting in analytical queries.

### Gotchas

- `dim_date` must be populated before date-bearing facts, and all parent rows
  must exist before bridge rows because the migration declares restrictive
  foreign-key relationships without cascades.
- The migration creates schema only; ETL population and `role_code`
  standardisation remain application responsibilities.

### Test coverage areas

- Documentation was checked against the supplied migration DDL, the developer
  runbook’s warehouse loading order, and the existing warehouse project-memory
  entry.

## PostgreSQL and SQLite migrations

### Title

Add PostgreSQL and SQLite database migrations

### Date/time completed

2026-09-08 06:51

### What was shipped

- Added complete `1__initial_tables.sql` and `2__initial_warehouse.sql`
  migration pairs under `bb-update-database/migrations/postgres` and
  `bb-update-database/migrations/sqlite`.
- Added dialect selection to the Flyway Gradle configuration through
  `migration.database` or `FLYWAY_DATABASE`.
- Documented PostgreSQL and SQLite migration commands and dialect-specific
  schema behavior in `README-DEV.md` and `docs/architecture/database.md`.

### Key decisions

- MySQL remains the default Flyway target for backward compatibility.
- PostgreSQL uses identity columns and a `cricsheet` schema; SQLite uses
  `INTEGER PRIMARY KEY AUTOINCREMENT` and the built-in `main` database name.
- Indexes are separate `CREATE INDEX` statements outside MySQL table syntax so
  all three dialects preserve the same logical indexes.

### Gotchas

- PostgreSQL and SQLite migrations are dialect-specific and must not be mixed
  with the MySQL directory in one Flyway location.
- SQLite direct execution uses `PRAGMA foreign_keys = ON`; Flyway executions
  pass `-Pflyway.executeInTransaction=false` because SQLite does not change
  this connection setting inside an active transaction.

### Test coverage areas

- Both SQLite migrations execute together successfully and create the complete
  operational and warehouse table sets.
- PostgreSQL migration scripts passed repository whitespace validation; a local
  PostgreSQL server was unavailable for execution testing.

## Reproducible MariaDB and PostgreSQL test environments

### Title

Document and automate dual-database Docker setup

### Date/time completed

2026-09-08 08:27

### What was shipped

- Added `docs/setup/SETUP-DB.md` with detailed Docker, credential, migration,
  population, verification, lifecycle, and reset instructions.
- Added executable `docs/setup/setup-db.sh` to create or start both database
  containers, provision application users and databases, run Flyway, and load
  the same Cricsheet input into MariaDB and PostgreSQL.
- Linked the setup guide from the developer prerequisites in `README-DEV.md`.

### Key decisions

- MariaDB and PostgreSQL use separate pinned image tags, named volumes, and
  non-default host ports so both environments can run concurrently.
- Database creation and data loading remain idempotent and use the existing
  dialect-specific migrations and JDBC adapters rather than a second schema or
  loading implementation.

### Gotchas

- The script's default passwords are for local disposable environments only.
- Changing an existing container's image, port, volume, or administrative
  password requires `CONFIRM_RESET=1 docs/setup/setup-db.sh reset` first.

### Test coverage areas

- Shell syntax validation, executable help output, Docker status inspection, and
  repository whitespace validation.
- Full container migration and data-load execution remains dependent on Docker
  image availability and a local Cricsheet data directory.

## Delivery fielder bridge

### Title

Track fielders named on wicket dismissals

### Date/time completed

2026-09-08 10:38

### What was shipped

- Added `bridge_delivery_fielder` to the generated warehouse schema and to new
  Flyway migration `3__delivery_fielder.sql` for MySQL, PostgreSQL, and SQLite.
- Resolved Cricsheet wicket fielder names through `dim_person` and emitted the
  delivery, wicket, and fielder person relationship through every output mode.
- Updated the database architecture, developer runbook, and database setup
  documentation with the new table and loading order.

### Key decisions

- The bridge retains `wicket_key` as well as `delivery_key`, preserving the
  association when one delivery contains multiple wickets or fielders.
- The composite key (`delivery_key`, `wicket_key`, `person_key`) prevents
  duplicate fielder associations while allowing multiple fielders per wicket.

### Gotchas

- Fielder names must exist in the Cricsheet player registry because the bridge
  references `dim_person`.

### Test coverage areas

- End-to-end parser-to-SQL output for a caught wicket with a named fielder.
- CSV, SQL-script, generated-schema, and full Gradle verification coverage.

## BallByBall package namespace

### Title

Rename application packages from Cricsheet to BallByBall

### Date/time completed

2026-09-08 11:24

### What was shipped

- Renamed the application namespace from `com.knowledgespike.cricsheet` to
  `com.knowledgespike.ballbyball` across production code, tests, the Gradle
  application entry point, and logging configuration.
- Moved the Kotlin source and test package directories to match the new
  namespace and updated developer and architecture documentation paths.

### Key decisions

- Only the Kotlin package namespace changed; the `cricsheet` database/schema,
  migration identifiers, and Cricsheet input terminology remain unchanged.

### Gotchas

- Fully qualified test selectors and source-directory references must use the
  new `ballbyball` namespace.

### Test coverage areas

- Repository-wide stale-namespace search and full Gradle `clean check`.

## Fact match source filename

### Title

Retain source JSON filenames on match facts

### Date/time completed

2026-09-08 13:58

### What was shipped

- Added the non-null `file_name` column to generated `fact_match` schema output
  and all SQL/CSV output adapters.
- Added a versioned filename upgrade path for MariaDB, PostgreSQL, and SQLite,
  with backfill logic from `dim_match.file_name` for existing rows.
- Updated the database architecture, developer runbook, and Docker setup guide.

### Key decisions

- The application passes the original JSON filename from `Database.writeMatch`
  into every `fact_match` output mode.
- The original implementation used a versioned migration rather than
  rewriting the already-applied initial warehouse migration.

### Gotchas

- The original SQLite upgrade path used an empty-string default only while
  adding the column because SQLite cannot add a non-null column to a populated
  table without a default; existing values were immediately backfilled from
  `dim_match`.

### Test coverage areas

- SQL-script and CSV regression tests assert the filename column and emitted
  value in `fact_match` rows.
- SQLite generated-schema execution and the full Gradle verification suite.

## Consolidate fact match filename into the initial warehouse migration

### Title

Merge the fact match filename schema into migration version 2

### Date/time completed

2026-09-08 14:26

### What was shipped

- Added the non-null `fact_match.file_name` column directly to each dialect's
  `2__initial_warehouse.sql` migration.
- Removed the redundant follow-up migration files.
- Updated the developer, setup, and database architecture documentation to
  describe version 2 as the complete initial warehouse schema.

### Key decisions

- The project is still using fresh database setup rather than incremental
  migration upgrades, so the filename column is created with the rest of the
  warehouse schema.
- No backfill statement is needed in the initial migration because
  `fact_match` has no rows when it is created.

### Gotchas

- Databases that have already applied the removed follow-up migration should
  not rerun the consolidated version 2 migration; this change targets fresh
  early-stage environments.

### Test coverage areas

- Fresh SQLite schema execution and the full Gradle verification suite should
  confirm the initial warehouse migration exposes `fact_match.file_name`.

## Index fact delivery ball position

### Title

Add an index for `fact_delivery.ball_in_over`

### Date/time completed

2026-09-08 14:50

### What was shipped

- Added `idx_fact_delivery_ball_in_over` to the MariaDB, PostgreSQL, and SQLite
  version 2 warehouse migrations.
- Added the same index to generated dialect-specific warehouse schema output.
- Updated the database architecture documentation to describe ball-position
  filtering support.

### Key decisions

- The index is single-column because queries filtering by the ball position
  should not require a preceding `over_number` predicate.

### Gotchas

- Existing databases need the equivalent `CREATE INDEX` statement applied
  separately because the project currently manages fresh schemas through the
  consolidated version 2 migration.

### Test coverage areas

- Fresh SQLite migration index inspection and the full Gradle verification
  suite.

## Remove duplicated fact match filename

### Title

Keep the source filename only on `dim_match`

### Date/time completed

2026-09-08 15:29

### What was shipped

- Removed `fact_match.file_name` from the MariaDB, PostgreSQL, and SQLite
  warehouse migrations and generated warehouse schema.
- Removed the filename argument and value from the shared output contract,
  database writer, JDBC/SQL/CSV adapters, and regression tests.
- Updated the developer, setup, and database architecture documentation to
  describe `dim_match.file_name` as the single source filename field.

### Key decisions

- `MatchRecord.fileName` remains unchanged because the filename is required by
  `dim_match` for provenance and duplicate-match lookup.
- No incremental migration was added because the project is using the
  consolidated initial schema for fresh early-stage environments.

### Gotchas

- Existing databases containing the duplicate fact column must be rebuilt or
  altered separately before loading the updated output.

### Test coverage areas

- SQL-script, CSV, and end-to-end parser output now assert the filename only on
  `dim_match` and the reduced `fact_match` column set.
- Full Gradle verification and fresh SQLite schema execution.

## Add warehouse query indexes

### Title

Add requested lookup and delivery-sequence indexes to the initial warehouse schema

### Date/time completed

2026-09-08 21:18

### What was shipped

- Added the requested delivery sequence, match lookup, person-name, and wicket
  bridge indexes to the MariaDB, PostgreSQL, and SQLite version 2 migrations.
- Added the same indexes to generated dialect-specific warehouse schema output.
- Updated database architecture documentation and SQLite schema assertions.

### Key decisions

- Preserved the existing indexes and added the requested indexes with the exact
  names and column order supplied for query compatibility.

### Gotchas

- Existing databases need the equivalent `CREATE INDEX` statements applied
  separately because the project currently manages fresh schemas through the
  consolidated version 2 migration.

### Test coverage areas

- Generated SQLite warehouse schema contains all seven requested indexes.
- Full Gradle verification and migration execution checks.

## Suppress duplicate delivery fielder associations

### Title

Make duplicate `bridge_delivery_fielder` inserts safe and observable

### Date/time completed

2026-09-08 16:20

### What was shipped

- Added composite-key checks to parser, SQL-script, and CSV output so repeated
  `(delivery_key, wicket_key, person_key)` associations are emitted once.
- Added MariaDB, PostgreSQL, and SQLite conflict-safe SQL for direct JDBC and
  generated script output.
- Added warning logs containing the source filename and all bridge keys when the
  parser detects a repeated fielder, plus adapter warnings for suppressed calls.
- Documented the duplicate handling contract in the developer and database
  architecture guides.

### Key decisions

- The composite primary key remains the source of truth; duplicate rows are
  ignored because they represent the same delivery/wicket/person association.
- Duplicate detection happens before script/CSV emission and is also enforced
  at the database boundary for direct JDBC callers.

### Gotchas

- A warning identifies duplicate keys but does not identify which upstream
  Cricsheet object produced each occurrence beyond the source filename and
  wicket kind; inspect the corresponding JSON when investigating a warning.

### Test coverage areas

- Duplicate fielder payloads produce one bridge row.
- Repeated SQL-script and CSV adapter calls are suppressed.
- Dialect-specific PostgreSQL/SQLite SQL syntax and full Gradle verification.

## Add Ktor API and web applications

### Title

Create the JOOQ-backed API and static HTMX web application skeletons

### Date/time completed

2026-09-08 21:36

### What was shipped

- Added the `bb-api` Ktor application with environment-backed database
  configuration, Hikari pooling, JOOQ match queries, health checking, and
  JSON endpoints.
- Added the `bb-web` Ktor application with a static HTMX page and an API-backed
  HTML fragment route.
- Added shared serializable API contracts, Gradle version-catalog entries,
  application configuration, logging, and focused tests for both applications.
- Documented the module responsibilities and local run commands in
  `docs/architecture/applications.md` and `README-DEV.md`.

### Key decisions

- Kept `MatchRepository` as the persistence seam so HTTP routes do not depend
  directly on JOOQ or Hikari.
- Used JOOQ's DSL against the existing warehouse tables while postponing code
  generation until the schema and query surface are stable.
- Kept the web application framework-light: Ktor serves static HTML and HTMX
  performs the browser interaction, while the server performs the API call.

### Gotchas

- PostgreSQL connections must set `currentSchema=acs_ball_by_ball` in the JDBC
  URL because the warehouse tables live in that schema.
- Both applications require a migrated warehouse before the production API
  module can answer database requests.

### Test coverage areas

- API health response and match limit validation using an injected repository.
- Web API handoff and HTML rendering using a Ktor `MockEngine` client.
- Focused `bb-api` and `bb-web` Gradle test suites passed.

## Harden Ktor applications and adopt YAML configuration

### Title

Fix API/web runtime risks and replace HOCON with YAML

### Date/time completed

2026-09-09 07:50

### What was shipped

- Made the API database pool non-fail-fast so `/health` remains available when
  the configured database is unavailable.
- Moved blocking JOOQ/JDBC work to an IO dispatcher, selected JOOQ dialects from
  JDBC URLs, and ordered matches by start date with a deterministic key tie-breaker.
- Added explicit API validation and cancellation-safe exception handling, plus
  web-client timeouts and `502 Bad Gateway` mapping for upstream failures.
- Replaced both HOCON resources with Ktor `application.yaml` files and added the
  YAML configuration dependency through the version catalog.
- Removed committed IntelliJ module/VCS metadata and ignored regenerated module
  metadata.

### Key decisions

- The requested redundant database-index cleanup remains deferred; no database
  index definitions were changed in this task.
- `MatchRepository` is suspendable so transport code can call it without
  blocking Ktor request threads, while the repository owns dispatcher selection.

### Gotchas

- YAML environment substitutions use `${ENV:default}` rather than HOCON's
  optional substitution syntax.
- A database outage is reported by `/health`; a failed match query still uses
  the API's normal server-error response.

### Test coverage areas

- API health, startup with an unavailable database, valid/invalid limits, and
  JSON serialization.
- Web success, upstream HTTP failure, connection failure, and malformed JSON.
- Focused module tests and the full Gradle `check` task.

## Layer bb-api and bb-web around application seams

### Title

Introduce pragmatic layered Ktor application architecture

### Date/time completed

2026-09-09 09:04

### What was shipped

- Reorganised `bb-api` into bootstrap/configuration, inbound HTTP routes,
  application validation/use cases, outbound ports, and a JOOQ adapter.
- Reorganised `bb-web` into bootstrap, inbound routes/presentation, an
  application API-client port, and an outbound Ktor HTTP adapter.
- Added typed `MatchLimit` validation, separated `DatabaseHealth` from the
  match repository, and added shared `RecentMatchesResponse` and `ApiError`
  JSON contracts.
- Added the layered architecture, dependency direction, request flows, and
  contributor navigation to `docs/architecture/applications.md` and
  `AGENTS.md`.

### Key decisions

- Kept the layers inside the existing Gradle applications instead of creating
  more Gradle modules; the interfaces are sufficient seams for this scope.
- Kept `bb-shared` as the single source of API JSON contracts while keeping
  JOOQ rows and HTML rendering out of the shared module.
- Used sealed results for expected validation/upstream failures and retained
  cancellation-safe exception handling for unexpected failures.

### Gotchas

- Kotlin requires backticks when importing packages named `in`.
- `bb-api` and `bb-web` YAML module paths now point to their `bootstrap`
  packages.

### Test coverage areas

- API route validation, shared response serialization, health status, and
  unavailable-database startup.
- Web shared-contract decoding, HTML rendering, upstream status/connection,
  and malformed JSON failures through the outbound adapter.

## GitHub Actions CI/CD and Docker development environment

### Title

Add GitHub Actions workflows, Dockerfiles, and Docker Compose development environment

### Date/time completed

2026-09-09 11:15

### What was shipped

- Added GitHub Actions composite actions (`setup-jdk`, `use-gradle`, `docker-push`) and reusable workflows (`reusable-gradle.yml`, `reusable-docker.yml`).
- Added CI workflow (`ci.yml`) running `./gradlew clean check --no-daemon` on pull requests and pushes to `main`.
- Added release workflows (`build-bb-api.yml`, `build-bb-web.yml`, `build-bb-update-database.yml`) for Gradle building, distribution artifact uploading, and multi-architecture Docker image publishing.
- Added standalone and CI Dockerfiles for `bb-api`, `bb-web`, and `bb-update-database` using Temurin Alpine images and non-root application users.
- Added `compose.yaml` with MariaDB 11.4 (`acs_ball_by_ball`, `ballbyball` user, `p4ssw0rd` password), health checks, auto-initialization SQL scripts, API/Web services, and an on-demand `update-database` tool service.
- Added `.env.example` and updated developer documentation in `README-DEV.md`.

### Key decisions

- Used Temurin 21 Alpine for minimal image attack surface and efficient multi-architecture container runtime.
- Docker containers run as non-root user `appuser` with explicit logging and working directory permissions.
- Database initialization in Docker Compose mounts `docker/mariadb/init/` to pre-create both relational and dimensional warehouse schemas upon container startup.
- Reusable workflows and composite actions keep CI definitions DRY and consistent across microservices.

### Gotchas

- When running `bb-update-database` in Docker Compose, point `--connectionString` to `jdbc:mariadb://mariadb:3306/acs_ball_by_ball` within the internal network.
- Docker Compose development stack exposes MariaDB on 3306, API on 8081, and Web on 8080 by default.

### Test coverage areas

- `./gradlew clean check --no-daemon` validated across all modules.
- Docker Compose configuration and service dependency graph verified.

## Task: Implement BBB Sprint 1 historical match search slice

### Title

Add bounded historical match search contracts, API, BFF, and Angular results

### Date/time completed

2026-09-24 16:19

### What was shipped

- Added validated `SearchQuery`, `PageNumber`, and `PageSize` tiny types plus
  nullable-safe shared search result and pagination contracts.
- Added protected API search with Arrow boundary validation, deterministic
  warehouse-backed joins, bounded pagination, and safe failure logging.
- Added typed BFF search forwarding with server-side token handling and
  controlled upstream/malformed-response mapping.
- Added Angular search/results routes and states, query preservation, keyboard-
  accessible result actions, and a non-fabricating selected-match route shell.

### Key decisions

- Search uses free text across verified match identity fields and keeps the
  existing recent-match response unchanged.
- Search returns `{ matches, pagination }`, orders by date then `match_key`,
  and does not infer scores or results from unavailable facts.

### Gotchas

- The warehouse date relationship is nullable, so search date values remain
  nullable and null dates sort last.
- Scoresheet delivery, wicket, and fielder presentation remains outside this
  completed slice and is intentionally not represented by placeholder data.

### Test coverage areas

- Shared value-class validation and primitive JSON serialization.
- API authorization, accumulated invalid search parameters, and bounded result
  envelope behavior.
- BFF query encoding, bearer forwarding, search decoding, recent-match
  compatibility, upstream failures, and malformed JSON.
- Angular search/result route, service, state, accessibility, and build checks.

## Task: Persist declared innings and render scorecard declaration

### Title

Store innings declarations and append `dec` to scorecard card scores

### Date/time completed

2026-10-03 22:21

### What was shipped

- Added a non-null `declared` column with a false default to each dialect's
  `1__initial_tables.sql` and the generated relational schema.
- Propagated the parsed declaration through database adapters, jOOQ loading,
  the shared/API scoresheet contract, and the Angular scorecard presenter.
- Declared innings now render card scores such as `565/6 dec`.

### Key decisions

- Updated only the authoritative initial schema definitions; no follow-up
  migration was added.
- Existing innings rows are updated by the JDBC adapter when replayed, while
  fresh SQL-script and CSV output includes the declaration in the insert row.

### Gotchas

- Live integration databases must be reapplied from the initial schema before
  scoresheet queries can select `innings.declared`.

### Test coverage areas

- Database insert coverage verifies declared innings output.
- API/shared contract and jOOQ mapping checks pass with the new field.
- Angular presenter coverage verifies `565/6 dec`; full `clean check` passed.

## Task: Resolve thermo-nuclear quality review findings

### Title

Remove structural regressions from parser routing, team naming, migrations, and warehouse replay

### Date/time completed

2026-10-06 20:08

### What was shipped

- Made parsed/not-parsed routing authoritative by removing the stale counterpart
  whenever a file changes from success to failure or failure to success.
- Centralized female team-name exceptions in `TeamNamePolicy` and replaced the
  parser's growing event-condition chain with data-driven `StatsLookupPolicy`.
- Restored initial migration immutability, removed live alias data from schema
  migrations, and added additive migration `4__unique_source_ca_id.sql` for
  existing databases.
- Added source-ID reconciliation to JDBC, SQL-script, and CSV output adapters so
  replay can backfill a missing `sourceCaId` and reject conflicting ownership.

### Key decisions

- Alias migrations define structure only; operator-owned mappings remain explicit
  data managed outside versioned schema files.
- A source ID is reconciled before idempotent early return, preserving existing
  match identity while allowing a later parser retry to complete the link.
- The same team-name policy is used for stats lookup and warehouse normalization.

### Gotchas

- Existing databases must apply migration `4__unique_source_ca_id.sql`; editing
  the old initial migration is intentionally no longer the upgrade path.
- Reconciliation updates an existing CSV row by rewriting `matches.csv`, while
  SQL-script output emits an explicit `UPDATE` statement.

### Test coverage areas

- Parser output transitions between `parsed` and `not-parsed`.
- CSV and SQL-script source-ID reconciliation and existing updater behavior.
- Existing parser, linker, and updater suites continued to pass after the
  structural refactor.

## Task: Close final quality-review blockers

### Title

Make parser boundaries, adapters, and migration verification deterministic

### Date/time completed

2026-10-07 17:20

### What was shipped

- Parser arguments now reject overlapping input, parsed, and not-parsed paths
  and require both JDBC connection strings before application startup.
- Stats linking policy is separated from JDBC match queries and alias data
  access; the ball-by-ball alias connection is explicitly read-only.
- CSV and SQL-script source-ID reconciliation is staged until commit, with
  rollback/retry coverage and one atomic CSV replacement for pending updates.
- Alias migration 2 now represents the predecessor unique-name schema and
  migration 3 removes that constraint; dialect integration tests cover the
  MySQL and PostgreSQL sequences when Docker is available.

### Key decisions

- Required parser settings fail as command-line `ParseException` values rather
  than later `requireNotNull` startup failures.
- Migration compatibility is tested from the schema state produced by the
  preceding migration instead of manufacturing a hidden predecessor state in
  a test.

### Gotchas

- MySQL and PostgreSQL migration tests are skipped when Testcontainers cannot
  access a working Docker server; they execute fully in a Docker-enabled CI
  environment.
- Existing SQL-script and CSV output callers must use the adapter transaction
  lifecycle so staged source-ID updates are committed.

### Test coverage areas

- Parser path overlap and required connection setting validation.
- Linker repository composition, CSV atomic replacement, and SQL-script
  rollback behavior.
- SQLite migration upgrades plus Docker-backed MySQL/PostgreSQL migration
  execution and idempotence.

## Task: Make non-JDBC match output transactional

### Title

Stage CSV and SQL-script output until a match commits

### Date/time completed

2026-10-07 16:27

### What was shipped

- CSV adapters now stage rows in memory and publish replacement files only from
  `commit`, restoring both files and in-memory indexes if publication fails.
- SQL-script adapters now write to a temporary script and publish it only when
  the adapter closes successfully; rollback truncates staged statements and
  restores generated-key and lookup state.
- Added end-to-end `Database.writeMatch` regressions proving that failures after
  match rows are staged do not leave partial CSV files or SQL inserts.

### Key decisions

- A match transaction includes all relational output, not only source-ID
  reconciliation; `beginMatch`, `commit`, and `rollback` cover the complete
  adapter state.
- Generated SQL remains unpublished until the complete script is closed, while
  CSV commits use temporary replacements and restore prior files if any table
  publication fails.

### Gotchas

- Non-JDBC callers must use the adapter lifecycle and close the adapter to
  publish output; uncommitted staged output is intentionally discarded on
  failure.

### Test coverage areas

- CSV replacement failure and source-ID retry behavior.
- SQL-script reconciliation rollback and staged-script publication.
- Database-level failures after match rows were staged for both adapters.
- Full `./gradlew clean check --no-daemon` and `git diff --check` passed.

## Task: Publish CSV tables as one relational bundle

### Title

Make CSV commits atomic across the relational dataset

### Date/time completed

2026-10-07 16:37

### What was shipped

- CSV commits now copy the current output into a staged directory, replace all
  changed tables there, and publish the complete directory as one bundle.
- Directory publication keeps the previous output directory available for
  restoration when the bundle swap fails, preventing mixed table generations.
- Added regression coverage for one publication per multi-table commit and for
  preserving every existing CSV table after a failed publication.

### Key decisions

- The CSV output directory is the transaction boundary because the relational
  dataset consists of multiple interdependent table files.
- In-memory indexes are updated only after the bundle is published successfully;
  rollback restores the pre-transaction state.

### Gotchas

- The output directory is briefly swapped as a whole rather than replacing
  individual files, so readers should open a dataset after publication begins.

### Test coverage areas

- Multi-table bundle publication and failed-publication rollback.
- Existing CSV source-ID reconciliation and retry behavior.

## Task: Close final database quality blockers

### Title

Make adapter transactions mandatory and verify durable database output

### Date/time completed

2026-10-07 16:47

### What was shipped

- Made `beginMatch` and `rollback` mandatory operations on every
  `OutputAdapter` implementation.
- Added a durable CSV publication journal with restart recovery for interrupted
  directory swaps, including recovery regression coverage.
- Made MySQL and PostgreSQL migration tests fail rather than skip when their
  required database runtime is unavailable, and configured Docker API
  compatibility plus the MySQL test driver.

### Key decisions

- CSV publication retains the prior directory until the journal records a
  completed replacement, allowing startup recovery after a process crash.
- Dialect migration coverage is part of the normal test contract rather than
  an optional local check.

### Gotchas

- MySQL and PostgreSQL migration tests require Docker and use Testcontainers;
  Docker API `1.44` is configured for current Docker 29 environments.
- CSV publication metadata and recovery artifacts are kept beside the output
  directory while a swap is in progress.

### Test coverage areas

- Mandatory transaction lifecycle across JDBC, CSV, and SQL-script adapters.
- Interrupted CSV publication recovery and failed bundle publication.
- MySQL and PostgreSQL migration execution, duplicate aliases, and repeatable
  source-ID uniqueness migrations.
- Full `./gradlew clean check --no-daemon` and `git diff --check` passed.

## Task: Enforce parser input and output boundaries

### Title

Protect direct parser callers from overlapping directory trees

### Date/time completed

2026-10-07 17:09

### What was shipped

- Added a shared `ParseDirectoryLayout` validator for parser and CLI directory
  configuration.
- `CricSheetParser.parse` now normalizes paths and rejects equal or nested
  input, parsed-output, and not-parsed directories before creating outputs or
  walking input files.
- Added direct parser regressions for equal and nested input/output paths.

### Key decisions

- Directory safety is enforced at the public parser boundary rather than only
  at command-line argument parsing, protecting library callers as well.
- The parser operates on normalized absolute paths after validation so relative
  direct calls cannot bypass the same overlap rules.

### Gotchas

- Output directories must remain disjoint from the input tree and from each
  other; this prevents retries from re-ingesting generated or preserved files.

### Test coverage areas

- Direct parser rejection of equal and nested directory configurations.
- Existing parser routing and CLI boundary tests.
- Full `./gradlew clean check --no-daemon` and `git diff --check` passed.

## Task: Extract the CSV bundle publisher

### Title

Separate CSV publication from relational transaction coordination

### Date/time completed

2026-10-07 19:48

### What was shipped

- Extracted `CsvBundlePublisher` from `CsvOutputAdapter`.
- Moved CSV serialization, complete-bundle staging, atomic directory publication,
  durable journal writing, interrupted-publication recovery, and output
  preparation into the publisher.
- Kept relational indexes, transaction snapshots, commit/rollback decisions, and
  source-ID reconciliation in `CsvOutputAdapter`.

### Key decisions

- The adapter passes pending rows and updated match rows to the publisher only
  during commit; in-memory state is still updated after successful publication.
- The publisher remains injectable through the existing publication callback
  seam, preserving failure-injection coverage without coupling adapter tests to
  journal implementation details.

### Gotchas

- `CsvBundlePublisher` must retain the durable journal and restart recovery
  behavior because directory publication can be interrupted between swaps.

### Test coverage areas

- Multi-table bundle publication and failed-publication rollback.
- Source-ID reconciliation retry after publication failure.
- Interrupted publication recovery through the extracted publisher boundary.
- Full `./gradlew clean check --no-daemon` and `git diff --check` passed.

## Task: Add parser application lifecycle seams

### Title

Make parser startup wiring and cleanup boundary-testable

### Date/time completed

2026-10-07 20:05

### What was shipped

- Injected database-connection and parser factories into `Application.execute`.
- Preserved the production factories that open the stats connection normally,
  open the ball-by-ball alias connection read-only, construct `JdbcMatchLinker`,
  and run `CricSheetParser`.
- Added application boundary tests for missing arguments, successful dual-
  connection wiring, read-only alias configuration, and parser failure cleanup.

### Key decisions

- The application owns the connection lifecycle while factories provide a small
  test seam; parser code remains unchanged and still receives JDBC connections.
- The parser factory is created only after both connections are open, so the
  existing nested resource scopes close both handles on parser construction or
  execution failure.

### Gotchas

- Missing command-line settings must return the argument-error status before any
  database factory is invoked.

### Test coverage areas

- Successful parser application wiring and result status.
- Read-only configuration of the ball-by-ball alias connection.
- Cleanup of both database handles after parser failure.
- Application argument failure without opening connections.

## Task: Clean up failed CSV bundle creation

### Title

Remove temporary CSV bundles after staging failures

### Date/time completed

2026-10-07 20:04

### What was shipped

- Moved temporary bundle creation inside a cleanup-safe scope in
  `CsvBundlePublisher`.
- Added injectable copy and table-writer seams so staging failures can be
  exercised without changing production publication behavior.
- Added regressions proving failed CSV copying and table serialization remove
  their temporary `.csv-bundle-*` directories.

### Key decisions

- The publisher always deletes the temporary bundle from `finally` after the
  directory has been created, including failures before publication begins.
- Production copying and table serialization continue to use the existing
  filesystem implementations; injection is limited to focused tests.

### Gotchas

- A failure creating the temporary directory itself cannot leave a bundle because
  no directory has been created yet; cleanup begins immediately afterward.

### Test coverage areas

- Existing-file copy failure during bundle staging.
- Changed-table serialization failure during bundle staging.
- Full `./gradlew clean check --no-daemon` and `git diff --check` passed.

## Task: Separate stats linking policy from JDBC composition

### Title

Make stats match linking policy independently testable

### Date/time completed

2026-10-07 20:13

### What was shipped

- Added `StatsMatchLookupPolicy` as a pure deterministic planner for exact,
  individual-alias, and combined-alias candidates.
- Added `StatsTeamAliasResolver` and moved stats-team target validation out of
  `JdbcMatchLinker`.
- Changed `JdbcMatchLinker` to accept repository seams, while retaining its
  production constructor for separate stats and ball-by-ball JDBC connections.
- Added policy tests for exact-first ordering and multiple alias targets.

### Key decisions

- JDBC repositories are now constructed only by the connection-based adapter
  constructor; lookup policy tests do not require a database.
- Alias target validation remains fail-fast and uses the injected stats
  repository before an alias becomes a lookup candidate.
- Existing candidate ordering, margin retry, duplicate selection, and logging
  behavior remain unchanged.

### Gotchas

- The production compatibility constructor creates the validating alias
  resolver with the stats repository needed to validate stable `TeamId` values;
  callers with alternate data sources can inject their own resolver.

### Test coverage areas

- Exact, individual-alias, combined-alias, and duplicate-alias candidate order.
- Existing JDBC linker integration scenarios, including invalid targets,
  duplicate matches, reverse ordering, and margin retry.
- Full `./gradlew clean check --no-daemon` and `git diff --check`.

## Task: Preserve exact-first stats lookup

### Title

Prefer exact team names over alias resolution

### Date/time completed

2026-10-07 20:19

### What was shipped

- Changed `JdbcMatchLinker` to execute the exact two-team-name lookup before
  resolving persistent aliases.
- Added a regression proving an exact `CaId` is returned even when an alias
  resolver would reject a stale target.

### Key decisions

- Alias validation remains fail-fast when the exact lookup cannot resolve the
  match; stale aliases must not prevent a valid exact match.
- Alias candidate ordering remains delegated to `StatsMatchLookupPolicy` after
  the exact candidate has returned no result.

### Gotchas

- The margin retry applies the same exact-first ordering to its retry criteria.

### Test coverage areas

- Exact lookup call order with an invalid alias target.
- Existing alias, duplicate-result, reverse-order, and margin-retry scenarios.

## Task: Harden output reconciliation and parser linking

### Title

Make source linking mandatory and output moves portable

### Date/time completed

2026-10-07 20:31

### What was shipped

- Added an `AtomicMoveNotSupportedException` fallback for SQL-script output replacement.
- Made `CricSheetParser` require a `MatchLinker`; isolated parser tests now pass an explicitly named no-op linker.
- Centralized source CA-ID validation, ownership, pending updates, snapshots, commit promotion, and rollback restoration in `SourceCaIdState`, used by JDBC, SQL-script, and CSV adapters.

### Key decisions

- Adapter-specific persistence remains local while the shared state component commits pending assignments only after the output callback succeeds.
- Pending assignments for different canonical matches cannot claim the same source CA ID, and failed persistence leaves assignments retryable.

### Gotchas

- JDBC still queries the database as the source of truth; its shared state snapshot is restored alongside JDBC rollback.

### Test coverage areas

- Atomic-move fallback, source-ID ownership conflicts, snapshot restoration, and retry after persistence failure.
- Parser, SQL-script, CSV, and JDBC adapter regression suites.

## Task: Open stats connection read-only

### Title

Prevent unnecessary write access to the stats database

### Date/time completed

2026-10-08 07:08

### What was shipped

- Changed the Cricsheet parser application to request the stats database
  connection as read-only.
- Kept the existing read-only setting for the ball-by-ball database unchanged.

### Key decisions

- Stats matching is query-only, so its connection now uses the same read-only
  boundary as the other database used by the parser.

### Gotchas

- Database drivers still determine how strictly the JDBC read-only hint is
  enforced; the application now supplies the correct intent to the connection
  layer.

### Test coverage areas

- Application connection wiring asserts both stats and ball-by-ball connections
  are opened read-only.
- Full `./gradlew clean check --no-daemon` passed.

## Task: Reject ambiguous marginless stats matches

### Title

Prevent nondeterministic stats match linking when the result margin is absent

### Date/time completed

2026-10-08 07:15

### What was shipped

- Preserved a missing Cricsheet result margin as `null` instead of converting it
  to zero.
- Added explicit `StatsMatchAmbiguousException` handling when a lookup returns
  multiple distinct stats `CaId` values.
- Avoided retrying an already marginless lookup with identical criteria.

### Key decisions

- A match without a source margin may link only when the team/date lookup
  produces one distinct stats match; the linker no longer chooses the first row.
- The same ambiguity rule applies to exact and aliased team lookup candidates.

### Gotchas

- Existing callers must resolve duplicate same-day team matches before parsing
  can produce a linked envelope; the parser records the explicit lookup failure.

### Test coverage areas

- Marginless single-match resolution and margin-mismatch fallback.
- Ambiguous exact and aliased matches, including candidates with different
  stored margins.
- Full `StatsMatchLinkerTest` suite passed; `git diff --check` passed.

## Task: Resolve database and code-quality FIXMEs

### Title

Resolve SQL, pooling, transaction, adapter, and stats lookup issues

### Date/time completed

2026-10-09 10:12

### What was shipped

- SQL script bindings now substitute placeholders in one pass, and datasource
  pools are cached concurrently by connection URL and credentials.
- Transaction undo logs are private to their collections; relational snapshots
  now contain only counters, with rollback retained for SQL and CSV adapters.
- Output adapters use `RelationalOutputState` directly, source-reference change
  detection is delegated to shared state, and MariaDB table templates emit an
  explicit InnoDB suffix.
- Stats alias candidates are separate from exact candidates; database upsert
  wrappers were removed, and name classification uses `Locale.ROOT`.
- All eight findings in `docs/FIXME.md` are marked resolved.

### Key decisions

- Used `ConcurrentHashMap.computeIfAbsent` with a composite URL/user/password
  key so concurrent initialization shares pools without credential collisions.
- Kept transaction logs internal and made snapshots scalar-only rather than
  copying every relational map.
- Preserved exact-first stats matching while removing caller dependence on
  candidate ordering.

### Gotchas

- Passwords are part of datasource cache keys held in memory, consistent with
  the existing Hikari configuration that already retains credentials.
- `Locale.ROOT` makes the uppercase comparison independent of the process
  locale; the Turkish-locale test confirms expected name classification.

### Test coverage areas

- Regression test for question marks in SQL literals; writer rejects mismatched
  placeholder/value counts.
- Concurrent datasource cache access and credential-separated cache entries.
- Transactional map/set rollback, snapshot counter restoration, and adapter
  match rollback.
- MariaDB versus SQLite DDL, stats exact/alias lookup, and database name parsing.
- Full `./gradlew clean check --no-daemon` and `git diff --check` passed.

### Maintenance note (2026-10-09)
- `docs/project_memory.md` remains the live rolling log; prefer short sprint entries here and move deep narrative to dated files under `docs/memory/` when this file exceeds comfortable review size (~this file is large—trim historical sprint prose in a follow-up if needed).

## Task: Consolidate dialect schema migrations

### Title

Use one initial schema migration per database dialect

### Date/time completed

2026-10-12 16:11

### What was shipped

- Folded the team-alias triple unique index into `1__initial_tables.sql` for
  MySQL, PostgreSQL, and SQLite, and removed each dialect's second migration.
- Updated migration tests and architecture documentation for the single-file
  schema.

### Key decisions

- Keep dialect-specific idempotency: conditional index creation in MySQL and
  `CREATE UNIQUE INDEX IF NOT EXISTS` in PostgreSQL and SQLite.
- Preserve distinct team targets for one source alias while rejecting repeated
  `(source_system, source_team_name, stats_team_id)` triples.

### Gotchas

- This branch's migration consolidation is intended for fresh/rebuilt databases;
  do not replay the edited baseline against a database with prior Flyway history.
- One PostgreSQL Testcontainers connection attempt failed during SSL setup, but
  the isolated rerun and final project-wide check passed.

### Test coverage areas

- Migration DDL application and duplicate-alias rejection in MySQL, PostgreSQL,
  and SQLite.
- Alias preservation, relational schema parity, and full
  `./gradlew clean check --no-daemon` validation.

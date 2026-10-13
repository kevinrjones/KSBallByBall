# Save all converted matches under parsed

### Title

Save all converted matches under parsed

### Date/time completed

2026-10-13 09:28

### What was shipped

- Removed the `-np` / `--not-parsed` CLI option and its directory model; converted
  match envelopes are now written only beneath `parsed`.
- Stats lookup failures, including thrown lookup exceptions, leave
  `sourceCaId` null, still write the normalized envelope, and continue to be
  reported as a failed run.
- Updated parser documentation, CLI examples, and architecture guidance for the
  single-output behavior.
- Corrected the `SourceCaId` overlength error text and test case to match its
  existing 20-character database/value-class limit.

### Key decisions

- Keep conversion and output failures distinct from stats lookup failures:
  lookup failures preserve valid normalized output; conversion or output
  failures report errors and remove stale parsed output when possible.
- Preserve a non-zero process result for lookup failures so operators can
  distinguish unlinked output from successful stats reconciliation.
- Keep the existing `VARCHAR(20)` / `MAX_LENGTH = 20` source-CA ID contract;
  update the invalid test example instead of lowering the limit.

### Gotchas

- A file with a stats-link failure is present under `parsed` with null
  `sourceCaId`; consumers must not interpret a nonzero parser exit as meaning
  that no normalized output was produced.
- Invalid source JSON still cannot produce a normalized envelope.

### Test coverage areas

- Stats match linked, skipped, missing, ambiguous, invalid-alias, and thrown
  lookup cases, including continued processing and null-linked output.
- Parser CLI arguments with no `-np`; input/output path overlap and symlink
  validation.
- Conversion failure behavior and application connection lifecycle.
- `./gradlew clean check --no-daemon` passed across all modules.
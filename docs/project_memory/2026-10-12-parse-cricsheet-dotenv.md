# Load parse-cricsheet passwords from dotenv

### Title

Load parse-cricsheet passwords from dotenv

### Date/time completed

2026-10-12 16:51

### What was shipped

- Added dotenv loading to `bbb-parse-cricsheet` before CLI execution and let
  named password options resolve from JVM system properties as well as OS
  environment variables.
- Added an ignored local `.env` template and a shareable `.env.example` for
  stats and ball-by-ball database passwords.
- Documented local setup and precedence in `README.md`.

### Key decisions

- Followed the `bbb-api` convention: OS environment variables take precedence,
  existing JVM properties are preserved, and dotenv values fill only missing
  keys.
- Kept CLI options unchanged; they continue to accept variable names rather
  than password values.
- Ignore missing or malformed `.env` files so production and CI environments
  can continue to provide configuration externally.

### Gotchas

- `.env` values are loaded as JVM system properties for this process; the CLI
  still does not export them into the shell environment.
- The checked-in `.env.example` contains blank values; populate the ignored
  `.env` locally and do not commit secrets.

### Test coverage areas

- Password lookup from system properties and failure when a named password is
  absent from both environment sources.
- Dotenv loading into missing system properties while preserving existing
  properties.
- Full `./gradlew clean check --no-daemon` passed across all modules.
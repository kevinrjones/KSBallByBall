# Generating match keys

This document explains how match identity is derived in the Ball by Ball
applications, what the chance of a public-ID clash is, and what to do if one
occurs.

Related decisions:

- [ADR 0003: Deterministic source and canonical match identity](adr/0003-deterministic-match-identity.md)
- [ADR 0004: Deterministic public match identifiers](adr/0004-deterministic-public-match-identifiers.md)

## Identity layers

Each match has several different identifiers. They are not interchangeable.

| Identifier | Purpose | Exposed in URLs? |
| --- | --- | --- |
| `sourceRecordId` | Provider-scoped UUIDv5 for one source record | No |
| `rawContentDigest` | SHA-256 of exact downloaded bytes; revision evidence only | No |
| `canonicalMatchId` | Stable UUID of the reconciled/canonical match | No |
| `publicMatchId` | Ten-digit URL identifier derived from the canonical UUID | Yes |
| `matchKey` | Generated warehouse surrogate key for SQL joins | No |

### Derivation chain

```text
source file basename  →  sourceRecordId (UUIDv5)
sourceRecordId        →  canonicalMatchId (UUIDv5)
canonicalMatchId      →  publicMatchId (10-digit, hash-and-reject)
```

Important details:

- Public IDs are **not** derived from directory path, warehouse insertion order,
  or JVM/`hashCode()`.
- Directory moves do **not** change identity.
- For the current Cricsheet single-source flow, changing the **basename stem**
  changes the provider key and therefore changes source, canonical, and public
  IDs.
- Correcting file contents changes the digest only; the IDs stay the same when
  the provider-local key is unchanged.
- Re-importing the same canonical envelope reuses the same `publicMatchId`.

## Public match ID

`publicMatchId` is a ten-digit integer in the inclusive range:

```text
1_000_000_000 .. 9_999_999_999
```

That is **9,000,000,000** possible values.

### Algorithm

1. Take the canonical match UUID string.
2. Compute UUIDv5 using the fixed public namespace
   `0e4f5f84-3b4c-5f0b-a5c6-7d8e9f0a1b2c`.
3. Interpret the first eight UUID bytes as an unsigned 64-bit value.
4. Reduce that value modulo `9_000_000_000`.
5. Offset by `1_000_000_000`.

The intermediate public UUID is never serialized in API responses or URLs.
The API route is:

```text
/api/matches/{publicMatchId}/scoresheet
```

### Collision policy

`matches.public_match_id` has a **unique constraint**.

If two different canonical UUIDs map to the same ten-digit value, import
**fails loudly**. The loader does **not** probe for another free number.

That is intentional:

- probing would make IDs depend on empty-database state or import order
- sequential allocation is unique only relative to insertion order
- silent reassignment could point a URL at the wrong match

Same-match re-import is **not** a clash. Same canonical UUID always produces
the same public ID and is reused idempotently.

## Chance of a clash

UUIDs themselves are effectively collision-free here. The practical risk is only
the fold from UUID space into about nine billion public decimals.

Approximate birthday-paradox odds for a uniform 9-billion space:

| Matches already stored | Approx. chance next new match collides | Approx. chance any pair collides |
| --- | --- | --- |
| 1,000 | ~0.00001% | negligible |
| 10,000 | ~0.0001% | ~0.0006% |
| 100,000 | ~0.001% | ~0.06% |
| 1,000,000 | ~0.01% | ~5.4% |
| ~94,000 | — | ~0.05% |
| ~3,000,000 | — | ~50% |

For cricket-scale data (tens or low hundreds of thousands of matches), clash
risk is effectively **near zero**. It becomes a practical concern only if the
warehouse grows into the **millions of distinct canonical matches**.

## What to do if a clash happens

### Confirm it is a real clash

A real clash has:

- different `canonical_match_id` values
- the same derived `public_match_id`

Not a clash:

- re-importing the same envelope
- content corrections that keep the same provider key
- directory-only renames

### Will renaming the file fix it?

**Usually no, and it is the wrong first tool.**

| Action | Effect |
| --- | --- |
| Move directory / rename path only | No change to IDs |
| Rename basename (for example `12345.json` → `12345a.json`) | Changes Cricsheet provider key → new source UUID → new canonical UUID → new public ID |
| Correct file contents only | Digest changes; IDs stay the same |
| Re-import same envelope | Same public ID; no second match |

Basename rename can change the colliding match’s public ID in today’s
single-source Cricsheet path, but:

- it is a manual identity change, not a designed recovery procedure
- it can break lineage / “same match” continuity
- after true multi-provider reconciliation, filename may not be the stable
  input at all

### Practical recovery steps

1. Confirm different canonical UUIDs map to the same public ID.
2. Keep the already-loaded match as-is if its URL may already be shared.
3. Choose an explicit recovery path for the new match:
   - **Preferred long-term:** support a one-off override/mapping for that
     canonical ID, or widen/change the public-ID mapping via a versioned
     contract and migration.
   - **Operational workaround today:** change the Cricsheet provider-local key
     (basename) for the *new* match only, re-parse, and load the new canonical
     envelope.
4. Do **not** delete or reassign the already-published public ID of the first
   match.
5. Log both canonical UUIDs and the colliding public ID so the incident is
   auditable.

## Bottom line

- Clash probability is **very low** at normal cricket volumes.
- On clash, the loader **stops**; nothing silently points at the wrong match.
- **Renaming alone is not a clean fix.** Only changing the identity inputs, or
  the mapping contract, changes the public ID, and that should be treated as an
  exceptional, deliberate action.

## Implementation references

- Identity generation:
  `bbb-cli-shared/.../identity/MatchIdentity.kt`
- Public ID value type:
  `bbb-shared/.../types/values/PublicMatchId.kt`
- Warehouse uniqueness:
  `bbb-update-database/migrations/*/4__public_match_id.sql`
- Architecture overview:
  [applications.md](architecture/applications.md)

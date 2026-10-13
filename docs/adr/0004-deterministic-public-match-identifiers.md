# ADR 0004: Deterministic public match identifiers

## Status

Accepted

## Context

The warehouse has several different identity concepts. A provider source record
has a provider-scoped UUID, a reconciled match has a canonical UUID, and the
warehouse uses a generated numeric `match_key` for joins. Exposing either UUID
or `match_key` in a URL would couple the public contract to internal storage.

## Decision

Add `publicMatchId` as a separate ten-digit URL identifier in the inclusive
range `1_000_000_000..9_999_999_999`. It is derived from the canonical UUID using UUIDv5
with the immutable public namespace
`0e4f5f84-3b4c-5f0b-a5c6-7d8e9f0a1b2c` as the namespace and the canonical UUID
string as the name. The first eight UUID bytes are interpreted as an unsigned
64-bit value, reduced modulo `9_000_000_000`, and offset by `1_000_000_000`.

The namespace, byte extraction, UTF-8 encoding, bounds, and mapping are a
persisted contract. The public UUIDv5 intermediate is never serialized in API
responses or URLs. `public_match_id` is stored on `matches` with a unique
constraint; a collision fails loudly instead of probing for another number.

## Schema note

ADR 0005 supersedes this ADR's earlier dimensional-schema storage details.
`public_match_id` now lives on normalized `matches`, not `dim_match`; the
derivation algorithm, namespace, and public API contract in this ADR remain
accepted. Migration filenames `4__public_match_id.sql` and
`5__widen_public_match_id.sql` referred to the earlier migration history and
are not present in the current dialect migration sequence.

The API route is `/api/matches/{publicMatchId}/scoresheet`. Match summaries,
search results, and scoresheet contexts expose `publicMatchId`; repositories
resolve it to the internal `match_key` before existing fact and innings joins.

## Consequences

- Re-importing a canonical envelope produces the same public ID and URL.
- Future cross-provider reconciliation can retain one URL by deriving from the
  reconciled canonical identity rather than a provider key.
- Nine billion possible values cannot guarantee collision freedom forever;
  rejecting collisions prevents a wrong match from being served.
- Rows with canonical identity are backfilled when their envelope is replayed;
  rows without canonical identity remain null and are not inferred from filenames.
- Existing numeric warehouse foreign keys and source/canonical UUID semantics
  remain unchanged.

## Rejected alternatives

- Sequential allocation is unique only relative to insertion order.
- Collision probing makes an empty database and import order observable.
- Raw-file, canonical-JSON, or JVM `hashCode()` values do not preserve the
  required canonical identity semantics across corrections or machines.
package com.knowledgespike.ballbyball.identity

import com.knowledgespike.ballbyball.types.values.PublicMatchId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object DeterministicIdentity {
    fun sourceRecordId(namespace: UUID, providerRecordKey: String): SourceRecordId {
        require(providerRecordKey.isNotEmpty()) { "Provider record key must not be empty" }
        return SourceRecordId.from(uuidV5(namespace, providerRecordKey))
    }

    fun canonicalMatchId(namespace: UUID, stableKey: String): CanonicalMatchId {
        require(stableKey.isNotEmpty()) { "Canonical stable key must not be empty" }
        return CanonicalMatchId.from(uuidV5(namespace, stableKey))
    }

    /**
     * Derives the persisted ten-digit URL identifier from the canonical match identity.
     * Collisions are intentionally rejected by the warehouse unique constraint rather than
     * resolved by probing, so the result remains independent of insertion order.
     */
    fun publicMatchId(canonicalMatchId: CanonicalMatchId): PublicMatchId {
        val publicUuid = uuidV5(PublicNamespaces.MATCH_ID, canonicalMatchId.value.toString())
        val unsignedValue = java.lang.Long.remainderUnsigned(
            publicUuid.mostSignificantBits,
            PUBLIC_ID_RANGE
        )
        return PublicMatchId.from(PublicMatchId.MIN_PUBLIC_MATCH_ID + unsignedValue)
    }

    /**
     * Generates a UUID based on the version 5 (SHA-1 hash and namespace) standard.
     *
     * @param namespace The namespace UUID used as the base for generating the version 5 UUID.
     * @param stableKey The stable key (name) used as input to hash with the namespace.
     * @return A new UUID generated using the version 5 algorithm.
     */
    private fun uuidV5(namespace: UUID, stableKey: String): UUID {
        val namespaceBytes = ByteBuffer.allocate(UUID_BYTES)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val hash = MessageDigest.getInstance("SHA-1").digest(
            namespaceBytes + stableKey.toByteArray(StandardCharsets.UTF_8)
        )
        hash[6] = ((hash[6].toInt() and VERSION_MASK) or VERSION_FIVE).toByte()
        hash[8] = ((hash[8].toInt() and VARIANT_MASK) or IETF_VARIANT).toByte()
        val buffer = ByteBuffer.wrap(hash)
        return UUID(buffer.long, buffer.long)
    }

    fun rawContentDigest(rawBytes: ByteArray): Sha256Digest = Sha256Digest.from(
        MessageDigest.getInstance("SHA-256").digest(rawBytes).toHexString()
    )

    private const val UUID_BYTES = 16
    private const val VERSION_MASK = 0x0f
    private const val VERSION_FIVE = 0x50
    private const val VARIANT_MASK = 0x3f
    private const val IETF_VARIANT = 0x80
    private const val PUBLIC_ID_RANGE =
        PublicMatchId.MAX_PUBLIC_MATCH_ID - PublicMatchId.MIN_PUBLIC_MATCH_ID + 1
}

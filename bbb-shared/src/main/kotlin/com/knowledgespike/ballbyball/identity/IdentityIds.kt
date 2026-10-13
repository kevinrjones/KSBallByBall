package com.knowledgespike.ballbyball.identity

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID

sealed class IdentityError(val message: String) {
    class InvalidProviderId(value: String) : IdentityError("Provider ID must not be blank: '$value'")

    class InvalidUuid(value: String) : IdentityError("Value must be a valid UUID: '$value'")

    class InvalidSha256(value: String) : IdentityError("Value must be a lowercase SHA-256 digest: '$value'")

    class InvalidSourceCaId(value: String?) :
        IdentityError("Source CA ID must contain 1 to 10 non-whitespace characters: '$value'")
}

@Serializable
@JvmInline
value class ProviderId private constructor(val value: String) {
    companion object {
        context(raise: Raise<IdentityError.InvalidProviderId>)
        operator fun invoke(value: String): ProviderId {
            val normalized = value.trim().lowercase()
            if (normalized.isEmpty()) raise.raise(IdentityError.InvalidProviderId(value))
            return ProviderId(normalized)
        }

        fun of(value: String): Either<IdentityError.InvalidProviderId, ProviderId> = either { invoke(value) }

        fun from(value: String): ProviderId = requireNotNull(of(value).getOrNull()) {
            "Provider ID must not be blank"
        }
    }
}

@JvmInline
value class AliasSourceSystem private constructor(val value: String) {
    companion object {
        val CRICSHEET: AliasSourceSystem =
            AliasSourceSystem(ProviderNamespaces.CRICSHEET.toString())
    }
}

@Serializable(with = SourceRecordIdSerializer::class)
@JvmInline
value class SourceRecordId private constructor(val value: UUID) {
    companion object {
        fun of(value: String): Either<IdentityError.InvalidUuid, SourceRecordId> = parseUuid(value, ::SourceRecordId)

        fun from(value: UUID): SourceRecordId = SourceRecordId(value)
    }
}

@Serializable(with = CanonicalMatchIdSerializer::class)
@JvmInline
value class CanonicalMatchId private constructor(val value: UUID) {
    companion object {
        fun of(value: String): Either<IdentityError.InvalidUuid, CanonicalMatchId> = parseUuid(value, ::CanonicalMatchId)

        fun from(value: UUID): CanonicalMatchId = CanonicalMatchId(value)
    }
}

@Serializable
@JvmInline
value class Sha256Digest private constructor(val value: String) {
    companion object {
        private val sha256Pattern = Regex("^[0-9a-f]{64}$")

        context(raise: Raise<IdentityError.InvalidSha256>)
        operator fun invoke(value: String): Sha256Digest {
            if (!sha256Pattern.matches(value)) raise.raise(IdentityError.InvalidSha256(value))
            return Sha256Digest(value)
        }

        fun of(value: String): Either<IdentityError.InvalidSha256, Sha256Digest> = either { invoke(value) }

        fun from(value: String): Sha256Digest = requireNotNull(of(value).getOrNull()) {
            "Value must be a lowercase SHA-256 digest"
        }
    }
}

@Serializable
@JvmInline
value class SourceCaId private constructor(val value: String) {
    companion object {
        context(raise: Raise<IdentityError.InvalidSourceCaId>)
        operator fun invoke(value: String?): SourceCaId {
            if (value.isNullOrEmpty() || value.length > MAX_LENGTH || value.any(Char::isWhitespace)) {
                raise.raise(IdentityError.InvalidSourceCaId(value))
            }
            return SourceCaId(value)
        }

        fun of(value: String?): Either<IdentityError.InvalidSourceCaId, SourceCaId> = either { invoke(value) }

        fun from(value: String): SourceCaId = requireNotNull(of(value).getOrNull()) {
            "Source CA ID must contain 1 to 10 non-whitespace characters"
        }

        const val MAX_LENGTH = 20
    }
}

object ProviderNamespaces {
    val CRICSHEET: UUID = UUID.fromString("f3c879d8-6d5c-5f33-9c7f-4f6c2dcff18b")
    val TEST_PROVIDER: UUID = UUID.fromString("ba6f14c2-6d85-5dde-bf22-f40ff595e62f")
}

object CanonicalNamespaces {
    val SINGLE_SOURCE: UUID = UUID.fromString("6b456afa-09d1-5cc1-a820-77617ad17ca2")
}

object PublicNamespaces {
    val MATCH_ID: UUID = UUID.fromString("0e4f5f84-3b4c-5f0b-a5c6-7d8e9f0a1b2c")
}

private fun <T> parseUuid(value: String, factory: (UUID) -> T): Either<IdentityError.InvalidUuid, T> = either {
    val uuid = runCatching { UUID.fromString(value) }
        .getOrElse { raise(IdentityError.InvalidUuid(value)) }
    factory(uuid)
}

object SourceRecordIdSerializer : KSerializer<SourceRecordId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SourceRecordId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SourceRecordId) = encoder.encodeString(value.value.toString())

    override fun deserialize(decoder: Decoder): SourceRecordId = SourceRecordId.of(decoder.decodeString()).fold(
        ifLeft = { error -> throw IllegalArgumentException(error.message) },
        ifRight = { it }
    )
}

object CanonicalMatchIdSerializer : KSerializer<CanonicalMatchId> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CanonicalMatchId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: CanonicalMatchId) = encoder.encodeString(value.value.toString())

    override fun deserialize(decoder: Decoder): CanonicalMatchId = CanonicalMatchId.of(decoder.decodeString()).fold(
        ifLeft = { error -> throw IllegalArgumentException(error.message) },
        ifRight = { it }
    )
}
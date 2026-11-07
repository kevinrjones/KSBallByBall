package com.knowledgespike.ballbyball.types.values

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import com.knowledgespike.ballbyball.types.error.PublicMatchIdError
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class PublicMatchId private constructor(val value: Long) {
    init {
        require(value in MIN_PUBLIC_MATCH_ID..MAX_PUBLIC_MATCH_ID) {
            "publicMatchId must be a ten digit number"
        }
    }

    companion object {
        private val PUBLIC_MATCH_ID_PATTERN = Regex("^[1-9][0-9]{9}$")

        context(raise: Raise<PublicMatchIdError>)
        operator fun invoke(value: Long?): PublicMatchId {
            if (value == null || value !in MIN_PUBLIC_MATCH_ID..MAX_PUBLIC_MATCH_ID) {
                raise.raise(PublicMatchIdError("publicMatchId must be between 1000000000 and 9999999999", value))
            }
            return PublicMatchId(value)
        }

        context(raise: Raise<PublicMatchIdError>)
        operator fun invoke(value: String?): PublicMatchId {
            if (value == null || !PUBLIC_MATCH_ID_PATTERN.matches(value)) {
                raise.raise(PublicMatchIdError("publicMatchId must be a ten digit number", value?.toLongOrNull()))
            }
            return PublicMatchId(value.toLong())
        }

        fun fromRaw(value: String?): Either<PublicMatchIdError, PublicMatchId> = either {
            invoke(value)
        }

        fun of(value: Long): Either<PublicMatchIdError, PublicMatchId> = either {
            invoke(value)
        }

        fun from(value: Long): PublicMatchId {
            require(value in MIN_PUBLIC_MATCH_ID..MAX_PUBLIC_MATCH_ID) {
                "publicMatchId must be between 1000000000 and 9999999999"
            }
            return PublicMatchId(value)
        }

        const val MIN_PUBLIC_MATCH_ID: Long = 1_000_000_000L
        const val MAX_PUBLIC_MATCH_ID: Long = 9_999_999_999L
    }

    override fun toString(): String = value.toString()
}

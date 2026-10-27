package com.knowledgespike.ballbyball.parsecricsheet

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either

/**
 * Represents an error that occurs in relation to Stats Player ID.
 * This is a sealed class that can have various specific types of errors.
 *
 * @property message Descriptive message related to the error.
 */
sealed class StatsPlayerIdError(val message: String) {
    /**
     * Represents an error condition where an invalid player ID is encountered
     * in the Stats system. This error is thrown when the player ID is not
     * a positive integer.
     *
     * @constructor Creates an instance of the Invalid error with the given value.
     * @param value The invalid player ID that caused the error.
     */
    class Invalid(value: Int?) : StatsPlayerIdError("Stats player ID must be a positive integer: '$value'")
}

/**
 * Represents a StatsPlayerId, used to encapsulate and enforce the validity of a player's ID
 * in the context of stats processing.
 *
 * This is a value class, ensuring that only valid IDs (positive integers) are allowed.
 * Instances of this class can be created through the provided static methods in the `companion object`.
 *
 * @property value The integer value of the player's ID.
 */
@JvmInline
value class StatsPlayerId private constructor(val value: Int) {
    /**
     * Companion object for the `StatsPlayerId` class. Provides functions for creating and validating `StatsPlayerId` instances.
     */
    companion object {
        /**
         * Constructs a new [StatsPlayerId] from the provided integer value, ensuring it is a positive integer.
         * If the value is null or non-positive, raises a [StatsPlayerIdError.Invalid] error through the provided context.
         *
         * @param value The integer value to be wrapped in a [StatsPlayerId]. Must be a positive integer.
         * @return A [StatsPlayerId] instance wrapping the given value.
         * @throws StatsPlayerIdError.Invalid if the value is null or non-positive.
         */
        context(raise: Raise<StatsPlayerIdError.Invalid>)
        operator fun invoke(value: Int?): StatsPlayerId {
            if (value == null || value <= 0) raise.raise(StatsPlayerIdError.Invalid(value))
            return StatsPlayerId(value)
        }

        /**
         * Constructs an `Either` result based on the validity of the provided integer value.
         * If the value is valid (positive integer), it wraps it into a `StatsPlayerId`.
         * If the value is invalid (null or non-positive), it wraps a `StatsPlayerIdError.Invalid` error.
         *
         * @param value The integer value to be validated and transformed into a `StatsPlayerId`.
         * @return An `Either` containing either a `StatsPlayerId` for a valid input or a `StatsPlayerIdError.Invalid` for invalid input.
         */
        fun of(value: Int): Either<StatsPlayerIdError.Invalid, StatsPlayerId> = either { invoke(value) }

        /**
         * Converts the given integer value to a valid `StatsPlayerId` if the value is positive.
         * Throws an exception if the provided value is invalid.
         *
         * @param value The integer value to be converted to a `StatsPlayerId`. Must be a positive integer.
         * @return A `StatsPlayerId` representing the given integer value.
         * @throws IllegalArgumentException if the input value is not a positive integer.
         */
        fun from(value: Int): StatsPlayerId = requireNotNull(of(value).getOrNull()) {
            "Stats player ID must be a positive integer"
        }
    }
}

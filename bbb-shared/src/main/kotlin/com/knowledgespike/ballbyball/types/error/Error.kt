package com.knowledgespike.ballbyball.types.error

import kotlinx.serialization.Serializable

@Serializable
sealed class Error(val message: String)

class PublicMatchIdError(message: String, val publicMatchId: Long? = null) : Error(message)

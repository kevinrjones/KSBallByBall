package com.knowledgespike.cricketarchive

class InvalidStateException : Exception {

    constructor()

    constructor(message: String) : super(message)
    constructor(message: String, t: Throwable) : super(message, t)

}

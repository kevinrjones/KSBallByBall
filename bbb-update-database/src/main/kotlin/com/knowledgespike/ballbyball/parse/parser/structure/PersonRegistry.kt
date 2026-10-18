package com.knowledgespike.ballbyball.parse.parser.structure

data class PersonRegistry(val id: String, val name: String, val caId: String)
data class Person(val id: String, val name: String, val caId: Int = 0)

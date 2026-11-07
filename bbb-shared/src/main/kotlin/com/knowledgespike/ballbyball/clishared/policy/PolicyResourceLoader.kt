package com.knowledgespike.ballbyball.clishared.policy

import java.nio.charset.StandardCharsets

object PolicyResourceLoader {
    fun loadLines(
        resourcePath: String,
        classLoader: ClassLoader = PolicyResourceLoader::class.java.classLoader
            ?: ClassLoader.getSystemClassLoader()
    ): List<String> {
        val stream = classLoader.getResourceAsStream(resourcePath)
            ?: throw IllegalStateException("Required policy resource not found: $resourcePath")
        val lines = stream.bufferedReader(StandardCharsets.UTF_8).useLines { resources ->
            resources.map(String::trim).filter(String::isNotEmpty).toList()
        }
        return lines
    }
}
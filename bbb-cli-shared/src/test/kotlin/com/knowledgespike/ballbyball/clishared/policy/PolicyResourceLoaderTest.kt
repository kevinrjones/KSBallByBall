package com.knowledgespike.ballbyball.clishared.policy

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.ByteArrayInputStream

class PolicyResourceLoaderTest {
    @Test
    fun `given a missing resource when loading policy lines then fail fast`() {
        assertThrows<IllegalStateException> {
            PolicyResourceLoader.loadLines("policies/missing-policy-test.txt")
        }
    }

    @Test
    fun `given an empty resource when loading policy lines then return no exclusions`() {
        val emptyResourceClassLoader = object : ClassLoader(null) {
            override fun getResourceAsStream(name: String) = ByteArrayInputStream(ByteArray(0))
        }

        val lines = PolicyResourceLoader.loadLines("policies/empty-policy-test.txt", emptyResourceClassLoader)

        expectThat(lines).isEqualTo(emptyList())
    }
}
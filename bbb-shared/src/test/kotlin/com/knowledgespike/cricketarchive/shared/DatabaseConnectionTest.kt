package com.knowledgespike.cricketarchive.shared

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class DatabaseConnectionTest {
    @Test
    fun `given a datasource key when rendered then it does not expose the password`() {
        val password = "secret-${UUID.randomUUID()}"
        val connectionString = "jdbc:private://host/database?password=$password"
        val key = DataSourceManager.keyFor(connectionString, "user", password)

        expectThat(key.toString().contains(password)).isEqualTo(false)
        expectThat(key.toString().contains(connectionString)).isEqualTo(false)
    }

    @Test
    fun `given concurrent requests for the same key when creating a datasource then only one is created`() {
        val key = DataSourceManager.keyFor("jdbc:test:${UUID.randomUUID()}", "user", "password")
        val createCount = AtomicInteger()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        try {
            val futures = (1..16).map {
                executor.submit(Callable {
                    start.await()
                    DataSourceManager.getOrCreate(key) {
                        createCount.incrementAndGet()
                        DataSource()
                    }
                })
            }
            start.countDown()

            val dataSources = futures.map { it.get() }
            expectThat(createCount.get()).isEqualTo(1)
            expectThat(dataSources.distinct().size).isEqualTo(1)
        } finally {
            executor.shutdownNow()
            DataSourceManager.remove(key)
        }
    }

    @Test
    fun `given the same connection string and different credentials when requested then separate datasources are created`() {
        val connectionString = "jdbc:test:${UUID.randomUUID()}"
        val firstKey = DataSourceManager.keyFor(connectionString, "user", "first-password")
        val secondKey = DataSourceManager.keyFor(connectionString, "user", "second-password")
        val sameCredentialsKey = DataSourceManager.keyFor(connectionString, "user", "first-password")

        try {
            val first = DataSourceManager.getOrCreate(firstKey) { DataSource() }
            val second = DataSourceManager.getOrCreate(secondKey) { DataSource() }
            val sameCredentials = DataSourceManager.getOrCreate(sameCredentialsKey) { DataSource() }

            expectThat(first === second).isEqualTo(false)
            expectThat(first === sameCredentials).isEqualTo(true)
        } finally {
            DataSourceManager.remove(firstKey)
            DataSourceManager.remove(secondKey)
        }
    }

    @Test
    fun `given shared datasources when close all is called then every pool is closed and removed`() {
        var firstClosed = false
        var secondClosed = false
        val firstKey = DataSourceManager.keyFor("jdbc:test:first", "user", "password")
        val secondKey = DataSourceManager.keyFor("jdbc:test:second", "user", "password")
        val first = TrackingDataSource { firstClosed = true }
        val second = TrackingDataSource { secondClosed = true }

        try {
            DataSourceManager.getOrCreate(firstKey) { first }
            DataSourceManager.getOrCreate(secondKey) { second }

            DataSourceManager.closeAll()

            expectThat(firstClosed).isEqualTo(true)
            expectThat(secondClosed).isEqualTo(true)
            expectThat(DataSourceManager.getOrCreate(firstKey) { TrackingDataSource {} } === first).isEqualTo(false)
        } finally {
            DataSourceManager.closeAll()
        }
    }

    private class TrackingDataSource(private val closeAction: () -> Unit) : DataSource() {
        override fun close() = closeAction()
    }
}

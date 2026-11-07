package com.knowledgespike.cricketarchive.shared

import com.knowledgespike.cricketarchive.LoggerDelegate
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.io.Closeable
import java.sql.Connection
import java.sql.SQLException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class DatabaseConnection(
    connectionString: String,
    userName: String?,
    password: String?,
    private val readOnly: Boolean = false
) {
    private val normalizedUserName = userName.orEmpty()
    private val normalizedPassword = password.orEmpty()

    val connect: DbConnection
        get() = DbConnection(ds.connection, ds, readOnly)

    val ds: DataSource = DataSourceManager.getOrCreate(
        DataSourceManager.keyFor(connectionString, normalizedUserName, normalizedPassword)
    ) {
        DataSource().also { dataSource ->
            dataSource.configure(connectionString, normalizedUserName, normalizedPassword)
            dataSource.debugLog("init")
        }
    }

    fun debugLog(prefix: String) {
        ds.debugLog(prefix)
    }

    class DbConnection(
        val connection: Connection,
        val dataSource: DataSource,
        readOnly: Boolean
    ) : Closeable {

        init {
            connection.isReadOnly = readOnly
        }

        override fun close() {
            dataSource.debugLog("before close")
            connection.close()
            dataSource.debugLog("after close")
        }
    }
}

object DataSourceManager {
    private val log by LoggerDelegate()
    private val fingerprintSecret = ByteArray(32).also(SecureRandom()::nextBytes)
    private val datasources = ConcurrentHashMap<DataSourceKey, DataSource>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ closeAll() }, "database-datasource-shutdown"))
    }

    internal fun keyFor(connectionString: String, userName: String, password: String): DataSourceKey = DataSourceKey(
        connectionStringFingerprint = fingerprint("connection:$connectionString"),
        userName = userName,
        passwordFingerprint = fingerprint("password:$password")
    )

    @Synchronized
    internal fun getOrCreate(key: DataSourceKey, create: () -> DataSource): DataSource =
        datasources.computeIfAbsent(key) { create() }

    @Synchronized
    internal fun remove(key: DataSourceKey) {
        datasources.remove(key)?.close()
    }

    @Synchronized
    fun closeAll() {
        val sources = datasources.values.toList()
        datasources.clear()
        var failure: Exception? = null
        sources.forEach { source ->
            try {
                source.close()
            } catch (closeFailure: Exception) {
                log.error("Failed to close a shared database datasource", closeFailure)
                val previousFailure = failure
                if (previousFailure == null) failure = closeFailure else previousFailure.addSuppressed(closeFailure)
            }
        }
        failure?.let { throw it }
    }

    private fun fingerprint(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(fingerprintSecret, "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
    }
}

internal data class DataSourceKey(
    val connectionStringFingerprint: String,
    val userName: String,
    val passwordFingerprint: String
)

open class DataSource {
    private val log by LoggerDelegate()
    private val config: HikariConfig = HikariConfig()
    private var ds: HikariDataSource? = null

    fun configure(connectionString: String, userName: String, password: String) {
        config.setJdbcUrl(connectionString)
        config.setUsername(userName)
        config.setPassword(password)
        config.isAutoCommit = false
        config.maximumPoolSize = 20

        config.idleTimeout = 1000 * 60 * 10
        config.leakDetectionThreshold = 1000 * 60 * 10
        config.validationTimeout = 1000 * 60 * 10
        config.maxLifetime = 1000 * 60 * 10
        config.connectionTimeout = 1000 * 60 * 10

        config.addDataSourceProperty("cachePrepStmts", "true")
        config.addDataSourceProperty("prepStmtCacheSize", "250")
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        config.addDataSourceProperty("socketTimeout", "360000")

        // Equiv to cachePrepStmts=true and prepStmtCacheSize=250
        config.addDataSourceProperty("preparedStatementCacheQueries", "500")
        config.addDataSourceProperty("preparedStatementCacheSizeMiB", "10")
        // Optional: Switch to server-side preparation on the 1st execution
        config.addDataSourceProperty("prepareThreshold", "1")
        // Recommended: Optimize batch inserts
        config.addDataSourceProperty("reWriteBatchedInserts", "true")
        ds = HikariDataSource(config)
    }

    fun debugLog(prefix: String) {
        log.debug("$prefix - active connections: ${ds?.hikariPoolMXBean?.activeConnections}")
        log.debug("$prefix - idle connections: ${ds?.hikariPoolMXBean?.idleConnections}")
        log.debug("$prefix - total connections: ${ds?.hikariPoolMXBean?.totalConnections}")
    }

    open fun close() {
        ds?.close()
        ds = null
    }

    @get:Throws(SQLException::class)
    val connection: Connection
        get() = requireNotNull(ds) { "DataSource has not been configured" }.connection
}

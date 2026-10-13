package com.knowledgespike.ballbyball.parse.database.adapter

import com.knowledgespike.ballbyball.clishared.identity.SourceReference
import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.ProviderId
import com.knowledgespike.ballbyball.identity.Sha256Digest
import com.knowledgespike.ballbyball.identity.SourceRecordId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID

class JdbcIdentityStoreTest {
    @Test
    fun `given provenance query connection failure when checking references then SQLException propagates`() {
        val expected = SQLException("connection unavailable", "08006")
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, _, _ -> throw expected } as Connection

        val failure = assertThrows<SQLException> {
            JdbcIdentityStore(connection).sourceReferencesChanged(42L, emptyList())
        }

        expectThat(failure.sqlState).isEqualTo("08006")
    }

    @Test
    fun `given readable provenance table when checking references then empty and changed states are distinguished`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE match_source_reference " +
                        "(match_id INTEGER, provider TEXT, provider_record_key TEXT, " +
                        "source_record_id TEXT, raw_content_digest TEXT)"
                )
            }
            val identityStore = JdbcIdentityStore(connection)

            expectThat(identityStore.sourceReferencesChanged(17L, emptyList())).isEqualTo(true)
            expectThat(identityStore.sourceReferencesChanged(18L, listOf(sourceReference()))).isEqualTo(true)

            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO match_source_reference VALUES " +
                        "(42, 'cricsheet', 'record', 'source-id', 'digest')"
                )
            }

            expectThat(identityStore.sourceReferencesChanged(42L, emptyList())).isEqualTo(true)
        }
    }

    @Test
    fun `given missing or empty in-memory provenance when checking references then change is reported`() {
        val identityCore = IdentityCore()
        val incoming = listOf(sourceReference())

        expectThat(identityCore.sourceReferencesChanged(17L, incoming)).isEqualTo(true)

        identityCore.rememberSourceReferences(18L, emptyList())

        expectThat(identityCore.sourceReferencesChanged(18L, incoming)).isEqualTo(true)
    }

    @Test
    fun `given database and pending source ca owners when looked up then both ownership sources are composed`() {
        val databaseOwner = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
        val pendingOwner = CanonicalMatchId.from(UUID.fromString("74d4cbe4-9dd7-5e19-8f95-b7c05bd07e58"))
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE matches " +
                        "(id INTEGER PRIMARY KEY, source_ca_id TEXT, canonical_match_id TEXT)"
                )
                statement.executeUpdate(
                    "INSERT INTO matches VALUES " +
                        "(10, 'ca-12345', '${databaseOwner.value}'), (11, 'ca-12345', NULL)"
                )
            }
            val identityCore = IdentityCore().apply {
                sourceCaIdAllocator().apply(SourceCaIdAssignment.PendingUpdate(pendingOwner, "ca-12345"))
            }

            val ownership = JdbcIdentityStore(connection, identityCore).sourceCaOwnership("ca-12345")

            expectThat(ownership.canonicalMatchOwners.toSet()).isEqualTo(setOf(databaseOwner, pendingOwner))
            expectThat(ownership.uncanonicalMatchKey).isEqualTo(11L)
        }
    }

    private fun sourceReference() = SourceReference(
        provider = ProviderId.from("cricsheet"),
        providerRecordKey = "record",
        sourceRecordId = SourceRecordId.from(UUID.fromString("c61f62bd-7b02-5c3f-ae44-572d533b5877")),
        rawContentDigest = Sha256Digest.from("0".repeat(64))
    )
}
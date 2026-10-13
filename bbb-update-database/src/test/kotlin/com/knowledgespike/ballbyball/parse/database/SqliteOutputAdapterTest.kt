package com.knowledgespike.ballbyball.parse.database

import com.knowledgespike.ballbyball.identity.CanonicalMatchId
import com.knowledgespike.ballbyball.identity.SourceCaId

import com.knowledgespike.ballbyball.parse.database.adapter.sqlite.SqlOutputAdapter
import com.knowledgespike.ballbyball.parse.database.adapter.MatchRecord
import com.knowledgespike.cricketarchive.InvalidStateException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import java.util.UUID

class SqliteOutputAdapterTest {
    @Test
    fun `given an active match transaction when closed then it rolls back without committing`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { it.execute("create table close_test (value int)") }
            val commitCount = AtomicInteger()
            val adapter = SqlOutputAdapter(countingConnection(connection, commitCount))

            adapter.beginMatch()
            connection.createStatement().use { it.executeUpdate("insert into close_test values (1)") }
            adapter.close()

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from close_test").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(0)
                }
            }
            expectThat(connection.autoCommit).isEqualTo(true)
            expectThat(commitCount.get()).isEqualTo(0)
        }
    }

    @Test
    fun `given a successful match transaction when committed then the connection commits once`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { it.execute("create table commit_test (value int)") }
            val commitCount = AtomicInteger()
            val adapter = SqlOutputAdapter(countingConnection(connection, commitCount))

            adapter.beginMatch()
            connection.createStatement().use { it.executeUpdate("insert into commit_test values (1)") }
            adapter.commit()
            adapter.close()

            expectThat(commitCount.get()).isEqualTo(1)
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from commit_test").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
        }
    }

    private fun countingConnection(connection: Connection, commitCount: AtomicInteger): Connection =
        Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java)
        ) { _, method, arguments ->
            if (method.name == "commit") commitCount.incrementAndGet()
            try {
                method.invoke(connection, *(arguments ?: emptyArray()))
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            }
        } as Connection

    @Test
    fun `given a source ca id conflict when inserting directly then conflict is rejected`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createInsertableMatchesTable(connection)

            SqlOutputAdapter(connection).use { adapter ->
                adapter.insertMatch(matchRecord("1890a7a8-f76d-5f36-89f7-39b0319044b0", "ca-exist"))

                assertThrows<InvalidStateException> {
                    adapter.insertMatch(matchRecord("1890a7a8-f76d-5f36-89f7-39b0319044b1", "ca-exist"))
                }
            }
        }
    }
    @Test
    fun `given an existing match without source ca id when reconciled then source ca id is backfilled`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createMatchesTable(connection)
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id, source_ca_id) values " +
                        "(7, '1890a7a8-f76d-5f36-89f7-39b0319044b0', null)"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                adapter.reconcileSourceCaId(
                    canonicalId("1890a7a8-f76d-5f36-89f7-39b0319044b0"),
                    SourceCaId.from("ca-12345")
                )
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select source_ca_id from matches where id = 7").use { result ->
                    result.next()
                    expectThat(result.getString(1)).isEqualTo("ca-12345")
                }
            }
        }
    }

    @Test
    fun `given a different existing source ca id when reconciled then conflict is rejected`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createMatchesTable(connection)
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id, source_ca_id) values " +
                        "(7, '1890a7a8-f76d-5f36-89f7-39b0319044b0', 'ca-exist')"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                assertThrows<InvalidStateException> {
                    adapter.reconcileSourceCaId(
                        canonicalId("1890a7a8-f76d-5f36-89f7-39b0319044b0"),
                        SourceCaId.from("ca-new")
                    )
                }
            }
        }
    }

    @Test
    fun `given a source ca id owned by another match when reconciled then ownership conflict is rejected`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createMatchesTable(connection)
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id, source_ca_id) values " +
                        "(7, '1890a7a8-f76d-5f36-89f7-39b0319044b0', 'ca-exist')"
                )
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id, source_ca_id) values " +
                        "(8, '1890a7a8-f76d-5f36-89f7-39b0319044b1', 'ca-exist')"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                assertThrows<InvalidStateException> {
                    adapter.reconcileSourceCaId(
                        canonicalId("1890a7a8-f76d-5f36-89f7-39b0319044b2"),
                        SourceCaId.from("ca-exist")
                    )
                }
            }
        }
    }

    @Test
    fun `given a source ca id owned by a match without canonical id when reconciled then invalid state is reported`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createMatchesTableAllowingNullCanonicalId(connection)
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id, source_ca_id) values " +
                        "(7, null, 'ca-orphan')"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                assertThrows<InvalidStateException> {
                    adapter.reconcileSourceCaId(
                        canonicalId("1890a7a8-f76d-5f36-89f7-39b0319044b2"),
                        SourceCaId.from("ca-orphan")
                    )
                }
            }
        }
    }

    @Test
    fun `given a source ca id backfill in a match transaction when rolled back then no id remains`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createMatchesTable(connection)
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id, source_ca_id) values " +
                        "(7, '1890a7a8-f76d-5f36-89f7-39b0319044b0', null)"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                adapter.beginMatch()
                adapter.reconcileSourceCaId(
                    canonicalId("1890a7a8-f76d-5f36-89f7-39b0319044b0"),
                    SourceCaId.from("ca-12345")
                )
                adapter.rollback()
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select source_ca_id from matches where id = 7").use { result ->
                    result.next()
                    expectThat(result.getString(1)).isEqualTo(null)
                }
            }
        }
    }
    @Test
    fun `given an existing canonical match when found then its surrogate key is returned`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table matches (
                        id integer primary key,
                        canonical_match_id varchar(36) not null unique
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    "insert into matches (id, canonical_match_id) values (7, '1890a7a8-f76d-5f36-89f7-39b0319044b0')"
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                val id = CanonicalMatchId.from(UUID.fromString("1890a7a8-f76d-5f36-89f7-39b0319044b0"))
                expectThat(adapter.findMatchKey(id)).isEqualTo(7L)
            }
        }
    }

    @Test
    fun `given sqlite connection when adapter opens then foreign keys are enabled`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            SqlOutputAdapter(connection).use { }

            connection.createStatement().use { statement ->
                statement.executeQuery("pragma foreign_keys").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
        }
    }

    @Test
    fun `given a failed match write when rolled back then no rows remain`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table delivery_fielders (
                        delivery_id integer not null,
                        wicket_id integer not null,
                        person_id integer not null,
                        primary key (delivery_id, wicket_id, person_id)
                    )
                    """.trimIndent()
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                adapter.beginMatch()
                adapter.insertDeliveryFielder(1, 2, 3)
                adapter.rollback()
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from delivery_fielders").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(0)
                }
            }
        }
    }

    @Test
    fun `given repeated delivery fielder when written to sqlite then one row is stored`() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    create table delivery_fielders (
                        delivery_id integer not null,
                        wicket_id integer not null,
                        person_id integer not null,
                        primary key (delivery_id, wicket_id, person_id)
                    )
                    """.trimIndent()
                )
            }

            SqlOutputAdapter(connection).use { adapter ->
                adapter.insertDeliveryFielder(1, 2, 3)
                adapter.insertDeliveryFielder(1, 2, 3)
            }

            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from delivery_fielders").use { result ->
                    result.next()
                    expectThat(result.getInt(1)).isEqualTo(1)
                }
            }
        }
    }

    private fun createMatchesTable(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table matches (
                    id integer primary key,
                    canonical_match_id varchar(36) not null,
                    source_ca_id varchar(20)
                )
                """.trimIndent()
            )
        }
    }

    private fun createMatchesTableAllowingNullCanonicalId(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table matches (
                    id integer primary key,
                    canonical_match_id varchar(36),
                    source_ca_id varchar(20)
                )
                """.trimIndent()
            )
        }
    }

    private fun createInsertableMatchesTable(connection: java.sql.Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                """
                create table matches (
                    id integer primary key autoincrement,
                    canonical_match_id varchar(36),
                    public_match_id bigint,
                    source_ca_id varchar(20),
                    file_name varchar(120),
                    match_in_series integer,
                    match_type varchar(15),
                    event_name varchar(200),
                    match_date_text varchar(200),
                    season varchar(200),
                    match_start_year varchar(200),
                    match_start_date_id integer,
                    balls_per_over integer,
                    added_timestamp datetime,
                    team1_id integer,
                    team2_id integer,
                    ground_id integer,
                    toss_team_id integer,
                    toss_decision varchar(10),
                    victory_type varchar(15),
                    winner_team_id integer,
                    loser_team_id integer,
                    duration_days integer,
                    margin integer,
                    match_count integer
                )
                """.trimIndent()
            )
        }
    }

    private fun matchRecord(canonicalId: String, sourceCaId: String) = MatchRecord(
        canonicalMatchId = canonicalId(canonicalId),
        sourceCaId = SourceCaId.from(sourceCaId),
        fileName = "match.json",
        matchInSeries = 1,
        matchType = "tt",
        eventName = "Series",
        matchDateText = "2024-01-02",
        season = "2024",
        matchStartYear = "2024",
        matchStartDateKey = null,
        ballsPerOver = 6,
        team1Key = 1,
        team2Key = 2,
        groundKey = 1,
        tossTeamKey = 1,
        tossDecision = "bat",
        victoryType = "runs",
        winnerTeamKey = 1,
        loserTeamKey = 2
    )

    private fun canonicalId(value: String) = CanonicalMatchId.from(UUID.fromString(value))
}
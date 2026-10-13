package com.knowledgespike.ballbyball.parse.database.adapter

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class TransactionalCollectionsTest {
    @Test
    fun `given mutations through map views when rolled back then every mutation is restored`() {
        val values = TransactionMap<String, Int>().apply {
            put("one", 1)
            put("two", 2)
        }
        values.beginTransaction()

        values.entries.first { it.key == "one" }.setValue(10)
        values.keys.remove("two")
        values.values.clear()
        values.rollback()

        expectThat(values.toMap()).isEqualTo(mapOf("one" to 1, "two" to 2))
    }

    @Test
    fun `given removal through set iterator when rolled back then the value is restored`() {
        val values = TransactionSet<String>().apply {
            add("one")
            add("two")
        }
        values.beginTransaction()

        values.iterator().apply {
            next()
            remove()
        }
        values.rollback()

        expectThat(values.toSet()).isEqualTo(setOf("one", "two"))
    }

    @Test
    fun `given nested map transaction when inner is committed and outer is rolled back then all mutations are restored`() {
        val values = TransactionMap<String, Int>().apply {
            put("one", 1)
        }
        values.beginTransaction()
        values["one"] = 10
        values["two"] = 20

        values.beginTransaction()
        expectThat(values.transactionDepth).isEqualTo(2)
        values["one"] = 100
        values["three"] = 30
        values.commit()

        expectThat(values.transactionDepth).isEqualTo(1)
        expectThat(values.toMap()).isEqualTo(mapOf("one" to 100, "two" to 20, "three" to 30))

        values.rollback()
        expectThat(values.isInTransaction).isFalse()
        expectThat(values.toMap()).isEqualTo(mapOf("one" to 1))
    }

    @Test
    fun `given nested map transaction when inner is rolled back then only inner mutations are undone`() {
        val values = TransactionMap<String, Int>().apply {
            put("one", 1)
        }
        values.beginTransaction()
        values["two"] = 2

        values.beginTransaction()
        values["one"] = 10
        values["three"] = 3
        values.rollback()

        expectThat(values.toMap()).isEqualTo(mapOf("one" to 1, "two" to 2))

        values.commit()
        expectThat(values.toMap()).isEqualTo(mapOf("one" to 1, "two" to 2))
    }

    @Test
    fun `given nested set transaction when inner is committed and outer is rolled back then all mutations are restored`() {
        val values = TransactionSet<String>().apply {
            add("one")
        }
        values.beginTransaction()
        values.add("two")

        values.beginTransaction()
        values.add("three")
        values.remove("one")
        values.commit()

        expectThat(values.toSet()).isEqualTo(setOf("two", "three"))

        values.rollback()
        expectThat(values.toSet()).isEqualTo(setOf("one"))
    }

    @Test
    fun `given nested set transaction when inner is rolled back then only inner mutations are undone`() {
        val values = TransactionSet<String>().apply {
            add("one")
        }
        values.beginTransaction()
        values.add("two")

        values.beginTransaction()
        values.add("three")
        values.remove("one")
        values.rollback()

        expectThat(values.toSet()).isEqualTo(setOf("one", "two"))

        values.commit()
        expectThat(values.toSet()).isEqualTo(setOf("one", "two"))
    }

    @Test
    fun `given map inTransaction when block succeeds then changes are committed and result returned`() {
        val values = TransactionMap<String, Int>()
        val result = values.inTransaction { map ->
            map["alpha"] = 1
            map["beta"] = 2
            "success"
        }

        expectThat(result).isEqualTo("success")
        expectThat(values.isInTransaction).isFalse()
        expectThat(values.toMap()).isEqualTo(mapOf("alpha" to 1, "beta" to 2))
    }

    @Test
    fun `given map transaction block when exception thrown then changes are rolled back and exception rethrown`() {
        val values = TransactionMap<String, Int>().apply {
            put("initial", 100)
        }

        assertThrows<IllegalStateException> {
            values.transaction { map ->
                map["initial"] = 200
                map["temporary"] = 300
                error("simulated failure")
            }
        }

        expectThat(values.isInTransaction).isFalse()
        expectThat(values.toMap()).isEqualTo(mapOf("initial" to 100))
    }

    @Test
    fun `given set inTransaction when block succeeds then changes are committed and result returned`() {
        val values = TransactionSet<String>()
        val result = values.inTransaction { set ->
            set.add("item1")
            set.add("item2")
            42
        }

        expectThat(result).isEqualTo(42)
        expectThat(values.isInTransaction).isFalse()
        expectThat(values.toSet()).isEqualTo(setOf("item1", "item2"))
    }

    @Test
    fun `given set transaction block when exception thrown then changes are rolled back and exception rethrown`() {
        val values = TransactionSet<String>().apply {
            add("initial")
        }

        assertThrows<IllegalStateException> {
            values.transaction { set ->
                set.add("temporary")
                set.remove("initial")
                error("simulated failure")
            }
        }

        expectThat(values.isInTransaction).isFalse()
        expectThat(values.toSet()).isEqualTo(setOf("initial"))
    }

    @Test
    fun `given nested transaction blocks when inner fails and is handled then outer can still commit`() {
        val values = TransactionMap<String, Int>().apply {
            put("root", 0)
        }

        values.transaction { outer ->
            outer["outerKey"] = 1
            try {
                outer.transaction { inner ->
                    inner["innerKey"] = 2
                    error("inner failed")
                }
            } catch (_: IllegalStateException) {
                // Handled inner error
            }
            outer["afterInner"] = 3
        }

        expectThat(values.toMap()).isEqualTo(mapOf("root" to 0, "outerKey" to 1, "afterInner" to 3))
    }

    @Test
    fun `given no active transaction when map commit or rollback is called then IllegalStateException is thrown`() {
        val values = TransactionMap<String, Int>()
        assertThrows<IllegalStateException> { values.commit() }
        assertThrows<IllegalStateException> { values.rollback() }
    }

    @Test
    fun `given no active transaction when set commit or rollback is called then IllegalStateException is thrown`() {
        val values = TransactionSet<String>()
        assertThrows<IllegalStateException> { values.commit() }
        assertThrows<IllegalStateException> { values.rollback() }
    }

    @Test
    fun `given committed collection changes when a later transaction rolls back then committed values remain`() {
        val values = TransactionSet<String>()
        values.beginTransaction()
        values.add("committed")
        values.commit()

        values.beginTransaction()
        values.add("temporary")
        values.rollback()

        expectThat(values.toSet()).isEqualTo(setOf("committed"))
    }

    @Test
    fun `given clear called during transaction when rolled back then all entries are restored`() {
        val map = TransactionMap<String, Int>().apply {
            put("k1", 1)
            put("k2", 2)
        }
        runCatching {
            map.transaction {
                it.clear()
                expectThat(it.isEmpty()).isTrue()
                throw RuntimeException("abort")
            }
        }

        expectThat(map.toMap()).isEqualTo(mapOf("k1" to 1, "k2" to 2))

        val set = TransactionSet<String>().apply {
            add("e1")
            add("e2")
        }
        runCatching {
            set.transaction {
                it.clear()
                expectThat(it.isEmpty()).isTrue()
                throw RuntimeException("abort")
            }
        }

        expectThat(set.toSet()).isEqualTo(setOf("e1", "e2"))
    }

    @Test
    fun `given an identity core snapshot when rolled back then maps and counters are restored`() {
        val identityCore = IdentityCore().apply {
            rememberPerson("existing", 1)
            allocateKey(IdentitySequence.PERSON)
        }
        val snapshot = identityCore.snapshot()

        identityCore.rememberPerson("temporary", 2)
        identityCore.allocateKey(IdentitySequence.PERSON)
        identityCore.restore(snapshot)

        expectThat(identityCore.findPersonKey("existing")).isEqualTo(1L)
        expectThat(identityCore.findPersonKey("temporary")).isEqualTo(null)
        expectThat(identityCore.allocateKey(IdentitySequence.PERSON).value).isEqualTo(2L)
    }
}
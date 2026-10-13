package com.knowledgespike.ballbyball.parse.database.adapter

/**
 * Represents a container for holding the previous value of a property along with its existence status.
 *
 * @param V The type of the contained value.
 * @property existed Indicates whether the value previously existed or not.
 * @property value The previous value, or `null` if it did not exist.
 */
internal data class PreviousValue<out V : Any>(
    val existed: Boolean,
    val value: V?
)

/**
 * A custom mutable map implementation that supports transactional changes, including nested transactions.
 * This map allows for beginning a transaction, rolling back changes, or committing changes.
 *
 * @param K the type of keys maintained by this map.
 * @param V the type of values mapped by this map, which must be non-null.
 */
internal class TransactionMap<K, V : Any> : AbstractMutableMap<K, V>() {
    /**
     * A mutable map that tracks key-value entries within the context of transactions.
     * This map is used to manage and store key-value associations locally, enabling
     * transactional operations like commit, rollback, and modification tracking.
     */
    private val entriesByKey = mutableMapOf<K, V>()

    /**
     * Stack of changes made during active transactions within the `TransactionMap`.
     *
     * Each frame associates keys of type `K` with their corresponding previous values encapsulated
     * in a `PreviousValue<V>` instance prior to modification in that transaction level.
     *
     * - An empty stack indicates no ongoing transaction.
     * - Each `beginTransaction()` pushes a new frame to support nested transactions / savepoints.
     */
    private val transactionStack = ArrayDeque<MutableMap<K, PreviousValue<V>>>()

    /**
     * Indicates whether at least one transaction is currently active.
     */
    val isInTransaction: Boolean
        get() = transactionStack.isNotEmpty()

    /**
     * Returns the current nesting depth of active transactions.
     */
    val transactionDepth: Int
        get() = transactionStack.size

    /**
     * A mutable set of entries representing the key-value pairs in the map.
     * Overrides the default behavior to prevent direct addition of entries.
     */
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = object : AbstractMutableSet<MutableMap.MutableEntry<K, V>>() {
        /**
         * Represents the count of key-value pairs currently stored in the map.
         *
         * The `size` property reflects the total number of entries present in the `TransactionMap`.
         * This value is dynamically retrieved based on the underlying `entriesByKey` collection.
         */
        override val size: Int
            get() = entriesByKey.size

        /**
         * Attempts to add the specified map entry to the collection. This operation is not supported
         * for this implementation and will always throw an exception.
         *
         * @param element the map entry to be added
         * @return always throws an UnsupportedOperationException
         * @throws UnsupportedOperationException if the method is called
         */
        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
            throw UnsupportedOperationException("Map entries cannot be added directly")

        /**
         * Provides an iterator over the mutable entries of the map.
         * The entries returned by the iterator are wrapped to track changes,
         * ensuring that modifications can be recorded when interacting with the map.
         *
         * @return A mutable iterator for the entries in the map.
         */
        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
            val iterator = entriesByKey.entries.iterator()
            return object : MutableIterator<MutableMap.MutableEntry<K, V>> {
                /**
                 * Holds a reference to the most recently processed map entry during iteration.
                 * This variable is used to track the current entry for operations such as removal.
                 * It is initially `null` and is updated as the iterator progresses.
                 */
                private var current: MutableMap.MutableEntry<K, V>? = null

                /**
                 * Checks if there are more elements to iterate over in the underlying iterator.
                 *
                 * @return true if there are more elements, false otherwise
                 */
                override fun hasNext(): Boolean = iterator.hasNext()

                /**
                 * Retrieves the next mutable map entry in the iteration, tracks it, and updates the current entry.
                 *
                 * The method fetches the next entry from the backing iterator, updates the `current` field
                 * with the retrieved entry, and wraps it using the `trackedEntry` function. The wrapping ensures
                 * that changes to the entry are tracked appropriately.
                 *
                 * @return The next mutable map entry in the iteration, wrapped to track changes.
                 */
                override fun next(): MutableMap.MutableEntry<K, V> = iterator.next().also { current = it }
                    .let(::trackedEntry)

                /**
                 * Removes the current element from the underlying collection.
                 *
                 * This method requires that the `next()` method has been called prior to its invocation,
                 * ensuring that there is a current element to remove. If `next()` has not been called,
                 * an `IllegalStateException` will be thrown.
                 *
                 * The removal operation is supported by invoking the underlying iterator's `remove()` method.
                 * Additionally, the key associated with the current entry is marked for tracking
                 * using the `remember` function, which ensures changes are noted for potential
                 * rollback or commit in a transactional context.
                 *
                 * After removal, the `current` reference is set to `null`, indicating that there
                 * is no current element selected until `next()` is called again.
                 *
                 * @throws IllegalStateException if `next()` has not been called before invoking this method
                 */
                override fun remove() {
                    val entry = checkNotNull(current) { "next() must be called before remove()" }
                    remember(entry.key)
                    iterator.remove()
                    current = null
                }
            }
        }
    }

    /**
     * Checks whether the specified key is present in the map.
     *
     * @param key The key whose presence in the map is to be tested.
     * @return `true` if the map contains the specified key, `false` otherwise.
     */
    override fun containsKey(key: K): Boolean = entriesByKey.containsKey(key)

    /**
     * Checks if the specified value is present in the map.
     *
     * @param value The value to check for existence in the map.
     * @return True if the value is present, false otherwise.
     */
    override fun containsValue(value: V): Boolean = entriesByKey.containsValue(value)

    /**
     * Retrieves the value corresponding to the specified key from the collection.
     *
     * @param key The key whose associated value is to be returned.
     * @return The value associated with the specified key, or null if the key is not present.
     */
    override fun get(key: K): V? = entriesByKey[key]

    /**
     * Checks if the collection is empty.
     *
     * @return true if the collection contains no elements, false otherwise.
     */
    override fun isEmpty(): Boolean = entriesByKey.isEmpty()

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is replaced.
     *
     * @param key the key with which the specified value is to be associated.
     * @param value the value to be associated with the specified key.
     * @return the previous value associated with the key, or null if there was no mapping for the key.
     */
    override fun put(key: K, value: V): V? {
        remember(key)
        return entriesByKey.put(key, value)
    }

    /**
     * Copies all of the mappings from the specified map to this map. Existing mappings in this map
     * will be replaced if their keys are present in the specified map.
     *
     * @param from The map containing mappings to be added to this map. Keys and values must have
     *             types that align with the generic types of this map.
     */
    override fun putAll(from: Map<out K, V>) {
        from.forEach { (key, value) -> put(key, value) }
    }

    /**
     * Removes the entry associated with the specified key from the collection.
     *
     * @param key The key of the entry to be removed.
     * @return The value associated with the removed key, or null if the key was not present in the collection.
     */
    override fun remove(key: K): V? {
        remember(key)
        return entriesByKey.remove(key)
    }

    /**
     * Removes all key-value entries from the map and tracks changes for transactional operations.
     *
     * This method iterates through all keys in the map and ensures they are recorded for
     * potential rollback by invoking the `remember` function. After all entries are tracked,
     * it clears the internal storage of the map, effectively making it empty.
     *
     * It is intended to support transactional behavior by allowing the state of the map
     * to be restored in case of a rollback.
     */
    override fun clear() {
        entriesByKey.keys.forEach(::remember)
        entriesByKey.clear()
    }

    /**
     * Begins a new transaction for managing changes to the collection.
     * Supports nested transactions by pushing a new transaction frame onto the stack.
     */
    fun beginTransaction() {
        transactionStack.addLast(linkedMapOf())
    }

    /**
     * Reverts all changes made during the innermost transaction, restoring the state of the collection
     * to what it was before that transaction level began. If no transaction is active, an exception is thrown.
     *
     * The rollback operation iterates through all tracked changes in the current frame, either reverting
     * a modified entry to its previous state or removing an entry if it did not exist prior to this transaction level.
     *
     * @throws IllegalStateException if no transaction is currently active.
     */
    fun rollback() {
        val activeChanges = if (transactionStack.isNotEmpty()) {
            transactionStack.removeLast()
        } else {
            throw IllegalStateException("No collection transaction is active")
        }
        activeChanges.forEach { (key, previous) ->
            if (previous.existed) entriesByKey[key] = requireNotNull(previous.value)
            else entriesByKey.remove(key)
        }
    }

    /**
     * Finalizes the innermost transaction by committing tracked modifications.
     * If this is a nested transaction, changes are merged into the parent transaction frame so that
     * an outer rollback will correctly revert to the pre-outer-transaction state.
     *
     * @throws IllegalStateException if no transaction is currently active.
     */
    fun commit() {
        val committedChanges = if (transactionStack.isNotEmpty()) {
            transactionStack.removeLast()
        } else {
            throw IllegalStateException("No collection transaction is active")
        }
        val parentChanges = transactionStack.lastOrNull()
        if (parentChanges != null) {
            committedChanges.forEach { (key, previous) ->
                if (key !in parentChanges) {
                    parentChanges[key] = previous
                }
            }
        }
    }

    /**
     * Executes the given [block] within a transaction scope on this [TransactionMap].
     *
     * Automatically commits the transaction upon successful execution or rolls back if an exception occurs.
     *
     * @param block The block of code to execute within the transaction.
     * @return The result of [block].
     */
    inline fun <R> inTransaction(block: (TransactionMap<K, V>) -> R): R {
        beginTransaction()
        return try {
            val result = block(this)
            commit()
            result
        } catch (e: Throwable) {
            rollback()
            throw e
        }
    }

    /**
     * Executes the given [block] within a transaction scope on this [TransactionMap].
     * Alias for [inTransaction].
     *
     * @param block The block of code to execute within the transaction.
     * @return The result of [block].
     */
    inline fun <R> transaction(block: (TransactionMap<K, V>) -> R): R = inTransaction(block)

    /**
     * Tracks the state of a given key during the current transaction, recording whether
     * the key previously existed and its associated value if applicable.
     *
     * @param key The key to track within the active transaction. It will record its prior existence
     * and value if it was already present in the map.
     */
    private fun remember(key: K) {
        val activeChanges = transactionStack.lastOrNull() ?: return
        if (key !in activeChanges) {
            activeChanges[key] = PreviousValue(entriesByKey.containsKey(key), entriesByKey[key])
        }
    }

    /**
     * Wraps a given mutable map entry to allow tracking changes made to its value.
     *
     * This method provides a custom implementation of `MutableMap.MutableEntry` that tracks modifications
     * to the entry's value by calling the `remember` function when the value is set.
     *
     * @param entry the original `MutableMap.MutableEntry` to be wrapped and tracked.
     * @return a new `MutableMap.MutableEntry` instance that tracks changes to the value.
     */
    private fun trackedEntry(entry: MutableMap.MutableEntry<K, V>): MutableMap.MutableEntry<K, V> =
        object : MutableMap.MutableEntry<K, V> {
            /**
             * Retrieves the key of the entry associated with this instance. This key represents
             * the identifier of the corresponding map entry being tracked or accessed.
             */
            override val key: K
                get() = entry.key

            /**
             * Retrieves the value associated with this entry in the map.
             * This value is mapped from the provided entry within the transaction context,
             * ensuring that changes to the value are tracked and managed appropriately.
             */
            override val value: V
                get() = entry.value

            /**
             * Replaces the current value associated with the entry's key with the specified new value.
             *
             * This method triggers the `remember` function to track changes to the entry's key
             * before setting the value to the provided new value.
             *
             * @param newValue the new value to associate with the entry's key
             * @return the previous value associated with the entry's key
             */
            override fun setValue(newValue: V): V {
                remember(entry.key)
                return entry.setValue(newValue)
            }

            /**
             * Compares this entry with another object for equality.
             *
             * @param other The object to be compared with this entry.
             * @return `true` if the specified object is equal to this entry, `false` otherwise.
             */
            override fun equals(other: Any?): Boolean = entry == other

            /**
             * Returns the hash code of the current entry.
             *
             * @return an integer representing the hash code of the entry
             */
            override fun hashCode(): Int = entry.hashCode()
        }
}

/**
 * A mutable set implementation with transaction capabilities, including nested transactions.
 * This class extends [AbstractMutableSet], providing a mechanism to perform operations that
 * can be rolled back or committed using transactions.
 *
 * @param T The type of elements contained in the set, which must be non-null.
 */
internal class TransactionSet<T : Any> : AbstractMutableSet<T>() {
    /**
     * A mutable set that holds elements of type `T`.
     *
     * This field serves as the primary storage of elements within the `TransactionSet` class.
     * Its contents can be modified through methods provided by the `TransactionSet` class,
     * such as adding, removing, or clearing elements. It supports transactional operations
     * such as beginning, committing, and rolling back transactions.
     */
    private val values = mutableSetOf<T>()

    /**
     * Stack of changes made during active transactions within the `TransactionSet`.
     *
     * Each frame associates elements of type `T` with a boolean indicating whether the element
     * existed prior to modification in that transaction level (`true`) or not (`false`).
     *
     * - An empty stack indicates no ongoing transaction.
     * - Each `beginTransaction()` pushes a new frame to support nested transactions / savepoints.
     */
    private val transactionStack = ArrayDeque<MutableMap<T, Boolean>>()

    /**
     * Indicates whether at least one transaction is currently active.
     */
    val isInTransaction: Boolean
        get() = transactionStack.isNotEmpty()

    /**
     * Returns the current nesting depth of active transactions.
     */
    val transactionDepth: Int
        get() = transactionStack.size

    /**
     * Represents the size of the collection or data structure.
     * This property provides the count of elements currently held
     * within the underlying `values` data set.
     */
    override val size: Int
        get() = values.size

    /**
     * Adds the specified element to the collection if it is not already present.
     *
     * @param element The element to be added to the collection.
     * @return true if the element was added successfully, false if the element was already present.
     */
    override fun add(element: T): Boolean {
        if (element in values) return false
        remember(element)
        return values.add(element)
    }

    /**
     * Adds all elements from the given collection to this set.
     * Each element is added only if it is not already present in the set.
     *
     * @param elements the collection of elements to be added
     * @return true if the set was modified as a result of this operation, false otherwise
     */
    override fun addAll(elements: Collection<T>): Boolean = elements.fold(false) { changed, element ->
        add(element) || changed
    }

    /**
     * Removes all elements from the set while recording their existence state for transaction handling.
     *
     * This method iterates over all elements in the set and invokes the `remember` function
     * on each element to track its current state for potential rollback functionality.
     * After tracking the state of all elements, it clears the set.
     */
    override fun clear() {
        values.forEach(::remember)
        values.clear()
    }

    /**
     * Checks if the specified element is present in the collection.
     *
     * @param element The element to be checked for presence in the collection.
     * @return `true` if the element is found in the collection, `false` otherwise.
     */
    override fun contains(element: T): Boolean = values.contains(element)

    /**
     * Checks whether the current collection contains all elements of the specified collection.
     *
     * @param elements the collection of elements to check for presence in the current collection.
     * @return `true` if all elements in the specified collection are present in the current collection,
     *         `false` otherwise.
     */
    override fun containsAll(elements: Collection<T>): Boolean = values.containsAll(elements)

    /**
     * Checks if the collection is empty.
     *
     * @return true if the collection contains no elements, false otherwise.
     */
    override fun isEmpty(): Boolean = values.isEmpty()

    /**
     * Returns a mutable iterator over the elements in the collection.
     *
     * The returned iterator allows traversal of the collection, along with the ability to remove elements during iteration.
     *
     * @return A mutable iterator for iterating over the collection.
     */
    override fun iterator(): MutableIterator<T> {
        val iterator = values.iterator()
        return object : MutableIterator<T> {
            /**
             * Represents the currently active element during an operation within the `TransactionSet` class.
             * This variable is used to keep track of the element that is actively being processed,
             * such as during iteration or modification operations.
             *
             * It is nullable to indicate the absence of an active element at specific points in the operation lifecycle.
             */
            private var current: T? = null

            /**
             * Indicates whether the current element in the iteration can be removed.
             * This variable is set to `true` after a successful call to `next()` and
             * allows the `remove()` operation to be invoked. It is reset to `false`
             * after a call to `remove()`, preventing repeated or invalid removal attempts.
             */
            private var canRemove = false

            /**
             * Checks if there are more elements to iterate over in the collection.
             *
             * @return `true` if the iteration has more elements, `false` otherwise.
             */
            override fun hasNext(): Boolean = iterator.hasNext()

            /**
             * Retrieves the next element in the iteration and updates the internal state
             * to allow subsequent removal of the current element.
             *
             * @return The next element in the iteration.
             * @throws NoSuchElementException if the iteration has no more elements.
             */
            override fun next(): T = iterator.next().also {
                current = it
                canRemove = true
            }

            /**
             * Removes the most recently accessed element returned by the iterator.
             *
             * This method must be called only after a call to `next()`. If `next()` has not been called,
             * an `IllegalStateException` will be thrown. The removal process involves:
             * - Verifying the removal can proceed by checking the `canRemove` flag.
             * - Storing a reference to the current element using the `remember` method.
             * - Removing the current element from the underlying collection.
             * - Resetting the `current` element reference and marking `canRemove` as false.
             *
             * Throws:
             * - `IllegalStateException` if invoked without a preceding call to `next()`.
             */
            override fun remove() {
                check(canRemove) { "next() must be called before remove()" }
                val element = checkNotNull(current) { "next() must be called before remove()" }
                remember(element)
                iterator.remove()
                current = null
                canRemove = false
            }
        }
    }

    /**
     * Removes the specified element from the collection, if it is present.
     *
     * @param element The element to be removed from the collection.
     * @return `true` if the element was successfully removed, `false` if the element was not found in the collection.
     */
    override fun remove(element: T): Boolean {
        if (element !in values) return false
        remember(element)
        return values.remove(element)
    }

    /**
     * Removes all elements in the specified collection from this collection.
     *
     * @param elements A collection of elements to be removed. Each element in the specified
     * collection will be attempted to be removed from this collection.
     * @return True if the collection was modified as a result of this operation, false otherwise.
     */
    override fun removeAll(elements: Collection<T>): Boolean = elements.fold(false) { changed, element ->
        remove(element) || changed
    }

    /**
     * Retains only the elements in this collection that are contained in the specified collection.
     * All elements not in the specified collection are removed from this collection.
     *
     * @param elements the collection of elements to retain in this collection.
     * @return `true` if the collection was modified as a result of this operation, otherwise `false`.
     */
    override fun retainAll(elements: Collection<T>): Boolean = values.toList()
        .filterNot(elements::contains)
        .fold(false) { changed, element -> remove(element) || changed }

    /**
     * Begins a new transaction within a collection.
     * Supports nested transactions by pushing a new transaction frame onto the stack.
     */
    fun beginTransaction() {
        transactionStack.addLast(linkedMapOf())
    }

    /**
     * Reverts all changes made during the innermost collection transaction.
     *
     * This method undoes any additions or removals performed on the collection
     * since the start of the innermost transaction level. If an element was added during the
     * transaction, it will be removed. If an element was removed, it will be
     * restored to the collection.
     *
     * @throws IllegalStateException if no transaction is currently active.
     */
    fun rollback() {
        val activeChanges = if (transactionStack.isNotEmpty()) {
            transactionStack.removeLast()
        } else {
            throw IllegalStateException("No collection transaction is active")
        }
        activeChanges.forEach { (element, existed) ->
            if (existed) values.add(element) else values.remove(element)
        }
    }

    /**
     * Finalizes changes made in the innermost collection transaction.
     * If this is a nested transaction, changes are merged into the parent transaction frame.
     *
     * @throws IllegalStateException if no transaction is currently active.
     */
    fun commit() {
        val committedChanges = if (transactionStack.isNotEmpty()) {
            transactionStack.removeLast()
        } else {
            throw IllegalStateException("No collection transaction is active")
        }
        val parentChanges = transactionStack.lastOrNull()
        if (parentChanges != null) {
            committedChanges.forEach { (element, existed) ->
                if (element !in parentChanges) {
                    parentChanges[element] = existed
                }
            }
        }
    }

    /**
     * Executes the given [block] within a transaction scope on this [TransactionSet].
     *
     * Automatically commits the transaction upon successful execution or rolls back if an exception occurs.
     *
     * @param block The block of code to execute within the transaction.
     * @return The result of [block].
     */
    inline fun <R> inTransaction(block: (TransactionSet<T>) -> R): R {
        beginTransaction()
        return try {
            val result = block(this)
            commit()
            result
        } catch (e: Throwable) {
            rollback()
            throw e
        }
    }

    /**
     * Executes the given [block] within a transaction scope on this [TransactionSet].
     * Alias for [inTransaction].
     *
     * @param block The block of code to execute within the transaction.
     * @return The result of [block].
     */
    inline fun <R> transaction(block: (TransactionSet<T>) -> R): R = inTransaction(block)

    /**
     * Marks the given element to be remembered in the current transaction.
     * If the element is not already tracked in the active changes, it gets added with
     * a flag indicating whether it exists in the `values` collection.
     *
     * @param element The element to be remembered in the current transaction.
     */
    private fun remember(element: T) {
        val activeChanges = transactionStack.lastOrNull() ?: return
        if (element !in activeChanges) activeChanges[element] = element in values
    }
}
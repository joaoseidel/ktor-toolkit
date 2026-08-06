package com.luizalabs.ktor.toolkit.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A bounded, optionally expiring in-memory [KeyValueCache].
 *
 * Entries are evicted least-recently-used once [maxSize] is reached, and expire [ttl] after they
 * were written. Both bounds matter: an unbounded map behind a request-keyed cache grows with the
 * number of distinct URLs a client cares to send.
 *
 * Suitable for a single node. Use a shared store — [LettuceCache], say — when more than one
 * instance serves the same traffic, otherwise each will hold, and invalidate, its own copy.
 *
 * @param maxSize The most entries to retain. Must be positive.
 * @param ttl How long an entry stays valid. [Duration.INFINITE] disables expiry.
 * @param clock The time source, injectable so tests need not sleep.
 */
class InMemoryCache(
    private val maxSize: Int = DEFAULT_MAX_SIZE,
    private val ttl: Duration = Duration.INFINITE,
    private val clock: Clock = Clock.System,
) : KeyValueCache {
    init {
        require(maxSize > 0) { "maxSize must be greater than 0, but was $maxSize" }
        require(ttl > Duration.ZERO) { "ttl must be positive, but was $ttl" }
    }

    private class Entry(
        val value: ByteArray,
        val expiresAt: Instant?,
    )

    private val mutex = Mutex()

    // accessOrder = true makes iteration order least-recently-used first, which is the eviction order.
    private val store =
        object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Entry>): Boolean = size > maxSize
        }

    override suspend fun get(key: String): ByteArray? =
        mutex.withLock {
            val entry = store[key] ?: return@withLock null

            if (entry.isExpired()) {
                store.remove(key)
                null
            } else {
                entry.value
            }
        }

    override suspend fun put(
        key: String,
        value: ByteArray,
    ) {
        val expiresAt = if (ttl.isInfinite()) null else clock.now() + ttl
        mutex.withLock { store[key] = Entry(value, expiresAt) }
    }

    override suspend fun delete(key: String) {
        mutex.withLock { store.remove(key) }
    }

    override suspend fun keys(): List<String> =
        mutex.withLock {
            store.entries.removeAll { it.value.isExpired() }
            store.keys.toList()
        }

    /** The number of live entries. Exposed for tests and diagnostics. */
    suspend fun size(): Int = keys().size

    private fun Entry.isExpired(): Boolean = expiresAt != null && clock.now() >= expiresAt

    companion object {
        /** Entry ceiling applied when the caller does not pick one. */
        const val DEFAULT_MAX_SIZE: Int = 1_000
    }
}

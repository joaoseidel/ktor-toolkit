package com.github.joaoseidel.ktor.toolkit.cache

/**
 * The minimal contract the toolkit needs from a cache.
 *
 * [InMemoryCache] is provided for single-node deployments and tests, and [LettuceCache] for Redis;
 * implement it over Memcached or anything else you already run.
 *
 * Implementations should treat failures as recoverable — callers go through `cacheCatching`, which
 * logs and falls back to the origin rather than failing the request.
 */
interface KeyValueCache {
    /** Returns the stored value, or `null` when the key is absent or expired. */
    suspend fun get(key: String): ByteArray?

    /** Stores [value] under [key], replacing any previous entry. */
    suspend fun put(
        key: String,
        value: ByteArray,
    )

    /** Removes [key], if present. */
    suspend fun delete(key: String)

    /** Every live key. Used by the namespace and content invalidation helpers. */
    suspend fun keys(): List<String>
}

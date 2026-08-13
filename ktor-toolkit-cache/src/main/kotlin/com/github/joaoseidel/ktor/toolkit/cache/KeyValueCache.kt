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

    /**
     * Every live key starting with [prefix].
     *
     * This is what namespace invalidation runs on, so a store that can match keys itself should
     * override it and let the store do the narrowing — the default fetches every key and filters
     * them here, which costs the same as [keys] however small the namespace is.
     */
    suspend fun keys(prefix: String): List<String> = keys().filter { it.startsWith(prefix) }
}

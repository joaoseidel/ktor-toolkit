package com.github.joaoseidel.ktor.toolkit.cache

import io.lettuce.core.KeyScanCursor
import io.lettuce.core.ScanArgs
import io.lettuce.core.SetArgs
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.future.await
import kotlin.time.Duration

/**
 * A [KeyValueCache] backed by Redis through [Lettuce](https://lettuce.io).
 *
 * Unlike [InMemoryCache], this is shared: every instance behind the same Redis sees the same
 * entries, so an invalidation on one node is seen by all of them. That is the reason to reach for
 * it — a per-node cache invalidated on one node only serves stale data from the others.
 *
 * Lettuce is an optional dependency. Add `io.lettuce:lettuce-core` yourself to use this class; the
 * toolkit only compiles against it.
 *
 * The connection is *not* owned by the cache: open it, share it across the application — a Lettuce
 * connection is thread-safe and multiplexes — and close it on shutdown.
 *
 * ```kotlin
 * val client = RedisClient.create("redis://localhost:6379")
 * val connection = client.connect(LettuceCache.Codec)
 * val cache = LettuceCache(connection.async(), ttl = 5.minutes)
 * ```
 *
 * Failures are left to propagate: `withCache` and the invalidation helpers run every call through
 * `cacheCatching`, which logs and falls back to the origin.
 *
 * @param commands The async command set, from `connection.async()`. Typed against the interface
 *   both a standalone and a cluster connection implement, so either works.
 * @param ttl How long an entry stays valid, applied by Redis itself.
 *   [Duration.INFINITE] stores entries without an expiry.
 * @param keyPrefix Prepended to every Redis key. It keeps the cache inside its own corner of a
 *   shared instance, which also bounds what [keys] — and therefore `invalidateContaining` — has to
 *   scan. Pass `""` only when the cache owns the whole database.
 * @param scanBatchSize The `COUNT` hint given to `SCAN`. Larger means fewer round trips per [keys]
 *   call, at the cost of a longer single-threaded pause on the server.
 */
class LettuceCache(
    private val commands: RedisClusterAsyncCommands<String, ByteArray>,
    private val ttl: Duration = Duration.INFINITE,
    private val keyPrefix: String = DEFAULT_KEY_PREFIX,
    private val scanBatchSize: Int = DEFAULT_SCAN_BATCH_SIZE,
) : KeyValueCache {
    init {
        require(ttl > Duration.ZERO) { "ttl must be positive, but was $ttl" }
        require(ttl.isInfinite() || ttl.inWholeMilliseconds > 0) {
            "ttl must be at least a millisecond, the finest expiry Redis records, but was $ttl"
        }
        require(scanBatchSize > 0) { "scanBatchSize must be greater than 0, but was $scanBatchSize" }
    }

    override suspend fun get(key: String): ByteArray? = commands.get(key.prefixed()).await()

    override suspend fun put(
        key: String,
        value: ByteArray,
    ) {
        val prefixed = key.prefixed()

        if (ttl.isInfinite()) {
            commands.set(prefixed, value).await()
        } else {
            commands.set(prefixed, value, SetArgs.Builder.px(ttl.inWholeMilliseconds)).await()
        }
    }

    override suspend fun delete(key: String) {
        commands.del(key.prefixed()).await()
    }

    /**
     * Every key the cache owns, without its prefix, collected over a full `SCAN`.
     *
     * `SCAN` is used rather than `KEYS` because `KEYS` blocks the server for the whole sweep. It
     * is still proportional to the keyspace under [keyPrefix], and it may return the same key
     * twice — duplicates are removed here — so this stays a maintenance-shaped operation, not a
     * per-request one.
     */
    override suspend fun keys(): List<String> = keys(prefix = "")

    /**
     * As [keys], but the narrowing is `SCAN MATCH`, so the server skips the keys that do not match
     * rather than returning them to be filtered here. The sweep is still proportional to the whole
     * keyspace under [keyPrefix] — that is what `SCAN` costs — but only the matches cross the wire.
     */
    override suspend fun keys(prefix: String): List<String> {
        val pattern = "${keyPrefix.escapeGlob()}${prefix.escapeGlob()}*"
        val args = ScanArgs.Builder.limit(scanBatchSize.toLong()).match(pattern)
        val collected = LinkedHashSet<String>()
        var cursor: KeyScanCursor<String> = commands.scan(args).await()

        while (true) {
            cursor.keys.mapTo(collected) { it.removePrefix(keyPrefix) }
            if (cursor.isFinished) return collected.toList()
            cursor = commands.scan(cursor, args).await()
        }
    }

    private fun String.prefixed(): String = "$keyPrefix$this"

    /** Escapes the `SCAN MATCH` metacharacters, so a prefix is matched literally. */
    private fun String.escapeGlob(): String =
        buildString(length) {
            for (character in this@escapeGlob) {
                if (character in GLOB_METACHARACTERS) append('\\')
                append(character)
            }
        }

    companion object {
        /** Key prefix applied when the caller does not pick one. */
        const val DEFAULT_KEY_PREFIX: String = "ktor-toolkit:"

        /** `SCAN COUNT` used when the caller does not pick one. */
        const val DEFAULT_SCAN_BATCH_SIZE: Int = 256

        private const val GLOB_METACHARACTERS = "*?[]^\\"

        /**
         * The codec the cache expects: keys are UTF-8 text, values the raw bytes it stores.
         *
         * Pass it when opening the connection — `client.connect(LettuceCache.Codec)` — otherwise
         * the connection is typed `<String, String>` and will not fit the constructor.
         */
        val Codec: RedisCodec<String, ByteArray> =
            RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
    }
}

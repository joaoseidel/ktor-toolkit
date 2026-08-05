package com.luizalabs.ktor.toolkit.cache

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.path
import io.ktor.util.filter
import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64
import kotlin.text.Charsets.UTF_8

@PublishedApi
internal val cacheLogger = KtorSimpleLogger("com.luizalabs.ktor.toolkit.cache")

/**
 * Runs [block], swallowing cache failures but never a coroutine cancellation.
 *
 * A cache is a best-effort optimisation: if the backing store is down, the caller should still
 * serve the request. `CancellationException`, though, is control flow — swallowing it breaks
 * structured concurrency and leaks the coroutine, so it is always rethrown.
 *
 * @return the block's value, or `null` when the cache misbehaved.
 */
@PublishedApi
internal inline fun <T> cacheCatching(
    operation: String,
    block: () -> T,
): T? =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        cacheLogger.warn("Cache $operation failed; falling back to the origin", failure)
        null
    }

/**
 * Serves [produce] through [cache], keyed by the request path and its query parameters.
 *
 * A cache failure is logged and ignored — the request is served from [produce] as if the entry
 * had simply been missing.
 *
 * @param namespace Prefix for the cache key, so a whole feature can be invalidated at once.
 * @param cache The backing store.
 * @param json The format used to serialize the cached value.
 * @param excludeQueryKeys Query parameters that must not take part in the cache key.
 * @param produce Computes the value on a cache miss.
 */
suspend inline fun <reified T : Any> ApplicationRequest.withCache(
    namespace: String,
    cache: KeyValueCache,
    json: Json = Json.Default,
    excludeQueryKeys: Set<String> = emptySet(),
    produce: () -> T,
): T {
    val key = buildCacheKey(namespace, this, excludeQueryKeys)

    val cached =
        cacheCatching("read") {
            cache.get(key)?.let { bytes -> json.decodeFromString<T>(String(bytes, UTF_8)) }
        }

    if (cached != null) return cached

    val fresh = produce()
    cacheCatching("write") { cache.put(key, json.encodeToString(fresh).toByteArray(UTF_8)) }
    return fresh
}

/**
 * Deletes every entry whose serialized payload mentions [id].
 *
 * This reads every key in the store, so it is meant for modest caches where an entity can appear
 * in entries whose keys do not name it. Prefer [invalidateNamespace] when the namespace is enough.
 *
 * @return `true` if at least one entry was deleted.
 */
suspend fun KeyValueCache.invalidateContaining(id: String): Boolean =
    cacheCatching("invalidateContaining") {
        coroutineScope {
            keys()
                .map { key ->
                    async {
                        cacheCatching("invalidateContaining($key)") {
                            val bytes = get(key)
                            if (bytes != null && String(bytes, UTF_8).contains(id)) {
                                delete(key)
                                true
                            } else {
                                false
                            }
                        } ?: false
                    }
                }.awaitAll()
                .any { it }
        }
    } ?: false

/** Deletes every entry stored under [namespace]. */
suspend fun KeyValueCache.invalidateNamespace(namespace: String) {
    cacheCatching("invalidateNamespace") {
        coroutineScope {
            keys()
                .filter { it.startsWith("$namespace.") }
                .map { key -> async { cacheCatching("delete($key)") { delete(key) } } }
                .awaitAll()
        }
    }
}

/**
 * Builds a stable cache key from [namespace], the request path and its query parameters.
 *
 * Parameters are sorted by name and their values sorted within each name, so the same logical
 * request produces the same key regardless of the order the client sent them in. The result is
 * hashed rather than encoded, because a key derived from a URL would otherwise be as long as the
 * URL — and a client controls how long that is.
 *
 * The namespace stays in the clear so [invalidateNamespace] can match on it.
 */
@PublishedApi
internal fun buildCacheKey(
    namespace: String,
    request: ApplicationRequest,
    excludeQueryKeys: Set<String> = emptySet(),
): String {
    val params = request.queryParameters.filter { key, _ -> key !in excludeQueryKeys }
    val query =
        params
            .entries()
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value.sorted().joinToString(",")}" }
    val raw = if (query.isEmpty()) request.path() else "${request.path()}?$query"
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(UTF_8))
    return "$namespace.${Base64.getUrlEncoder().withoutPadding().encodeToString(digest)}"
}

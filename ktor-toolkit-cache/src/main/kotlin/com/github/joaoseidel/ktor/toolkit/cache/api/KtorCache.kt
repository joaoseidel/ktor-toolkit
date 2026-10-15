package com.github.joaoseidel.ktor.toolkit.cache.api

import com.github.joaoseidel.ktor.toolkit.cache.runtime.UnreachableBytecode
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
internal val cacheLogger = KtorSimpleLogger("com.github.joaoseidel.ktor.toolkit.cache")

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
 * Serves [produce] through [cache], keyed by the request path, its query parameters and the
 * headers named in [varyHeaders].
 *
 * A cache failure is logged and ignored — the request is served from [produce] as if the entry
 * had simply been missing.
 *
 * @param namespace Prefix for the cache key, so a whole feature can be invalidated at once.
 * @param cache The backing store.
 * @param json The format used to serialize the cached value.
 * @param excludeQueryKeys Query parameters that must not take part in the cache key.
 * @param varyHeaders Request headers the response varies by, so each distinct value gets its
 * own entry — what `Vary` declares to an HTTP cache. Names are matched case-insensitively.
 * @param produce Computes the value on a cache miss.
 */
@UnreachableBytecode
suspend inline fun <reified T : Any> ApplicationRequest.withCache(
    namespace: String,
    cache: KeyValueCache,
    json: Json = Json.Default,
    excludeQueryKeys: Set<String> = emptySet(),
    varyHeaders: Set<String> = emptySet(),
    produce: () -> T,
): T {
    val key = buildCacheKey(namespace, this, excludeQueryKeys, varyHeaders)

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

/**
 * Deletes every entry stored under [namespace].
 *
 * The namespace is asked of the store rather than filtered here, so a store that can narrow keys
 * itself only returns the ones about to be deleted.
 */
suspend fun KeyValueCache.invalidateNamespace(namespace: String) {
    cacheCatching("invalidateNamespace") {
        coroutineScope {
            keys("$namespace.")
                .map { key -> async { cacheCatching("delete($key)") { delete(key) } } }
                .awaitAll()
        }
    }
}

private const val HEADER_BLOCK_SEPARATOR = '\n'

@PublishedApi
internal fun buildCacheKey(
    namespace: String,
    request: ApplicationRequest,
    excludeQueryKeys: Set<String> = emptySet(),
    varyHeaders: Set<String> = emptySet(),
): String {
    val query = request.canonicalQuery(excludeQueryKeys)
    val headers = request.canonicalHeaders(varyHeaders)
    val raw =
        buildString {
            append(request.path())
            if (query.isNotEmpty()) append('?').append(query)
            if (headers.isNotEmpty()) append(HEADER_BLOCK_SEPARATOR).append(headers)
        }
    val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(UTF_8))
    return "$namespace.${Base64.getUrlEncoder().withoutPadding().encodeToString(digest)}"
}

private fun ApplicationRequest.canonicalQuery(excludeQueryKeys: Set<String>): String =
    queryParameters
        .filter { key, _ -> key !in excludeQueryKeys }
        .entries()
        .sortedBy { it.key }
        .joinToString("&") { "${it.key}=${it.value.sorted().joinToString(",")}" }

private fun ApplicationRequest.canonicalHeaders(varyHeaders: Set<String>): String =
    varyHeaders
        .map { it.lowercase() }
        .toSortedSet()
        .mapNotNull { name -> headers.getAll(name)?.let { values -> "$name=${values.sorted().joinToString(",")}" } }
        .joinToString(HEADER_BLOCK_SEPARATOR.toString())

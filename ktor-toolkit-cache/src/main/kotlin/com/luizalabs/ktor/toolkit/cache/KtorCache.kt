package com.luizalabs.ktor.toolkit.cache

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.path
import io.ktor.util.filter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.text.Charsets.UTF_8

suspend inline fun <reified T : Any> ApplicationRequest.withCache(
    namespace: String,
    cache: KeyValueCache,
    json: Json = Json.Default,
    excludeQueryKeys: Set<String> = emptySet(),
    produce: () -> T,
): T {
    val key = buildCacheKey(namespace, this, excludeQueryKeys)

    val cached =
        cache
            .runCatching {
                val bytes = get(key)

                return@runCatching if (bytes != null) {
                    json.decodeFromString<T>(String(bytes, UTF_8))
                } else {
                    null
                }
            }.getOrNull()

    if (cached != null) return cached

    val fresh = produce()
    cache.runCatching { put(key, json.encodeToString(fresh).toByteArray(UTF_8)) }
    return fresh
}

suspend fun KeyValueCache.invalidateContaining(id: String): Boolean =
    runCatching {
        coroutineScope {
            keys()
                .map { key ->
                    async {
                        runCatching {
                            val bytes = get(key)

                            return@runCatching if (bytes != null && String(bytes, UTF_8).contains(id)) {
                                delete(key)
                                true
                            } else {
                                false
                            }
                        }.getOrDefault(false)
                    }
                }.awaitAll()
                .any { it }
        }
    }.getOrDefault(false)

suspend fun KeyValueCache.invalidateNamespace(namespace: String) {
    runCatching {
        coroutineScope {
            keys()
                .filter { it.startsWith("$namespace.") }
                .map { key -> async { runCatching { delete(key) } } }
                .awaitAll()
        }
    }
}

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
    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
    return "$namespace.$encoded"
}

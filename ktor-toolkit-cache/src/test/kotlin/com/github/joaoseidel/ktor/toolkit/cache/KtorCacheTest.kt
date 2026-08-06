package com.github.joaoseidel.ktor.toolkit.cache

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicInteger

@Serializable
private data class Book(
    val id: String,
    val title: String,
)

/** Runs [block] against the ApplicationRequest of a single GET to [path]. */
private fun <T> withRequest(
    path: String,
    block: suspend (ApplicationRequest) -> T,
): T {
    var result: Result<T>? = null

    testApplication {
        routing {
            get("/{...}") {
                result = runCatching { block(call.request) }
                call.respondText("ok")
            }
        }
        client.get(path).bodyAsText()
    }

    return checkNotNull(result) { "the route never ran" }.getOrThrow()
}

/** A cache whose every operation fails with [failure], standing in for a store that is down. */
private fun brokenCache(failure: Throwable): KeyValueCache {
    val cache = mockk<KeyValueCache>()

    coEvery { cache.get(any()) } throws failure
    coEvery { cache.put(any(), any()) } throws failure
    coEvery { cache.delete(any()) } throws failure
    coEvery { cache.keys() } throws failure

    return cache
}

/**
 * A store that lists [awkwardKey], whose entry it cannot hand back — either because it is gone, as
 * a shared cache's can be between the listing and the read, or because that one read fails. Every
 * other operation goes to [delegate].
 */
private fun partialCache(
    delegate: KeyValueCache,
    awkwardKey: String,
    read: () -> ByteArray?,
): KeyValueCache {
    val cache = mockk<KeyValueCache>()

    coEvery { cache.keys() } coAnswers { delegate.keys() + awkwardKey }
    coEvery { cache.get(awkwardKey) } answers { read() }
    coEvery { cache.get(neq(awkwardKey)) } coAnswers { delegate.get(firstArg()) }
    coEvery { cache.put(any(), any()) } coAnswers { delegate.put(firstArg(), secondArg()) }
    coEvery { cache.delete(any()) } coAnswers { delegate.delete(firstArg()) }

    return cache
}

/**
 * A store whose reads and writes really suspend, the way a network-backed one does, before handing
 * over to [delegate].
 *
 * [InMemoryCache] answers without ever parking the caller, which leaves the suspension paths of
 * [withCache] untried — and those are the paths every remote cache, [LettuceCache] included, takes.
 */
private fun suspendingCache(delegate: KeyValueCache): KeyValueCache {
    val cache = mockk<KeyValueCache>()

    coEvery { cache.get(any()) } coAnswers {
        delay(1)
        delegate.get(firstArg())
    }
    coEvery { cache.put(any(), any()) } coAnswers {
        delay(1)
        delegate.put(firstArg(), secondArg())
    }

    return cache
}

/** Serves one book through [withCache] on its defaults, counting how often the origin was asked. */
private fun cachedBook(
    path: String,
    cache: KeyValueCache,
    calls: AtomicInteger = AtomicInteger(),
): Book =
    withRequest(path) { request ->
        request.withCache("books", cache) {
            calls.incrementAndGet()
            Book("1", "Dune")
        }
    }

/** As [cachedBook], but with the serializer and the excluded parameters named explicitly. */
private fun cachedBook(
    path: String,
    cache: KeyValueCache,
    calls: AtomicInteger,
    json: Json = Json.Default,
    excludeQueryKeys: Set<String> = emptySet(),
): Book =
    withRequest(path) { request ->
        request.withCache("books", cache, json, excludeQueryKeys) {
            calls.incrementAndGet()
            Book("1", "Dune")
        }
    }

class KtorCacheTest :
    ShouldSpec({
        context("buildCacheKey") {
            should("prefix the key with the namespace so it can be invalidated wholesale") {
                withRequest("/books") { buildCacheKey("books", it) } shouldStartWith "books."
            }

            should("produce the same key regardless of parameter order") {
                val a = withRequest("/books?b=2&a=1") { buildCacheKey("books", it) }
                val b = withRequest("/books?a=1&b=2") { buildCacheKey("books", it) }

                a shouldBe b
            }

            should("produce the same key regardless of repeated-value order") {
                val a = withRequest("/books?tag=x&tag=y") { buildCacheKey("books", it) }
                val b = withRequest("/books?tag=y&tag=x") { buildCacheKey("books", it) }

                a shouldBe b
            }

            should("distinguish different paths") {
                val a = withRequest("/books") { buildCacheKey("books", it) }
                val b = withRequest("/authors") { buildCacheKey("books", it) }

                a shouldNotBe b
            }

            should("distinguish different parameter values") {
                val a = withRequest("/books?page=1") { buildCacheKey("books", it) }
                val b = withRequest("/books?page=2") { buildCacheKey("books", it) }

                a shouldNotBe b
            }

            should("ignore excluded parameters") {
                val a = withRequest("/books?page=1&traceId=abc") { buildCacheKey("books", it, setOf("traceId")) }
                val b = withRequest("/books?page=1&traceId=xyz") { buildCacheKey("books", it, setOf("traceId")) }

                a shouldBe b
            }

            should("stay short no matter how long the URL is") {
                val long = "/books?q=" + "x".repeat(5_000)

                withRequest(long) { buildCacheKey("books", it) }.length shouldBe "books.".length + 43
            }
        }

        context("withCache") {
            should("call the producer on a miss and serve the cache on a hit") {
                val cache = InMemoryCache()
                val calls = AtomicInteger()

                val first = cachedBook("/books/1", cache, calls)
                val second = cachedBook("/books/1", cache, calls)

                first shouldBe Book("1", "Dune")
                second shouldBe Book("1", "Dune")
                calls.get() shouldBe 1
            }

            should("treat a different query as a different entry") {
                val cache = InMemoryCache()
                val calls = AtomicInteger()

                cachedBook("/books?page=1", cache, calls)
                cachedBook("/books?page=2", cache, calls)

                calls.get() shouldBe 2
            }

            should("call the producer on a miss and serve the cache on a hit, through a store that suspends") {
                val cache = suspendingCache(InMemoryCache())
                val calls = AtomicInteger()

                cachedBook("/books/1", cache, calls) shouldBe Book("1", "Dune")
                cachedBook("/books/1", cache, calls) shouldBe Book("1", "Dune")

                calls.get() shouldBe 1
            }

            should("serve from the origin when the cache is down") {
                val cache = brokenCache(IllegalStateException("redis is unreachable"))

                cachedBook("/books/1", cache) shouldBe Book("1", "Dune")
            }

            should("propagate cancellation instead of swallowing it") {
                // runCatching would have caught this and quietly broken structured concurrency.
                val cache = brokenCache(CancellationException("scope cancelled"))

                shouldThrow<CancellationException> { cachedBook("/books/1", cache) }
            }

            should("store the entry with the serializer it was given") {
                val cache = InMemoryCache()
                val calls = AtomicInteger()
                val fetch = { path: String -> cachedBook(path, cache, calls, Json { encodeDefaults = true }) }

                fetch("/books/1") shouldBe Book("1", "Dune")
                fetch("/books/1") shouldBe Book("1", "Dune")

                calls.get() shouldBe 1
                String(cache.get(cache.keys().single())!!) shouldBe """{"id":"1","title":"Dune"}"""
            }

            should("serve one entry for two requests that differ only in an excluded parameter") {
                val cache = InMemoryCache()
                val calls = AtomicInteger()
                val fetch = { path: String -> cachedBook(path, cache, calls, excludeQueryKeys = setOf("trace")) }

                fetch("/books?page=1&trace=a")
                fetch("/books?page=1&trace=b")

                calls.get() shouldBe 1
            }
        }

        context("invalidateNamespace") {
            should("delete only the keys under the namespace") {
                val cache = InMemoryCache()
                cache.put("books.one", ByteArray(0))
                cache.put("books.two", ByteArray(0))
                cache.put("authors.one", ByteArray(0))

                cache.invalidateNamespace("books")

                cache.keys() shouldContainExactlyInAnyOrder listOf("authors.one")
            }

            should("propagate cancellation instead of swallowing it") {
                val cache = brokenCache(CancellationException("scope cancelled"))

                shouldThrow<CancellationException> { cache.invalidateNamespace("books") }
            }
        }

        context("invalidateContaining") {
            should("delete entries whose payload mentions the id") {
                val cache = InMemoryCache()
                cache.put("books.one", """{"id":"42","title":"Dune"}""".toByteArray())
                cache.put("books.two", """{"id":"7","title":"Emma"}""".toByteArray())

                cache.invalidateContaining("42") shouldBe true

                cache.keys() shouldContainExactlyInAnyOrder listOf("books.two")
            }

            should("report that nothing matched") {
                val cache = InMemoryCache()
                cache.put("books.one", """{"id":"42"}""".toByteArray())

                cache.invalidateContaining("999") shouldBe false

                cache.keys() shouldContainExactlyInAnyOrder listOf("books.one")
            }

            should("report failure rather than throw when the cache is down") {
                val cache = brokenCache(IllegalStateException("redis is unreachable"))

                cache.invalidateContaining("42") shouldBe false
            }

            should("skip a listed key whose entry is already gone") {
                val store = InMemoryCache()
                store.put("books.one", """{"id":"42"}""".toByteArray())
                val cache = partialCache(store, awkwardKey = "books.ghost") { null }

                cache.invalidateContaining("42") shouldBe true

                store.keys() shouldContainExactlyInAnyOrder emptyList()
            }

            should("keep going when one entry cannot be read") {
                val store = InMemoryCache()
                store.put("books.one", """{"id":"42"}""".toByteArray())
                val cache = partialCache(store, awkwardKey = "books.broken") { error("unreadable") }

                cache.invalidateContaining("42") shouldBe true
            }

            should("report that nothing matched when the only readable entry does not mention the id") {
                val store = InMemoryCache()
                store.put("books.one", """{"id":"7"}""".toByteArray())
                val cache = partialCache(store, awkwardKey = "books.broken") { error("unreadable") }

                cache.invalidateContaining("42") shouldBe false
            }

            should("propagate cancellation instead of swallowing it") {
                val cache = brokenCache(CancellationException("scope cancelled"))

                shouldThrow<CancellationException> { cache.invalidateContaining("42") }
            }
        }
    })

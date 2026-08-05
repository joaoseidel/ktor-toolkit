package com.luizalabs.ktor.toolkit.cache

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
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
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

/** A cache whose every operation fails, standing in for a store that is down. */
private class BrokenCache(
    private val failure: () -> Throwable,
) : KeyValueCache {
    override suspend fun get(key: String): ByteArray? = throw failure()

    override suspend fun put(
        key: String,
        value: ByteArray,
    ): Unit = throw failure()

    override suspend fun delete(key: String): Unit = throw failure()

    override suspend fun keys(): List<String> = throw failure()
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
                val produce = {
                    calls.incrementAndGet()
                    Book("1", "Dune")
                }

                val first = withRequest("/books/1") { it.withCache("books", cache, produce = produce) }
                val second = withRequest("/books/1") { it.withCache("books", cache, produce = produce) }

                first shouldBe Book("1", "Dune")
                second shouldBe Book("1", "Dune")
                calls.get() shouldBe 1
            }

            should("treat a different query as a different entry") {
                val cache = InMemoryCache()
                val calls = AtomicInteger()
                val produce = {
                    calls.incrementAndGet()
                    Book("1", "Dune")
                }

                withRequest("/books?page=1") { it.withCache("books", cache, produce = produce) }
                withRequest("/books?page=2") { it.withCache("books", cache, produce = produce) }

                calls.get() shouldBe 2
            }

            should("serve from the origin when the cache is down") {
                val cache = BrokenCache { IllegalStateException("redis is unreachable") }

                val book = withRequest("/books/1") { it.withCache("books", cache) { Book("1", "Dune") } }

                book shouldBe Book("1", "Dune")
            }

            should("propagate cancellation instead of swallowing it") {
                // runCatching would have caught this and quietly broken structured concurrency.
                val cache = BrokenCache { CancellationException("scope cancelled") }

                shouldThrow<CancellationException> {
                    withRequest("/books/1") { it.withCache("books", cache) { Book("1", "Dune") } }
                }
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
                val cache = BrokenCache { CancellationException("scope cancelled") }

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
                val cache = BrokenCache { IllegalStateException("redis is unreachable") }

                cache.invalidateContaining("42") shouldBe false
            }

            should("propagate cancellation instead of swallowing it") {
                val cache = BrokenCache { CancellationException("scope cancelled") }

                shouldThrow<CancellationException> { cache.invalidateContaining("42") }
            }
        }
    })

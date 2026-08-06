package com.luizalabs.ktor.toolkit.cache

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A clock the test moves by hand, so expiry can be exercised without sleeping. */
private class TestClock(
    var now: Instant = Instant.parse("2024-01-01T00:00:00Z"),
) : Clock {
    override fun now(): Instant = now

    fun advance(by: Duration) {
        now += by
    }
}

private fun bytes(value: String) = value.toByteArray()

class InMemoryCacheTest :
    ShouldSpec({
        context("basic operations") {
            should("return what was stored") {
                val cache = InMemoryCache()

                cache.put("a", bytes("1"))

                cache.get("a")?.decodeToString() shouldBe "1"
            }

            should("return null for an absent key") {
                InMemoryCache().get("missing").shouldBeNull()
            }

            should("replace an existing entry") {
                val cache = InMemoryCache()

                cache.put("a", bytes("1"))
                cache.put("a", bytes("2"))

                cache.get("a")?.decodeToString() shouldBe "2"
                cache.size() shouldBe 1
            }

            should("delete an entry") {
                val cache = InMemoryCache()
                cache.put("a", bytes("1"))

                cache.delete("a")

                cache.get("a").shouldBeNull()
            }

            should("list every live key") {
                val cache = InMemoryCache()
                cache.put("a", bytes("1"))
                cache.put("b", bytes("2"))

                cache.keys() shouldContainExactlyInAnyOrder listOf("a", "b")
            }
        }

        context("size bound") {
            should("evict the least recently used entry once full") {
                val cache = InMemoryCache(maxSize = 2)

                cache.put("a", bytes("1"))
                cache.put("b", bytes("2"))
                cache.put("c", bytes("3"))

                cache.get("a").shouldBeNull()
                cache.keys() shouldContainExactlyInAnyOrder listOf("b", "c")
            }

            should("count a read as a use, so a hot key survives") {
                val cache = InMemoryCache(maxSize = 2)

                cache.put("a", bytes("1"))
                cache.put("b", bytes("2"))
                cache.get("a")
                cache.put("c", bytes("3"))

                cache.get("a")?.decodeToString() shouldBe "1"
                cache.get("b").shouldBeNull()
            }

            should("reject a non-positive size") {
                shouldThrow<IllegalArgumentException> { InMemoryCache(maxSize = 0) }
                shouldThrow<IllegalArgumentException> { InMemoryCache(maxSize = -1) }
            }

            should("reject a non-positive ttl, which would expire every entry on arrival") {
                shouldThrow<IllegalArgumentException> { InMemoryCache(ttl = Duration.ZERO) }
                shouldThrow<IllegalArgumentException> { InMemoryCache(ttl = (-1).seconds) }
            }
        }

        context("expiry") {
            should("keep an entry for the whole ttl") {
                val clock = TestClock()
                val cache = InMemoryCache(ttl = 5.minutes, clock = clock)
                cache.put("a", bytes("1"))

                clock.advance(4.minutes)

                cache.get("a")?.decodeToString() shouldBe "1"
            }

            should("drop an entry once the ttl elapses") {
                val clock = TestClock()
                val cache = InMemoryCache(ttl = 5.minutes, clock = clock)
                cache.put("a", bytes("1"))

                clock.advance(5.minutes)

                cache.get("a").shouldBeNull()
            }

            should("leave expired entries out of the key listing") {
                val clock = TestClock()
                val cache = InMemoryCache(ttl = 5.minutes, clock = clock)
                cache.put("a", bytes("1"))
                clock.advance(6.minutes)
                cache.put("b", bytes("2"))

                cache.keys() shouldContainExactlyInAnyOrder listOf("b")
            }

            should("never expire without a ttl") {
                val clock = TestClock()
                val cache = InMemoryCache(clock = clock)
                cache.put("a", bytes("1"))

                clock.advance((60 * 24 * 365).minutes)

                cache.get("a")?.decodeToString() shouldBe "1"
            }
        }
    })

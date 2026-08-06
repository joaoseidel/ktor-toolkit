package com.luizalabs.ktor.toolkit.cache

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.lettuce.core.CompositeArgument
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.RedisException
import io.lettuce.core.RedisFuture
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.SetArgs
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.protocol.CommandArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

private typealias Commands = RedisClusterAsyncCommands<String, ByteArray>

/**
 * A real [RedisFuture] rather than a mock: `RedisFuture` only adds two methods to
 * `CompletableFuture`, so the coroutine bridge under test runs against the genuine implementation.
 */
private class TestRedisFuture<T> :
    CompletableFuture<T>(),
    RedisFuture<T> {
    override fun getError(): String? = null

    override fun await(
        timeout: Long,
        unit: TimeUnit,
    ): Boolean = true
}

private fun <T> completed(value: T): RedisFuture<T> = TestRedisFuture<T>().apply { complete(value) }

private fun <T> failed(cause: Throwable): RedisFuture<T> = TestRedisFuture<T>().apply { completeExceptionally(cause) }

/** A SCAN reply carrying [keys], finished when no [next] cursor is given. */
private fun cursor(
    vararg keys: String,
    next: String? = null,
): RedisFuture<KeyScanCursor<String>> =
    completed(
        KeyScanCursor<String>().apply {
            this.keys.addAll(keys)
            cursor = next ?: "0"
            isFinished = next == null
        },
    )

/** Renders an argument object the way Lettuce would send it. */
private fun render(argument: CompositeArgument): String = CommandArgs(StringCodec.UTF8).also(argument::build).toCommandString()

/** How [render] shows a byte argument — a `SCAN` pattern is one, a `PX` deadline is not. */
private fun asByteArgument(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray())

private fun bytes(value: String) = value.toByteArray()

class LettuceCacheTest :
    ShouldSpec({
        context("construction") {
            should("reject a non-positive ttl") {
                shouldThrow<IllegalArgumentException> { LettuceCache(mockk(), ttl = Duration.ZERO) }
                shouldThrow<IllegalArgumentException> { LettuceCache(mockk(), ttl = -1.minutes) }
            }

            should("reject a ttl Redis would round down to no expiry at all") {
                shouldThrow<IllegalArgumentException> { LettuceCache(mockk(), ttl = 999.nanoseconds) }
            }

            should("accept the smallest expiry Redis records") {
                LettuceCache(mockk(), ttl = 1.milliseconds)
            }

            should("reject a non-positive scan batch size") {
                shouldThrow<IllegalArgumentException> { LettuceCache(mockk(), scanBatchSize = 0) }
                shouldThrow<IllegalArgumentException> { LettuceCache(mockk(), scanBatchSize = -1) }
            }
        }

        context("get") {
            should("return the stored value under the prefixed key") {
                val commands = mockk<Commands>()
                every { commands.get("books:a") } returns completed(bytes("1"))

                val cache = LettuceCache(commands, keyPrefix = "books:")

                cache.get("a")?.decodeToString() shouldBe "1"
            }

            should("return null for an absent key") {
                val commands = mockk<Commands>()
                every { commands.get(any()) } returns completed(null)

                LettuceCache(commands).get("missing").shouldBeNull()
            }

            should("let a Redis failure propagate, so the caller can fall back to the origin") {
                val commands = mockk<Commands>()
                every { commands.get(any()) } returns failed(RedisException("connection reset"))

                shouldThrow<RedisException> { LettuceCache(commands).get("a") }
            }
        }

        context("put") {
            should("store without an expiry when no ttl is set") {
                val commands = mockk<Commands>()
                val value = bytes("1")
                every { commands.set("ktor-toolkit:a", value) } returns completed("OK")

                LettuceCache(commands).put("a", value)

                verify(exactly = 1) { commands.set("ktor-toolkit:a", value) }
            }

            should("let Redis expire the entry when a ttl is set") {
                val commands = mockk<Commands>()
                val value = bytes("1")
                val args = slot<SetArgs>()
                every { commands.set("ktor-toolkit:a", value, capture(args)) } returns completed("OK")

                LettuceCache(commands, ttl = 5.minutes).put("a", value)

                render(args.captured) shouldContain "PX 300000"
            }
        }

        context("delete") {
            should("remove the prefixed key") {
                val commands = mockk<Commands>()
                every { commands.del("books:a") } returns completed(1L)

                LettuceCache(commands, keyPrefix = "books:").delete("a")

                verify(exactly = 1) { commands.del("books:a") }
            }
        }

        context("keys") {
            should("strip the prefix, so namespace invalidation still matches") {
                val commands = mockk<Commands>()
                every { commands.scan(any<ScanArgs>()) } returns cursor("books:one", "books:two")

                LettuceCache(commands, keyPrefix = "books:").keys() shouldContainExactly
                    listOf("one", "two")
            }

            should("follow the cursor until the scan finishes") {
                val commands = mockk<Commands>()
                every { commands.scan(any<ScanArgs>()) } returns cursor("a", next = "17")
                every { commands.scan(any<ScanCursor>(), any<ScanArgs>()) } returns cursor("b")

                LettuceCache(commands, keyPrefix = "").keys() shouldContainExactly listOf("a", "b")
            }

            should("drop the duplicates a scan is allowed to return") {
                val commands = mockk<Commands>()
                every { commands.scan(any<ScanArgs>()) } returns cursor("a", "b", next = "17")
                every { commands.scan(any<ScanCursor>(), any<ScanArgs>()) } returns cursor("b", "c")

                LettuceCache(commands, keyPrefix = "").keys() shouldContainExactly
                    listOf("a", "b", "c")
            }

            should("scan only the keys the cache owns, at the configured batch size") {
                val commands = mockk<Commands>()
                val args = slot<ScanArgs>()
                every { commands.scan(capture(args)) } returns cursor()

                LettuceCache(commands, keyPrefix = "books:", scanBatchSize = 64).keys()

                render(args.captured) shouldContain "MATCH ${asByteArgument("books:*")}"
                render(args.captured) shouldContain "COUNT 64"
            }

            should("match a prefix containing glob metacharacters literally") {
                val commands = mockk<Commands>()
                val args = slot<ScanArgs>()
                every { commands.scan(capture(args)) } returns cursor()

                LettuceCache(commands, keyPrefix = "a*b[c]:").keys()

                render(args.captured) shouldContain
                    "MATCH ${asByteArgument("""a\*b\[c\]:*""")}"
            }
        }

        context("codec") {
            should("encode keys as text and leave values as raw bytes") {
                val codec = LettuceCache.Codec

                codec.decodeKey(codec.encodeKey("books:ç")) shouldBe "books:ç"
                codec.decodeValue(codec.encodeValue(bytes("1"))) shouldBe bytes("1")
            }
        }
    })

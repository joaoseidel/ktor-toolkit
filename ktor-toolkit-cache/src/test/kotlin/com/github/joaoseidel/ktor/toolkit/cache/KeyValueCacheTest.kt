package com.github.joaoseidel.ktor.toolkit.cache

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.delay

/**
 * A store that implements [keys] but not the prefix overload, so `keys(prefix)` runs the interface
 * default — the path a [KeyValueCache] written outside the toolkit takes.
 *
 * Its listing really suspends, the way a network-backed one does. [InMemoryCache] answers without
 * ever parking the caller, which would leave the default's suspension path untried.
 */
private class SuspendingKeyStore(
    private val listing: List<String>,
) : KeyValueCache {
    override suspend fun get(key: String): ByteArray? = null

    override suspend fun put(
        key: String,
        value: ByteArray,
    ) = Unit

    override suspend fun delete(key: String) = Unit

    override suspend fun keys(): List<String> {
        delay(1)
        return listing
    }
}

class KeyValueCacheTest :
    ShouldSpec(
        {
            context("keys(prefix)") {
                should("narrow the listing itself, so a store that cannot match keys still works") {
                    val store = SuspendingKeyStore(listOf("books.one", "book-chapters.one", "authors.one"))

                    store.keys("books.") shouldContainExactly listOf("books.one")
                }
            }
        },
    )

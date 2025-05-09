package com.luizalabs.ktor.toolkit.cache

import java.util.concurrent.ConcurrentHashMap

interface KeyValueCache {
    suspend fun get(key: String): ByteArray?
    suspend fun put(key: String, value: ByteArray)
    suspend fun delete(key: String)
    suspend fun keys(): List<String>
}

class InMemoryCache : KeyValueCache {
    private val store = ConcurrentHashMap<String, ByteArray>()

    override suspend fun get(key: String): ByteArray? = store[key]

    override suspend fun put(key: String, value: ByteArray) {
        store[key] = value
    }

    override suspend fun delete(key: String) {
        store.remove(key)
    }

    override suspend fun keys(): List<String> = store.keys().toList()
}

package com.github.joaoseidel.ktor.toolkit.nats.api

import io.nats.client.JetStreamApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WRONG_LAST_SEQUENCE: Int = 10071

/**
 * A slot only one replica can hold until [bucket]'s TTL expires it. Never renewed and never released, which suits periodic work that is safe to
 * repeat: a holder that dies only costs the slot, and one that overruns the TTL overlaps the next.
 */
class NatsLease(
    private val client: NatsClient,
    private val bucket: BucketSpec,
    private val key: String,
) {
    init {
        requireNotNull(bucket.ttl) { "A lease needs a bucket with a ttl, or it is never free again, but ${bucket.name} has none" }
    }

    /**
     * Takes the slot if nobody holds it.
     *
     * @return `true` if the caller now holds it, `false` if another holder does.
     * @throws java.io.IOException if NATS cannot be reached.
     */
    suspend fun tryAcquire(holder: String): Boolean {
        client.declare(bucket)
        val connection = client.connection()

        return withContext(Dispatchers.IO) {
            try {
                connection.keyValue(bucket.name).create(key, holder.toByteArray())
                true
            } catch (taken: JetStreamApiException) {
                if (taken.apiErrorCode != WRONG_LAST_SEQUENCE) throw taken
                false
            }
        }
    }
}

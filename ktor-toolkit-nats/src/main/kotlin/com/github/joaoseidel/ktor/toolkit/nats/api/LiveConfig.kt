package com.github.joaoseidel.ktor.toolkit.nats.api

import io.nats.client.JetStreamApiException
import io.nats.client.api.KeyValueConfiguration
import io.nats.client.api.KeyValueEntry
import io.nats.client.api.KeyValueOperation
import io.nats.client.api.KeyValueWatcher
import io.nats.client.api.StorageType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

private val logger = LoggerFactory.getLogger(BucketSpec::class.java)
private val WATCH_RETRY: Duration = 5.seconds

/**
 * A key-value bucket.
 *
 * @property history How many revisions of a key are kept, so a change can be seen and reverted.
 * @property ttl How long a value lasts, or `null` for as long as the bucket's limits allow.
 */
data class BucketSpec(
    val name: String,
    val maxBytes: Long,
    val replicas: Int = 1,
    val history: Int = 5,
    val ttl: Duration? = null,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(maxBytes > 0) { "maxBytes must be positive, but was $maxBytes" }
    }

    internal fun toConfiguration(): KeyValueConfiguration =
        KeyValueConfiguration
            .builder()
            .name(name)
            .maxBucketSize(maxBytes)
            .replicas(replicas)
            .maxHistoryPerKey(history)
            .storageType(StorageType.File)
            .apply { ttl?.let { ttl(it.toJavaDuration()) } }
            .build()
}

/** Creates [spec]'s bucket, or brings an existing one in line with it. Safe to run from every replica at every boot. */
suspend fun NatsClient.declare(spec: BucketSpec) {
    val management = connection().keyValueManagement()
    withContext(Dispatchers.IO) {
        try {
            management.create(spec.toConfiguration())
        } catch (_: JetStreamApiException) {
            management.update(spec.toConfiguration())
        }
    }
}

/**
 * Configuration that follows [spec]'s bucket: the result holds [default] until an override arrives, and [default] for as long as NATS cannot be
 * reached, so a service boots and keeps working without it. Returns at once; the bucket is declared and watched in [scope], retrying forever.
 *
 * A key whose value [parse] rejects is logged and skipped, leaving the last good configuration in force. Deleting a key removes its override.
 *
 * @param parse Builds the configuration from the overrides present, by key. Throws to reject them, so a [default] must not be needed to fail.
 */
fun <T> NatsClient.liveConfig(
    spec: BucketSpec,
    scope: CoroutineScope,
    default: T,
    parse: (Map<String, String>) -> T,
): StateFlow<T> {
    val current = MutableStateFlow(default)
    val watcher = ConfigWatcher(spec.name, current, parse)

    scope.launch {
        while (true) {
            try {
                declare(spec)
                val connection = connection()
                val subscription = withContext(Dispatchers.IO) { connection.keyValue(spec.name).watchAll(watcher) }
                try {
                    awaitCancellation()
                } finally {
                    subscription.unsubscribe()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                logger.warn("Could not watch bucket {}; retrying, with the configuration in force unchanged", spec.name, failure)
                delay(WATCH_RETRY)
            }
        }
    }

    return current
}

private class ConfigWatcher<T>(
    private val bucket: String,
    private val current: MutableStateFlow<T>,
    private val parse: (Map<String, String>) -> T,
) : KeyValueWatcher {
    private var overrides: Map<String, String> = emptyMap()

    override fun watch(entry: KeyValueEntry) {
        val candidate =
            if (entry.operation == KeyValueOperation.PUT) overrides + (entry.key to entry.valueAsString.orEmpty()) else overrides - entry.key

        try {
            current.value = parse(candidate)
            overrides = candidate
        } catch (failure: Exception) {
            logger.error("Ignoring {}.{}, which does not parse; keeping the configuration in force", bucket, entry.key, failure)
        }
    }

    override fun endOfData() = Unit
}

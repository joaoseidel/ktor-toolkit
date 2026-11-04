package com.github.joaoseidel.ktor.toolkit.nats.api

import io.nats.client.JetStreamApiException
import io.nats.client.api.DiscardPolicy
import io.nats.client.api.RetentionPolicy
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

/**
 * A file-backed stream with `limits` retention, which keeps a message after it is acknowledged or given up on. Dead letters depend on that:
 * they hold a sequence number into this stream, not a copy of the message.
 *
 * @property maxMessagesPerSubject How many messages each subject keeps, or `null` for no limit of its own.
 * @property duplicateWindow How long a repeated `Nats-Msg-Id` is rejected.
 */
data class StreamSpec(
    val name: String,
    val subjects: List<String>,
    val maxAge: Duration,
    val maxBytes: Long,
    val replicas: Int = 1,
    val maxMessagesPerSubject: Long? = null,
    val duplicateWindow: Duration = 2.minutes,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(subjects.isNotEmpty()) { "A stream needs at least one subject, but $name has none" }
        require(maxAge.isPositive()) { "maxAge must be positive, but was $maxAge" }
        require(maxBytes > 0) { "maxBytes must be positive, but was $maxBytes" }
        require(duplicateWindow <= maxAge) { "duplicateWindow must fit in maxAge, but was $duplicateWindow over $maxAge" }
    }

    /**
     * The stream recording which of this stream's messages were given up on, as server advisories: `MAX_DELIVERIES` when retries ran out and
     * `MSG_TERMINATED` when a message was rejected outright.
     *
     * An entry names a stream sequence, so it only leads back to the message while [maxAge] of this stream still holds it.
     */
    fun deadLetters(
        retention: Duration = 72.hours,
        bytes: Long = DEAD_LETTER_BYTES,
    ): StreamSpec =
        copy(
            name = "DLQ_$name",
            subjects =
                listOf(
                    "\$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.$name.*",
                    "\$JS.EVENT.ADVISORY.CONSUMER.MSG_TERMINATED.$name.*",
                ),
            maxAge = retention,
            maxBytes = bytes,
            maxMessagesPerSubject = null,
            duplicateWindow = minOf(2.minutes, retention),
        )

    internal fun toConfiguration(): StreamConfiguration =
        StreamConfiguration
            .builder()
            .name(name)
            .subjects(subjects)
            .storageType(StorageType.File)
            .retentionPolicy(RetentionPolicy.Limits)
            .discardPolicy(DiscardPolicy.Old)
            .maxAge(maxAge.toJavaDuration())
            .maxBytes(maxBytes)
            .replicas(replicas)
            .duplicateWindow(duplicateWindow.toJavaDuration())
            .apply { maxMessagesPerSubject?.let(::maxMessagesPerSubject) }
            .build()

    private companion object {
        const val DEAD_LETTER_BYTES: Long = 16 * 1024 * 1024
    }
}

/** Creates [spec]'s stream, or brings an existing one in line with it. Safe to run from every replica at every boot. */
suspend fun NatsClient.declare(spec: StreamSpec) {
    val management = connection().jetStreamManagement()
    withContext(Dispatchers.IO) {
        try {
            management.getStreamInfo(spec.name)
            management.updateStream(spec.toConfiguration())
        } catch (_: JetStreamApiException) {
            management.addStream(spec.toConfiguration())
        }
    }
}

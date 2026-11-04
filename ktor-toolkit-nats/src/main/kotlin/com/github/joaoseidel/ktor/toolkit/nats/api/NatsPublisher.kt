package com.github.joaoseidel.ktor.toolkit.nats.api

import io.nats.client.PublishOptions
import io.nats.client.impl.Headers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** NATS did not confirm a publish in time, which may mean it was stored all the same: only a `Nats-Msg-Id` makes a retry safe. */
class NatsPublishTimeoutException(
    subject: String,
    timeout: Duration,
) : RuntimeException("No acknowledgement for $subject within $timeout")

/** Where a stream put a message. [duplicate] means a message with the same id was already there, and this one was dropped. */
data class PublishedMessage(
    val sequence: Long,
    val duplicate: Boolean,
)

/**
 * Publishes to JetStream and waits for the stream's acknowledgement, so a returned [publish] means the message is stored.
 *
 * Carries the current trace context in the message's headers, for [NatsWorker] to continue.
 *
 * @property timeout How long a [publish] may take in all, including the wait for a first connection.
 */
class NatsPublisher(
    private val client: NatsClient,
    private val timeout: Duration = 2.seconds,
) {
    /**
     * @param messageId Sent as `Nats-Msg-Id`: within the stream's duplicate window, a second message with this id is dropped.
     * @throws NatsPublishTimeoutException if no acknowledgement arrived within [timeout].
     * @throws io.nats.client.JetStreamApiException if the stream refused the message.
     * @throws java.io.IOException if no stream captures [subject].
     */
    suspend fun publish(
        subject: String,
        payload: ByteArray,
        messageId: String? = null,
    ): PublishedMessage {
        val options = PublishOptions.builder().apply { messageId?.let(::messageId) }.build()
        val headers = Headers().also(NatsTracing::inject)

        val acknowledgement =
            withTimeoutOrNull(timeout) {
                client
                    .connection()
                    .jetStream()
                    .publishAsync(subject, headers, payload, options)
                    .await()
            } ?: throw NatsPublishTimeoutException(subject, timeout)

        return PublishedMessage(acknowledgement.seqno, acknowledgement.isDuplicate)
    }
}

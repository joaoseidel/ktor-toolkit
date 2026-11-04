package com.github.joaoseidel.ktor.toolkit.nats.api

import io.nats.client.ConsumerContext
import io.nats.client.FetchConsumeOptions
import io.nats.client.Message
import io.nats.client.api.AckPolicy
import io.nats.client.api.ConsumerConfiguration
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

private const val TERM_REASON_LIMIT: Int = 200
private const val INSTRUMENTATION_SCOPE: String = "com.github.joaoseidel.ktor.toolkit.nats"
private val RESTART_DELAY: Duration = 1.seconds
private val FETCH_WAIT: Duration = 5.seconds
private val STREAM_ATTRIBUTE: AttributeKey<String> = AttributeKey.stringKey("stream")
private val CONSUMER_ATTRIBUTE: AttributeKey<String> = AttributeKey.stringKey("consumer")

private val logger = LoggerFactory.getLogger(NatsWorker::class.java)

/**
 * How a failed message is tried again: each entry of [delays] is the wait before one more delivery, so a message gets `delays.size + 1` in all.
 *
 * The delays are applied with `nakWithDelay`, not the consumer's `BackOff`, which only governs messages that were never answered.
 */
data class RetryPolicy(
    val delays: List<Duration> = listOf(1.seconds, 5.seconds, 1.minutes, 5.minutes, 10.minutes),
) {
    val maxDeliveries: Int get() = delays.size + 1

    init {
        require(delays.all { it.isPositive() }) { "Every delay must be positive, but was $delays" }
    }

    /** The wait after delivery number [attempt], counted from 1, or `null` when it was the last one. */
    fun delayAfter(attempt: Long): Duration? = delays.getOrNull((attempt - 1).toInt())
}

/**
 * What can change on a running worker.
 *
 * @property batchSize How many messages are held and processed at once. Their ack clocks all start at delivery, so a size the worker cannot
 * finish within [ackWait] redelivers messages it has not started on.
 */
data class WorkerTuning(
    val retry: RetryPolicy = RetryPolicy(),
    val ackWait: Duration = 60.seconds,
    val batchSize: Int = 10,
) {
    init {
        require(ackWait.isPositive()) { "ackWait must be positive, but was $ackWait" }
        require(batchSize > 0) { "batchSize must be positive, but was $batchSize" }
    }

    /**
     * This tuning with whatever [overrides] sets under [prefix]: `<prefix>.retry_seconds` as a comma-separated list, `<prefix>.ack_wait_seconds` and
     * `<prefix>.batch_size`. Meant for a [liveConfig] parser.
     *
     * @throws IllegalArgumentException if a value does not parse or breaks an invariant.
     */
    fun overriddenBy(
        overrides: Map<String, String>,
        prefix: String,
    ): WorkerTuning =
        WorkerTuning(
            retry =
                overrides["$prefix.retry_seconds"]
                    ?.let { list -> RetryPolicy(list.split(',').map { it.trim().toLong().seconds }) }
                    ?: retry,
            ackWait = overrides["$prefix.ack_wait_seconds"]?.toLong()?.seconds ?: ackWait,
            batchSize = overrides["$prefix.batch_size"]?.toInt() ?: batchSize,
        )
}

/**
 * A message handed to a [NatsWorker]'s handler.
 *
 * @property attempt Which delivery this is, counted from 1.
 * @property sequence The stream sequence: what a dead-letter entry points at.
 */
class Delivery(
    val subject: String,
    val payload: ByteArray,
    val attempt: Long,
    val sequence: Long,
)

/** Thrown by a handler for a message that no retry can fix: it goes to the dead letters at once, with [message] as the reason. */
class PoisonMessageException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * A durable pull consumer with retries and dead letters, which declares the stream, its [StreamSpec.deadLetters] and itself when [run].
 *
 * A handler that returns acknowledges the message. One that throws has it redelivered by [WorkerTuning.retry], and the failure logged, because
 * the dead-letter advisory does not carry it. One that throws [PoisonMessageException] rejects it for good. Delivery is at least once, so a
 * handler must tolerate a repeat. Run one per replica: they share the work.
 *
 * Reports `nats.consumer.pending`, `nats.consumer.ack_pending`, `nats.consumer.redelivered` and `nats.dead_letters` through OpenTelemetry.
 *
 * @param name The durable consumer's name.
 * @param tuning Applied between batches, updating the consumer on the server.
 * @param paused While `true`, nothing is fetched, so messages wait in the stream. One already being fetched when it turns `true` goes back unhandled, at
 * the cost of one attempt, so nothing is handled during a pause.
 * @param minAge A message younger than this goes back until it is old enough, at the cost of one attempt. For a publisher that sends a job before the
 * write it refers to: without it the job can overtake the write.
 */
class NatsWorker(
    private val client: NatsClient,
    private val stream: StreamSpec,
    private val name: String,
    private val filterSubject: String? = null,
    private val tuning: StateFlow<WorkerTuning> = MutableStateFlow(WorkerTuning()),
    private val paused: StateFlow<Boolean> = MutableStateFlow(false),
    private val minAge: Duration = Duration.ZERO,
    private val handle: suspend (Delivery) -> Unit,
) {
    /** Works until cancelled, surviving NATS outages. Never returns normally. */
    suspend fun run(): Nothing {
        observe()
        while (true) {
            try {
                withContext(Dispatchers.IO) {
                    client.declare(stream)
                    client.declare(stream.deadLetters())
                }
                work()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                logger.warn("Worker {} on {} stopped; restarting", name, stream.name, failure)
                delay(RESTART_DELAY)
            }
        }
    }

    private suspend fun work(): Nothing {
        var bound: Bound? = null

        while (true) {
            paused.first { !it }

            val current = tuning.value
            val consumer = bound?.takeIf { it.tuning == current } ?: Bound(current, bind(current)).also { bound = it }

            val batch = fetch(consumer.context, current.batchSize)
            coroutineScope { batch.forEach { message -> launch { settle(message, current) } } }
        }
    }

    private suspend fun bind(current: WorkerTuning): ConsumerContext {
        val configuration =
            ConsumerConfiguration
                .builder()
                .durable(name)
                .ackPolicy(AckPolicy.Explicit)
                .ackWait(current.ackWait.toJavaDuration())
                .maxDeliver(current.retry.maxDeliveries.toLong())
                .apply { filterSubject?.let(::filterSubject) }
                .build()
        val connection = client.connection()

        return withContext(Dispatchers.IO) {
            connection.getStreamContext(stream.name).createOrUpdateConsumer(configuration)
        }
    }

    private suspend fun fetch(
        consumer: ConsumerContext,
        batchSize: Int,
    ): List<Message> =
        runInterruptible(Dispatchers.IO) {
            // noWait with an expiry answers as soon as any message exists; a plain fetch waits out the expiry to fill the batch.
            val options =
                FetchConsumeOptions
                    .builder()
                    .maxMessages(batchSize)
                    .noWaitExpiresIn(FETCH_WAIT.inWholeMilliseconds)
                    .build()
            consumer.fetch(options).use { fetched -> generateSequence { fetched.nextMessage() }.toList() }
        }

    private suspend fun settle(
        message: Message,
        current: WorkerTuning,
    ) {
        val metadata = message.metaData()

        heldBackFor(metadata.timestamp())?.let { wait -> return message.nakWithDelay(wait.toJavaDuration()) }

        val delivery = Delivery(message.subject, message.payload(), metadata.deliveredCount(), metadata.streamSequence())

        coroutineScope {
            // Without this, a handler slower than ackWait is delivered again while it still runs.
            val heartbeat =
                launch {
                    while (true) {
                        delay(current.ackWait / 2)
                        message.inProgress()
                    }
                }

            try {
                NatsTracing.consuming("${stream.name} process", message.headers) { handle(delivery) }
                message.ack()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (poison: PoisonMessageException) {
                logger.error("Rejecting {} #{} on {}", stream.name, delivery.sequence, delivery.subject, poison)
                message.terminate(poison.message)
            } catch (failure: Exception) {
                val wait = current.retry.delayAfter(delivery.attempt)
                val outcome = if (wait == null) "giving up" else "retrying in $wait"
                logger.warn(
                    "Delivery {}/{} of {} #{} on {} failed; {}",
                    delivery.attempt,
                    current.retry.maxDeliveries,
                    stream.name,
                    delivery.sequence,
                    delivery.subject,
                    outcome,
                    failure,
                )
                if (wait == null) message.nak() else message.nakWithDelay(wait.toJavaDuration())
            } finally {
                heartbeat.cancel()
            }
        }
    }

    private fun heldBackFor(publishedAt: ZonedDateTime): Duration? {
        if (paused.value) return RESTART_DELAY

        val age =
            java.time.Duration
                .between(publishedAt, ZonedDateTime.now())
                .toKotlinDuration()
        return (minAge - age).takeIf { it.isPositive() }
    }

    // The client's term() cannot carry a reason, and the server copies what follows +TERM into the advisory.
    private fun Message.terminate(reason: String) {
        val singleLine = reason.replace('\n', ' ').take(TERM_REASON_LIMIT)
        connection.publish(replyTo, "+TERM $singleLine".toByteArray())
    }

    private fun observe() {
        val meter = GlobalOpenTelemetry.getMeter(INSTRUMENTATION_SCOPE)
        val attributes = Attributes.of(STREAM_ATTRIBUTE, stream.name, CONSUMER_ATTRIBUTE, name)
        val pending = meter.gaugeBuilder("nats.consumer.pending").ofLongs().buildObserver()
        val ackPending = meter.gaugeBuilder("nats.consumer.ack_pending").ofLongs().buildObserver()
        val redelivered = meter.gaugeBuilder("nats.consumer.redelivered").ofLongs().buildObserver()
        val deadLetters = meter.gaugeBuilder("nats.dead_letters").ofLongs().buildObserver()

        meter.batchCallback(
            {
                try {
                    client.connectionOrNull()?.let { connection ->
                        val management = connection.jetStreamManagement()
                        val consumer = management.getConsumerInfo(stream.name, name)
                        pending.record(consumer.numPending, attributes)
                        ackPending.record(consumer.numAckPending, attributes)
                        redelivered.record(consumer.redelivered, attributes)
                        deadLetters.record(management.getStreamInfo(stream.deadLetters().name).streamState.msgCount, attributes)
                    }
                } catch (failure: Exception) {
                    logger.debug("Could not read {} on {} for its gauges", name, stream.name, failure)
                }
            },
            pending,
            ackPending,
            redelivered,
            deadLetters,
        )
    }

    private class Bound(
        val tuning: WorkerTuning,
        val context: ConsumerContext,
    )
}

internal fun Message.payload(): ByteArray = data ?: ByteArray(0)

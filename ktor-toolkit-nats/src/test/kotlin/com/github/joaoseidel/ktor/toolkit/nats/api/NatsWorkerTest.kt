package com.github.joaoseidel.ktor.toolkit.nats.api

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.nats.client.Message
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val quickRetries = WorkerTuning(retry = RetryPolicy(listOf(50.milliseconds, 50.milliseconds)))

class NatsWorkerTest :
    ShouldSpec({
        val client = NatsTestServer.client()
        val publisher = NatsPublisher(client)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        afterSpec {
            scope.cancel()
            client.close()
        }

        fun CoroutineScope.start(worker: NatsWorker) = launch { worker.run() }

        suspend fun deadLetters(stream: StreamSpec): List<String> {
            val management = client.connection().jetStreamManagement()
            val name = stream.deadLetters().name
            val count = management.getStreamInfo(name).streamState.msgCount
            return (1..count).map { String(management.getMessage(name, it).data ?: ByteArray(0)) }
        }

        context("a message whose handler returns") {
            should("be acknowledged, so it is never delivered again") {
                val stream = streamNamed()
                client.declare(stream)
                val deliveries = AtomicInteger()
                scope.start(NatsWorker(client, stream, "worker", tuning = MutableStateFlow(quickRetries)) { deliveries.incrementAndGet() })

                publisher.publish("${stream.name}.a", "payload".toByteArray())

                eventually(5.seconds) {
                    client
                        .connection()
                        .jetStreamManagement()
                        .getConsumerInfo(stream.name, "worker")
                        .numAckPending shouldBe 0
                    deliveries.get() shouldBe 1
                }
            }

            should("hand the handler what was published, numbered from the first attempt") {
                val stream = streamNamed()
                client.declare(stream)
                val received = CompletableDeferred<Delivery>()
                scope.start(NatsWorker(client, stream, "worker") { received.complete(it) })

                val published = publisher.publish("${stream.name}.a", "payload".toByteArray())

                val delivery = received.await()
                delivery.subject shouldBe "${stream.name}.a"
                String(delivery.payload) shouldBe "payload"
                delivery.attempt shouldBe 1
                delivery.sequence shouldBe published.sequence
            }
        }

        context("a message whose handler throws") {
            should("be retried as often as the policy says, then leave a dead letter pointing at it") {
                val stream = streamNamed()
                client.declare(stream)
                val attempts = ConcurrentHashMap.newKeySet<Long>()
                scope.start(
                    NatsWorker(client, stream, "worker", tuning = MutableStateFlow(quickRetries)) {
                        attempts += it.attempt
                        error("handler failed")
                    },
                )

                val published = publisher.publish("${stream.name}.a", "payload".toByteArray())

                eventually(10.seconds) {
                    val entries = deadLetters(stream)
                    entries shouldHaveSize 1
                    entries.single() shouldContain "\"stream_seq\":${published.sequence}"
                    entries.single() shouldContain "max_deliver"
                }
                attempts shouldBe setOf(1L, 2L, 3L)
            }

            should("keep the message in its stream, so a dead letter can be redriven") {
                val stream = streamNamed()
                client.declare(stream)
                scope.start(NatsWorker(client, stream, "worker", tuning = MutableStateFlow(quickRetries)) { error("handler failed") })

                val published = publisher.publish("${stream.name}.a", "payload".toByteArray())

                eventually(10.seconds) { deadLetters(stream) shouldHaveSize 1 }
                val stored = client.connection().jetStreamManagement().getMessage(stream.name, published.sequence)
                stored.data?.let(::String) shouldBe "payload"
            }
        }

        context("a poison message") {
            should("go to the dead letters on its first delivery, carrying the reason") {
                val stream = streamNamed()
                client.declare(stream)
                val deliveries = AtomicInteger()
                scope.start(
                    NatsWorker(client, stream, "worker", tuning = MutableStateFlow(quickRetries)) {
                        deliveries.incrementAndGet()
                        throw PoisonMessageException("not an event\nat all")
                    },
                )

                publisher.publish("${stream.name}.a", "{ not json".toByteArray())

                eventually(5.seconds) {
                    val entries = deadLetters(stream)
                    entries shouldHaveSize 1
                    entries.single() shouldContain "not an event at all"
                }
                deliveries.get() shouldBe 1
            }
        }

        context("a slow handler") {
            should("keep its message to itself past the ack wait, with the heartbeat") {
                val stream = streamNamed()
                client.declare(stream)
                val deliveries = AtomicInteger()
                val tuning = WorkerTuning(ackWait = 500.milliseconds)
                scope.start(
                    NatsWorker(client, stream, "worker", tuning = MutableStateFlow(tuning)) {
                        deliveries.incrementAndGet()
                        delay(1.5.seconds)
                    },
                )

                publisher.publish("${stream.name}.a", "payload".toByteArray())

                eventually(5.seconds) {
                    client
                        .connection()
                        .jetStreamManagement()
                        .getConsumerInfo(stream.name, "worker")
                        .delivered.streamSequence shouldBe 1
                    client
                        .connection()
                        .jetStreamManagement()
                        .getConsumerInfo(stream.name, "worker")
                        .numAckPending shouldBe 0
                }
                deliveries.get() shouldBe 1
            }
        }

        context("a paused worker") {
            should("leave messages in the stream, with their attempts unspent, until resumed") {
                val stream = streamNamed()
                client.declare(stream)
                val paused = MutableStateFlow(true)
                val received = CompletableDeferred<Delivery>()
                scope.start(NatsWorker(client, stream, "worker", paused = paused) { received.complete(it) })

                publisher.publish("${stream.name}.a", "payload".toByteArray())
                delay(300.milliseconds)
                received.isCompleted shouldBe false

                paused.value = false

                received.await().attempt shouldBe 1
            }
        }

        context("a pause that catches a fetch in flight") {
            should("send the message back unhandled, so nothing is handled during a pause") {
                val stream = streamNamed()
                client.declare(stream)
                val paused = MutableStateFlow(false)
                val received = CompletableDeferred<Delivery>()
                scope.start(NatsWorker(client, stream, "worker", paused = paused) { received.complete(it) })
                eventually(5.seconds) { client.connection().jetStreamManagement().getConsumerInfo(stream.name, "worker") }
                delay(200.milliseconds)

                paused.value = true
                publisher.publish("${stream.name}.a", "payload".toByteArray())
                delay(500.milliseconds)
                received.isCompleted shouldBe false

                paused.value = false

                received.await().attempt shouldBe 2
            }
        }

        context("a worker with a minimum age") {
            should("hold a fresh message back, so a job cannot overtake the write it refers to") {
                val stream = streamNamed()
                client.declare(stream)
                val received = CompletableDeferred<Delivery>()
                scope.start(NatsWorker(client, stream, "worker", minAge = 700.milliseconds) { received.complete(it) })

                publisher.publish("${stream.name}.a", "payload".toByteArray())
                delay(200.milliseconds)
                received.isCompleted shouldBe false

                received.await().attempt shouldBe 2
            }
        }

        context("a tuning change") {
            should("reach the consumer on the server without a restart") {
                val stream = streamNamed()
                client.declare(stream)
                val tuning = MutableStateFlow(quickRetries)
                val handled = Channelled()
                scope.start(NatsWorker(client, stream, "worker", tuning = tuning) { handled.hit() })
                publisher.publish("${stream.name}.a", "first".toByteArray())
                eventually(5.seconds) { handled.count shouldBe 1 }

                tuning.value = WorkerTuning(retry = RetryPolicy(List(5) { 50.milliseconds }))
                publisher.publish("${stream.name}.a", "second".toByteArray())

                eventually(10.seconds) {
                    handled.count shouldBe 2
                    client
                        .connection()
                        .jetStreamManagement()
                        .getConsumerInfo(stream.name, "worker")
                        .consumerConfiguration.maxDeliver shouldBe 6
                }
            }
        }

        context("a stream that cannot be declared") {
            should("keep trying instead of dying, and work as soon as it can") {
                val name = uniqueName()
                val taken = streamNamed(name)
                val wanted = taken.copy(name = uniqueName())
                client.declare(taken)
                val received = CompletableDeferred<Delivery>()
                val running = scope.start(NatsWorker(client, wanted, "worker") { received.complete(it) })
                delay(300.milliseconds)
                running.isActive shouldBe true

                client.connection().jetStreamManagement().deleteStream(taken.name)
                eventually(5.seconds) { client.connection().jetStreamManagement().getStreamInfo(wanted.name) }
                publisher.publish("$name.a", "payload".toByteArray())

                received.await().attempt shouldBe 1
            }
        }

        context("a filtered worker") {
            should("receive only the subjects it filters on") {
                val stream = streamNamed()
                client.declare(stream)
                val received = CompletableDeferred<Delivery>()
                scope.start(NatsWorker(client, stream, "worker", filterSubject = "${stream.name}.wanted") { received.complete(it) })

                publisher.publish("${stream.name}.other", "skipped".toByteArray())
                publisher.publish("${stream.name}.wanted", "taken".toByteArray())

                received.await().subject shouldBe "${stream.name}.wanted"
            }
        }

        context("a message with no body") {
            should("read as an empty payload, because the client may hand back null") {
                val message = mockk<Message>()
                every { message.data } returns null

                message.payload().size shouldBe 0
            }
        }

        context("an empty message") {
            should("reach the handler with an empty payload") {
                val stream = streamNamed()
                client.declare(stream)
                val received = CompletableDeferred<Delivery>()
                scope.start(NatsWorker(client, stream, "worker") { received.complete(it) })

                publisher.publish("${stream.name}.a", ByteArray(0))

                received.await().payload.size shouldBe 0
            }
        }

        context("the gauges") {
            suspend fun gauges(block: suspend (InMemoryMetricReader) -> Unit) {
                val reader = InMemoryMetricReader.create()
                GlobalOpenTelemetry.resetForTest()
                GlobalOpenTelemetry.set(
                    OpenTelemetrySdk.builder().setMeterProvider(SdkMeterProvider.builder().registerMetricReader(reader).build()).build(),
                )
                try {
                    block(reader)
                } finally {
                    GlobalOpenTelemetry.resetForTest()
                }
            }

            should("report what a worker holds and what it gave up on") {
                gauges { reader ->
                    val stream = streamNamed()
                    client.declare(stream)
                    scope.start(NatsWorker(client, stream, "worker", tuning = MutableStateFlow(quickRetries)) { error("handler failed") })
                    publisher.publish("${stream.name}.a", "payload".toByteArray())

                    eventually(10.seconds) { deadLetters(stream) shouldHaveSize 1 }

                    val metrics = reader.collectAllMetrics().associateBy { it.name }
                    metrics
                        .getValue("nats.dead_letters")
                        .longGaugeData.points
                        .sumOf { it.value } shouldBe 1
                    metrics.keys shouldContainAll listOf("nats.consumer.pending", "nats.consumer.ack_pending", "nats.consumer.redelivered")
                }
            }

            should("stay quiet about a consumer that does not exist yet, rather than fail the export") {
                gauges { reader ->
                    val stream = streamNamed()
                    client.declare(stream)
                    scope.start(NatsWorker(client, stream, "worker", paused = MutableStateFlow(true)) { })

                    eventually(5.seconds) { client.connection().jetStreamManagement().getStreamInfo(stream.deadLetters().name) }

                    reader.collectAllMetrics().none { it.name == "nats.dead_letters" } shouldBe true
                }
            }

            should("stay quiet before the first connection") {
                gauges { reader ->
                    val unreachable = NatsClient.connect(NatsSettings("nats://127.0.0.1:1"))
                    scope.start(NatsWorker(unreachable, streamNamed(), "worker") { })

                    eventually(5.seconds) { reader.collectAllMetrics().none { it.name == "nats.dead_letters" } shouldBe true }
                    unreachable.close()
                }
            }
        }

        context("WorkerTuning") {
            should("reject an ack wait the server would treat as unset") {
                shouldThrow<IllegalArgumentException> { WorkerTuning(ackWait = Duration.ZERO) }
            }

            should("reject a batch that could never hold a message") {
                shouldThrow<IllegalArgumentException> { WorkerTuning(batchSize = 0) }
            }
        }

        context("WorkerTuning.overriddenBy") {
            should("change only what the overrides name, under their prefix") {
                val tuning = WorkerTuning(batchSize = 5)

                val overridden = tuning.overriddenBy(mapOf("worker.push.ack_wait_seconds" to "30", "worker.other.batch_size" to "9"), "worker.push")

                overridden shouldBe WorkerTuning(ackWait = 30.seconds, batchSize = 5)
            }

            should("read a retry table and a batch size") {
                val overridden = WorkerTuning().overriddenBy(mapOf("w.retry_seconds" to "1, 2", "w.batch_size" to "3"), "w")

                overridden.retry shouldBe RetryPolicy(listOf(1.seconds, 2.seconds))
                overridden.batchSize shouldBe 3
            }

            should("reject a value that does not parse, so a live config keeps the last good one") {
                shouldThrow<IllegalArgumentException> { WorkerTuning().overriddenBy(mapOf("w.batch_size" to "many"), "w") }
            }

            should("reject a value that breaks an invariant") {
                shouldThrow<IllegalArgumentException> { WorkerTuning().overriddenBy(mapOf("w.retry_seconds" to "0"), "w") }
            }
        }

        context("RetryPolicy") {
            should("allow one delivery more than it has delays, because the first is not a retry") {
                RetryPolicy(listOf(1.seconds, 2.seconds)).maxDeliveries shouldBe 3
            }

            should("reject a delay that would redeliver at once") {
                shouldThrow<IllegalArgumentException> { RetryPolicy(listOf(1.seconds, Duration.ZERO)) }
            }

            should("offer no delay after the last delivery") {
                val policy = RetryPolicy(listOf(1.seconds, 2.seconds))

                policy.delayAfter(1) shouldBe 1.seconds
                policy.delayAfter(2) shouldBe 2.seconds
                policy.delayAfter(3) shouldBe null
            }
        }
    })

private class Channelled {
    private val hits = AtomicInteger()
    val count: Int get() = hits.get()

    fun hit() {
        hits.incrementAndGet()
    }
}

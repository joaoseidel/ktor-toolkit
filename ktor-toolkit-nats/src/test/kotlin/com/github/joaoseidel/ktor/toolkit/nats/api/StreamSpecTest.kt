package com.github.joaoseidel.ktor.toolkit.nats.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class StreamSpecTest :
    ShouldSpec({
        val stream = StreamSpec(name = "ORDERS", subjects = listOf("orders.>"), maxAge = 24.hours, maxBytes = 1024)

        context("StreamSpec.deadLetters") {
            should("capture only the two advisories of giving up on this stream's messages") {
                val deadLetters = stream.deadLetters()

                deadLetters.name shouldBe "DLQ_ORDERS"
                deadLetters.subjects shouldBe
                    listOf(
                        "\$JS.EVENT.ADVISORY.CONSUMER.MAX_DELIVERIES.ORDERS.*",
                        "\$JS.EVENT.ADVISORY.CONSUMER.MSG_TERMINATED.ORDERS.*",
                    )
            }

            should("not inherit a per-subject limit, which would forget a consumer's older dead letters") {
                stream.copy(maxMessagesPerSubject = 1).deadLetters().maxMessagesPerSubject shouldBe null
            }

            should("shrink the duplicate window to fit a short retention") {
                stream.deadLetters(retention = 30.seconds).duplicateWindow shouldBe 30.seconds
            }
        }

        context("NatsClient.declare") {
            should("bring an existing stream in line, so a boot can change a limit") {
                val client = NatsTestServer.client()
                val spec = streamNamed()
                client.declare(spec)

                client.declare(spec.copy(maxBytes = 2048))

                client
                    .connection()
                    .jetStreamManagement()
                    .getStreamInfo(spec.name)
                    .configuration.maxBytes shouldBe 2048
                client.close()
            }

            should("keep a message per subject only up to the limit") {
                val client = NatsTestServer.client()
                val spec = streamNamed().copy(maxMessagesPerSubject = 2)
                client.declare(spec)
                val publisher = NatsPublisher(client)

                repeat(3) { publisher.publish("${spec.name}.a", "message".toByteArray()) }

                client
                    .connection()
                    .jetStreamManagement()
                    .getStreamInfo(spec.name)
                    .streamState.msgCount shouldBe 2
                client.close()
            }
        }

        context("StreamSpec") {
            should("reject a blank name") {
                shouldThrow<IllegalArgumentException> { stream.copy(name = " ") }
            }

            should("reject an age limit that keeps nothing") {
                shouldThrow<IllegalArgumentException> { stream.copy(maxAge = Duration.ZERO) }
            }

            should("reject a byte limit that keeps nothing") {
                shouldThrow<IllegalArgumentException> { stream.copy(maxBytes = 0) }
            }

            should("reject a stream that captures nothing") {
                shouldThrow<IllegalArgumentException> { stream.copy(subjects = emptyList()) }
            }

            should("reject a duplicate window longer than the stream remembers") {
                shouldThrow<IllegalArgumentException> { stream.copy(maxAge = 1.seconds) }
            }
        }
    })

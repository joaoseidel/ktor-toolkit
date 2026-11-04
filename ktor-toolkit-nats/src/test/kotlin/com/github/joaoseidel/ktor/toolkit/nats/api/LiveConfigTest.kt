package com.github.joaoseidel.ktor.toolkit.nats.api

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_THRESHOLD: Int = 80

private fun threshold(overrides: Map<String, String>): Int = overrides["threshold"]?.let { it.toInt() } ?: DEFAULT_THRESHOLD

class LiveConfigTest :
    ShouldSpec({
        val client = NatsTestServer.client()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        afterSpec {
            scope.cancel()
            client.close()
        }

        fun bucket() = BucketSpec(name = "config-${uniqueName()}", maxBytes = 1024 * 1024)

        suspend fun put(
            spec: BucketSpec,
            key: String,
            value: String,
        ) {
            client.declare(spec)
            client.connection().keyValue(spec.name).put(key, value)
        }

        context("NatsClient.liveConfig") {
            should("hold the default while the bucket is empty") {
                val spec = bucket()

                val config = client.liveConfig(spec, scope, DEFAULT_THRESHOLD, ::threshold)
                client.declare(spec)

                config.value shouldBe DEFAULT_THRESHOLD
            }

            should("pick up what was already in the bucket at boot") {
                val spec = bucket()
                put(spec, "threshold", "42")

                val config = client.liveConfig(spec, scope, DEFAULT_THRESHOLD, ::threshold)

                eventually(5.seconds) { config.value shouldBe 42 }
            }

            should("follow a change without a restart") {
                val spec = bucket()
                val config = client.liveConfig(spec, scope, DEFAULT_THRESHOLD, ::threshold)

                put(spec, "threshold", "42")

                eventually(5.seconds) { config.value shouldBe 42 }
            }

            should("keep the last good configuration when a value does not parse") {
                val spec = bucket()
                val config = client.liveConfig(spec, scope, DEFAULT_THRESHOLD, ::threshold)
                put(spec, "threshold", "42")
                eventually(5.seconds) { config.value shouldBe 42 }

                put(spec, "threshold", "not a number")
                put(spec, "other", "value")

                eventually(5.seconds) {
                    client
                        .connection()
                        .keyValue(spec.name)
                        .get("other")
                        .valueAsString shouldBe "value"
                }
                config.value shouldBe 42
            }

            should("fall back to the default when an override is deleted") {
                val spec = bucket()
                val config = client.liveConfig(spec, scope, DEFAULT_THRESHOLD, ::threshold)
                put(spec, "threshold", "42")
                eventually(5.seconds) { config.value shouldBe 42 }

                client.connection().keyValue(spec.name).delete("threshold")

                eventually(5.seconds) { config.value shouldBe DEFAULT_THRESHOLD }
            }

            should("read an empty value as an empty string, for parse to judge") {
                val spec = bucket()
                val config = client.liveConfig(spec, scope, "default") { it["name"] ?: "default" }
                client.declare(spec)

                client.connection().keyValue(spec.name).put("name", ByteArray(0))

                eventually(5.seconds) { config.value shouldBe "" }
            }

            should("keep the default and keep trying when the bucket cannot be declared") {
                val impossible = bucket().copy(replicas = 3)

                val config = client.liveConfig(impossible, scope, DEFAULT_THRESHOLD, ::threshold)
                delay(300.milliseconds)

                config.value shouldBe DEFAULT_THRESHOLD
            }

            should("hold the default when NATS cannot be reached, so a service boots without it") {
                val unreachable = NatsClient.connect(NatsSettings("nats://127.0.0.1:1"))

                try {
                    unreachable.liveConfig(bucket(), scope, DEFAULT_THRESHOLD, ::threshold).value shouldBe DEFAULT_THRESHOLD
                } finally {
                    unreachable.close()
                }
            }
        }

        context("BucketSpec") {
            should("reject a blank name") {
                shouldThrow<IllegalArgumentException> { BucketSpec(name = " ", maxBytes = 1024) }
            }

            should("reject a byte limit that keeps nothing") {
                shouldThrow<IllegalArgumentException> { BucketSpec(name = "bucket", maxBytes = 0) }
            }
        }

        context("NatsClient.declare") {
            should("bring an existing bucket in line, so a change of history reaches it") {
                val spec = bucket()
                client.declare(spec)

                client.declare(spec.copy(history = 2))

                client
                    .connection()
                    .keyValueManagement()
                    .getStatus(spec.name)
                    .maxHistoryPerKey shouldBe 2
            }
        }
    })

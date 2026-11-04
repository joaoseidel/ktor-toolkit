package com.github.joaoseidel.ktor.toolkit.nats.api

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.nats.client.JetStreamApiException
import kotlin.time.Duration.Companion.seconds

class NatsLeaseTest :
    ShouldSpec({
        val client = NatsTestServer.client()

        afterSpec { client.close() }

        fun bucket(ttl: kotlin.time.Duration? = 1.seconds) =
            BucketSpec(name = "lease-${uniqueName()}", maxBytes = 1024 * 1024, history = 1, ttl = ttl)

        context("NatsLease.tryAcquire") {
            should("give the slot to one holder only") {
                val lease = NatsLease(client, bucket(), "sweep")

                lease.tryAcquire("first") shouldBe true
                lease.tryAcquire("second") shouldBe false
            }

            should("free the slot when the ttl runs out, so a dead holder only costs its turn") {
                val lease = NatsLease(client, bucket(), "sweep")
                lease.tryAcquire("first") shouldBe true

                eventually(5.seconds) { lease.tryAcquire("second") shouldBe true }
            }
        }

        context("a bucket that refuses the write") {
            should("let the failure through, rather than report the slot as taken") {
                val tiny = bucket().copy(maxBytes = 1)
                val lease = NatsLease(client, tiny, "sweep")

                shouldThrow<JetStreamApiException> { lease.tryAcquire("a holder far longer than the bucket can store") }
            }
        }

        context("NatsLease") {
            should("reject a bucket without a ttl, whose slot would never come free") {
                shouldThrow<IllegalArgumentException> { NatsLease(client, bucket(ttl = null), "sweep") }
            }
        }
    })

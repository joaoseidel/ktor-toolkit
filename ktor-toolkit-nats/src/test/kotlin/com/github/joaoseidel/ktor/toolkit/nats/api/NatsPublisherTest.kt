package com.github.joaoseidel.ktor.toolkit.nats.api

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.nats.client.impl.Headers
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.trace.SdkTracerProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

class NatsPublisherTest :
    ShouldSpec({
        val client = NatsTestServer.client()
        val publisher = NatsPublisher(client)

        afterSpec { client.close() }

        context("NatsPublisher.publish") {
            should("say where the stream put the message") {
                val stream = streamNamed()
                client.declare(stream)

                val first = publisher.publish("${stream.name}.a", "one".toByteArray())
                val second = publisher.publish("${stream.name}.a", "two".toByteArray())

                first shouldBe PublishedMessage(1, duplicate = false)
                second shouldBe PublishedMessage(2, duplicate = false)
            }

            should("drop a repeated id, so a retried publish cannot store a message twice") {
                val stream = streamNamed()
                client.declare(stream)

                publisher.publish("${stream.name}.a", "one".toByteArray(), messageId = "same")
                val repeated = publisher.publish("${stream.name}.a", "one".toByteArray(), messageId = "same")

                repeated shouldBe PublishedMessage(1, duplicate = true)
            }

            should("fail when no stream captures the subject, so the caller knows nothing was stored") {
                shouldThrow<Exception> { publisher.publish("nobody.listens", "one".toByteArray()) }
            }

            should("give up within its timeout when NATS cannot be reached, so a request is not held hostage") {
                val unreachable = NatsClient.connect(NatsSettings("nats://127.0.0.1:1"))

                try {
                    shouldThrow<NatsPublishTimeoutException> {
                        NatsPublisher(unreachable, timeout = 100.milliseconds).publish("any.subject", "one".toByteArray())
                    }
                } finally {
                    unreachable.close()
                }
            }
        }

        context("NatsClient.connect") {
            should("present the name and credentials it was given") {
                val named = NatsClient.connect(NatsSettings(NatsTestServer.url, "user", "password", "worker-1"))

                named.connection().options.connectionName shouldBe "worker-1"
                named.close()
            }
        }

        context("tracing") {
            should("continue the publisher's trace in the worker, so one trace crosses the stream") {
                val telemetry =
                    OpenTelemetrySdk
                        .builder()
                        .setTracerProvider(SdkTracerProvider.builder().build())
                        .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                        .build()
                GlobalOpenTelemetry.resetForTest()
                GlobalOpenTelemetry.set(telemetry)

                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
                val stream = streamNamed()
                client.declare(stream)
                val observed = CompletableDeferred<String>()
                scope.launch { NatsWorker(client, stream, "worker") { observed.complete(Span.current().spanContext.traceId) }.run() }

                val span = telemetry.getTracer("test").spanBuilder("publish").startSpan()
                withContext(Context.current().with(span).asContextElement()) {
                    publisher.publish("${stream.name}.a", "one".toByteArray())
                }
                span.end()

                observed.await() shouldBe span.spanContext.traceId
                scope.cancel()
                GlobalOpenTelemetry.resetForTest()
            }

            should("read a trace header from a message that has one, and nothing from one without headers") {
                val headers = Headers().put("traceparent", "value")

                NatsTracing.headerGetter.keys(headers).toList() shouldBe listOf("traceparent")
                NatsTracing.headerGetter.get(headers, "traceparent") shouldBe "value"
                NatsTracing.headerGetter.get(null, "traceparent") shouldBe null
            }

            should("leave the headers empty when nothing is being traced") {
                val headers = Headers()

                NatsTracing.inject(headers)

                headers.isEmpty shouldBe true
            }
        }
    })

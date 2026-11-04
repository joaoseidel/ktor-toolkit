package com.github.joaoseidel.ktor.toolkit.nats.api

import io.nats.client.impl.Headers
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

private const val INSTRUMENTATION_SCOPE: String = "com.github.joaoseidel.ktor.toolkit.nats"

/** Carries a trace across a message, so the consumer's work continues the publisher's trace instead of starting its own. */
internal object NatsTracing {
    internal val headerGetter =
        object : TextMapGetter<Headers> {
            override fun keys(carrier: Headers): Iterable<String> = carrier.keySet()

            override fun get(
                carrier: Headers?,
                key: String,
            ): String? = carrier?.getFirst(key)
        }

    fun inject(headers: Headers) {
        GlobalOpenTelemetry.getPropagators().textMapPropagator.inject(Context.current(), headers) { _, key, value ->
            headers.put(key, value)
        }
    }

    suspend fun <T> consuming(
        name: String,
        headers: Headers?,
        block: suspend () -> T,
    ): T {
        val parent = GlobalOpenTelemetry.getPropagators().textMapPropagator.extract(Context.root(), headers, headerGetter)
        val span =
            GlobalOpenTelemetry
                .getTracer(INSTRUMENTATION_SCOPE)
                .spanBuilder(name)
                .setParent(parent)
                .setSpanKind(SpanKind.CONSUMER)
                .startSpan()

        try {
            return withContext(parent.with(span).asContextElement()) { block() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            span.setStatus(StatusCode.ERROR)
            span.recordException(failure)
            throw failure
        } finally {
            span.end()
        }
    }
}

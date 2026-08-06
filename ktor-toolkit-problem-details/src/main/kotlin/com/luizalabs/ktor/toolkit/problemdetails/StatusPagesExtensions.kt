package com.luizalabs.ktor.toolkit.problemdetails

import com.luizalabs.ktor.toolkit.problemdetails.data.ProblemDetail
import com.luizalabs.ktor.toolkit.problemdetails.exception.HttpStatusException
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlin.reflect.KClass

@DslMarker
@Target(AnnotationTarget.CLASS)
annotation class ProblemDetailsDsl

/**
 * Options for [problemDetails].
 *
 * @property namingStrategy Applied to field names quoted in validation and binding errors, so the
 * response names them the way the client sent them. Match it to the application's own strategy.
 * @property includeExceptionMessage Echoes an unhandled exception's message back to the client.
 * Handy locally; leave it off in production, where the message tends to describe the database.
 * @property json The serializer used for problem bodies.
 */
@ProblemDetailsDsl
class ProblemDetailsConfig internal constructor() {
    var namingStrategy: JsonNamingStrategy? = null
    var includeExceptionMessage: Boolean = false
    var json: Json = ProblemJson

    @PublishedApi
    internal val mappings: MutableList<ExceptionMapping<*>> = mutableListOf()

    /**
     * Turns an application's own exception into a problem, rather than letting it fall through to
     * the catch-all as a 500.
     *
     * ```kotlin
     * problemDetails {
     *     on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound, it.message) }
     * }
     * ```
     *
     * A mapping covers its exception type and every subtype of it, and the closest one to the
     * exception thrown wins — so this never has to out-run the catch-all, and mappings can be
     * declared in any order.
     *
     * The problem's `instance` is filled in with the request path unless [toProblem] set one.
     *
     * @param E The exception type to map.
     * @param toProblem Builds the problem to respond with, on the call that failed.
     */
    inline fun <reified E : Throwable> on(noinline toProblem: suspend ApplicationCall.(E) -> ProblemDetail) {
        mappings += ExceptionMapping(E::class, toProblem)
    }
}

/**
 * One `on<E>` declaration, held until [problemDetails] registers it with the plugin.
 *
 * @property type The exception class to catch.
 * @property toProblem Builds the problem to respond with.
 */
@PublishedApi
internal class ExceptionMapping<E : Throwable>(
    val type: KClass<E>,
    val toProblem: suspend ApplicationCall.(E) -> ProblemDetail,
)

/**
 * Registers every handler in [ResponseHandlers], so the application answers with
 * `application/problem+json` for validation failures, malformed bodies, explicitly thrown
 * [HttpStatusException]s and anything otherwise unhandled.
 *
 * ```kotlin
 * install(StatusPages) {
 *     problemDetails {
 *         namingStrategy = JsonNamingStrategy.SnakeCase
 *
 *         on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound, it.message) }
 *     }
 * }
 * ```
 *
 * Register your own `exception<T>` handlers after this call to override any of them.
 */
fun StatusPagesConfig.problemDetails(configure: ProblemDetailsConfig.() -> Unit = {}) {
    val config = ProblemDetailsConfig().apply(configure)

    config.mappings.forEach { mapping -> register(mapping, config.json) }

    exception<HttpStatusException> { call, cause ->
        ResponseHandlers.handleHttpStatusException(call, cause, config.json)
    }

    exception<RequestValidationException> { call, cause ->
        ResponseHandlers.handleValidationException(call, cause, config.namingStrategy, config.json)
    }

    exception<BadRequestException> { call, cause ->
        ResponseHandlers.handleBadRequestException(call, cause, config.namingStrategy, config.json)
    }

    exception<Throwable> { call, cause ->
        ResponseHandlers.handleGenericException(call, cause, config.includeExceptionMessage, config.json)
    }
}

/** Wires one mapping into the plugin, recovering the exception type the reified `on` captured. */
private fun <E : Throwable> StatusPagesConfig.register(
    mapping: ExceptionMapping<E>,
    json: Json,
) {
    exception(mapping.type) { call, cause ->
        call.respondProblem(mapping.toProblem(call, cause).orInstance(call.problemInstance), json)
    }
}

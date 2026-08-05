package com.luizalabs.ktor.toolkit.mediator

import com.luizalabs.ktor.toolkit.mediator.exception.HttpStatusException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Options for [problemDetails].
 *
 * @property namingStrategy Applied to field names quoted in validation and binding errors, so the
 * response names them the way the client sent them. Match it to the application's own strategy.
 * @property includeExceptionMessage Echoes an unhandled exception's message back to the client.
 * Handy locally; leave it off in production, where the message tends to describe the database.
 * @property json The serializer used for problem bodies.
 */
class ProblemDetailsConfig {
    var namingStrategy: JsonNamingStrategy? = null
    var includeExceptionMessage: Boolean = false
    var json: Json = ProblemJson
}

/**
 * Registers every handler in [ResponseHandlers], so the application answers with
 * `application/problem+json` for validation failures, malformed bodies, explicitly thrown
 * [HttpStatusException]s and anything otherwise unhandled.
 *
 * ```kotlin
 * install(StatusPages) {
 *     problemDetails {
 *         namingStrategy = JsonNamingStrategy.SnakeCase
 *     }
 * }
 * ```
 *
 * Register your own `exception<T>` handlers after this call to override any of them.
 */
fun StatusPagesConfig.problemDetails(configure: ProblemDetailsConfig.() -> Unit = {}) {
    val config = ProblemDetailsConfig().apply(configure)

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

package com.luizalabs.ktor.toolkit.mediator

import com.luizalabs.ktor.toolkit.mediator.data.ProblemDetail
import com.luizalabs.ktor.toolkit.mediator.exception.HttpStatusException
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Collection of response handler functions that can be used with the Ktor StatusPages plugin.
 *
 * These handlers mediate between exceptions and the client, transforming various error conditions
 * into consistent `application/problem+json` responses.
 *
 * Registering all four at once is what [problemDetails] does; call them individually only when a
 * route needs different treatment.
 */
object ResponseHandlers {
    @OptIn(ExperimentalSerializationApi::class)
    private fun String.applyNamingStrategy(strategy: JsonNamingStrategy?): String {
        strategy ?: return this
        val descriptor =
            buildClassSerialDescriptor("_") {
                element(this@applyNamingStrategy, String.serializer().descriptor)
            }
        return strategy.serialNameForJson(descriptor, 0, this)
    }

    /**
     * Handles an [HttpStatusException] by transforming it into a [ProblemDetail] response.
     *
     * The response carries the exception's status code, its detail message, and any additional
     * properties it was given.
     *
     * @param call The [ApplicationCall] instance representing the current HTTP request/response context.
     * @param cause The [HttpStatusException] containing the status, detail message and optional properties.
     * @param json The serializer used for the problem body.
     */
    suspend fun handleHttpStatusException(
        call: ApplicationCall,
        cause: HttpStatusException,
        json: Json = ProblemJson,
    ) {
        call.respondProblem(
            ProblemDetail.fromStatus(
                status = cause.status,
                detail = cause.detail,
                properties = cause.properties,
                instance = call.problemInstance,
            ),
            json,
        )
    }

    /**
     * Turns the validator's failures into a `400` problem whose `properties` map each offending
     * JSON path to a human-readable message.
     *
     * @param call The [ApplicationCall] representing the current HTTP call.
     * @param cause The [RequestValidationException] containing the validation failure details.
     * @param namingStrategy The [JsonNamingStrategy] used to transform field names in the response
     *        properties, so they match the names the client actually sent.
     * @param json The serializer used for the problem body.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun handleValidationException(
        call: ApplicationCall,
        cause: RequestValidationException,
        namingStrategy: JsonNamingStrategy? = null,
        json: Json = ProblemJson,
    ) {
        val properties =
            cause.reasons.associate {
                val regex = Regex("`(.*?)`\\s(.*)")
                val match = regex.find(it)

                val pathGroup =
                    match
                        ?.groupValues
                        ?.get(1)
                        ?.split(".")
                        .orEmpty()

                val realPath =
                    pathGroup
                        .dropLast(1)
                        .joinToString(".") { segment -> segment.applyNamingStrategy(namingStrategy) }
                        .ifEmpty { "root" }
                val field = pathGroup.lastOrNull()?.applyNamingStrategy(namingStrategy).orEmpty()

                val path = "$" + if (realPath != "root") ".$realPath" else ""
                val message = match?.groupValues?.get(2).orEmpty()

                "$path.$field" to "Property `$field` at `$.$realPath` $message"
            }

        call.respondProblem(
            ProblemDetail.fromStatus(
                status = BadRequest,
                detail = "Validation failed",
                properties = properties,
                instance = call.problemInstance,
            ),
            json,
        )
    }

    /**
     * Handles a [BadRequestException], which Ktor raises when a request body cannot be bound.
     *
     * When the underlying cause is a [MissingFieldException] the response names the missing fields
     * individually; otherwise it falls back to the exception's own message, which for a binding
     * failure describes the request rather than the server.
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     * @param cause The [BadRequestException] to be handled.
     * @param namingStrategy The [JsonNamingStrategy] used to transform field names in the response properties.
     * @param json The serializer used for the problem body.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun handleBadRequestException(
        call: ApplicationCall,
        cause: BadRequestException,
        namingStrategy: JsonNamingStrategy? = null,
        json: Json = ProblemJson,
    ) {
        // Walk the chain rather than assume a depth: how deeply Ktor wraps the serialization
        // failure depends on the content negotiation path the request took.
        val missingFields =
            generateSequence(cause as Throwable) { it.cause }
                .filterIsInstance<MissingFieldException>()
                .firstOrNull()

        val problem =
            if (missingFields != null) {
                ProblemDetail.fromStatus(
                    status = BadRequest,
                    detail = "Missing required fields",
                    properties = missingFieldProperties(missingFields.message, namingStrategy),
                    instance = call.problemInstance,
                )
            } else {
                ProblemDetail.fromStatus(
                    status = BadRequest,
                    detail = cause.message,
                    instance = call.problemInstance,
                )
            }

        call.respondProblem(problem, json)
    }

    /**
     * Extracts the offending field names out of a [MissingFieldException] message, which
     * kotlinx.serialization phrases either as a single `Field 'x' is required` or as a plural
     * `Fields [x, y] are required`.
     */
    private fun missingFieldProperties(
        message: String?,
        namingStrategy: JsonNamingStrategy?,
    ): Map<String, String> {
        val text = message.orEmpty()
        val single = Regex("Field '([^']+)' is required .*? at path: (\\$\\.?\\S*)").find(text)
        val plural = Regex("Fields \\[([^]]+)] are required .*? at path: (\\$\\.?\\S*)").find(text)

        val (rawFields, path) =
            when {
                single != null -> listOf(single.groupValues[1]) to single.groupValues[2]
                plural != null -> plural.groupValues[1].split(", ") to plural.groupValues[2]
                else -> return emptyMap()
            }

        val readablePath = if (path == "$") "$.root" else path

        return rawFields.associate { rawField ->
            val field = rawField.applyNamingStrategy(namingStrategy)
            "$path.$field" to "Property `$field` at `$readablePath` is required"
        }
    }

    /**
     * Handles a generic exception that occurs during application execution by logging the error
     * and sending a structured HTTP error response.
     *
     * The exception message is deliberately kept out of the response: it routinely carries driver
     * internals, SQL fragments or filesystem paths. The full stack trace goes to the application log.
     *
     * @param call The current [ApplicationCall] instance representing the HTTP request/response cycle.
     * @param cause The [Throwable] instance representing the exception that was thrown.
     * @param includeExceptionMessage Echoes the exception message back to the client. Useful while
     *        developing locally; leave it off anywhere the caller is not trusted.
     * @param json The serializer used for the problem body.
     */
    suspend fun handleGenericException(
        call: ApplicationCall,
        cause: Throwable,
        includeExceptionMessage: Boolean = false,
        json: Json = ProblemJson,
    ) {
        call.application.environment.log
            .error("Unhandled exception", cause)

        val detail =
            if (includeExceptionMessage) {
                "An unexpected error occurred: ${cause.message}"
            } else {
                "An unexpected error occurred."
            }

        call.respondProblem(
            ProblemDetail.fromStatus(
                status = InternalServerError,
                detail = detail,
                instance = call.problemInstance,
            ),
            json,
        )
    }
}

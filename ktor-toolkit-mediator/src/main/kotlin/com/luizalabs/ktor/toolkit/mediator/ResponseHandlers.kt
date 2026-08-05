package com.luizalabs.ktor.toolkit.mediator

import com.luizalabs.ktor.toolkit.mediator.data.ProblemDetail
import com.luizalabs.ktor.toolkit.mediator.exception.HttpStatusException
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.response.respond
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Collection of response handler functions that can be used with the Ktor StatusPages plugin.
 *
 * These handlers mediate between exceptions and the client, transforming
 * various error conditions into standardized, consistent HTTP responses.
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
     * Handles an [com.luizalabs.ktor.toolkit.mediator.exception.HttpStatusException] by transforming it into a standardized HTTP response
     * using the [com.luizalabs.ktor.toolkit.mediator.data.ProblemDetail] format.
     *
     * This method generates a structured error response that includes the HTTP status code,
     * a human-readable detail message, and any additional properties provided by the exception.
     *
     * @param call The [ApplicationCall] instance representing the current HTTP request/response context.
     * @param cause The [com.luizalabs.ktor.toolkit.mediator.exception.HttpStatusException] containing the status, detail message, and optional properties
     *        to include in the response.
     */
    suspend fun handleHttpStatusException(
        call: ApplicationCall,
        cause: HttpStatusException,
    ) {
        call.respond(
            status = cause.status,
            message = ProblemDetail.fromStatus(cause.status, cause.detail, cause.properties),
        )
    }

    /**
     * Handles a request validation exception by processing validation error messages and responding
     * with a structured error response containing detailed information about the validation failure.
     *
     * @param call The ApplicationCall instance representing the current HTTP call.
     * @param cause The RequestValidationException containing the validation failure details.
     * @param namingStrategy The [JsonNamingStrategy] used to transform field names in the response properties.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun handleValidationException(
        call: ApplicationCall,
        cause: RequestValidationException,
        namingStrategy: JsonNamingStrategy? = null,
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

        call.respond(
            status = BadRequest,
            message =
                ProblemDetail.fromStatus(
                    status = BadRequest,
                    detail = "Validation failed",
                    properties = properties,
                ),
        )
    }

    /**
     * Handles exceptions of type [BadRequestException] in an HTTP context. This function processes
     * the exception to generate structured error responses with appropriate HTTP status codes and
     * error details. It specifically handles cases where the cause of the exception is a
     * [MissingFieldException], providing detailed information about the missing fields, and responds
     * with a [ProblemDetail] object.
     *
     * @param call The [ApplicationCall] representing the HTTP request.
     * @param cause The [BadRequestException] to be handled.
     * @param namingStrategy The [JsonNamingStrategy] used to transform field names in the response properties.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun handleBadRequestException(
        call: ApplicationCall,
        cause: BadRequestException,
        namingStrategy: JsonNamingStrategy? = null,
    ) {
        if (cause.cause?.cause is MissingFieldException) {
            val missingFields: (String?) -> Map<String, String> = { message ->
                val regexField = Regex("Field '([^']+)' is required .*? at path: (\\$\\.?\\S*)")
                val regexFields = Regex("Fields \\[([^]]+)] are required .*? at path: (\\$\\.?\\S*)")

                val matchField = regexField.find(message ?: "")
                if (matchField != null) {
                    val path = matchField.groupValues[2]
                    val realPath = path.let { if (it == "$") "$.root" else it }
                    val field = matchField.groupValues[1].applyNamingStrategy(namingStrategy)

                    mapOf("$path.$field" to "Property `$field` at `$realPath` is required")
                } else {
                    val matchFields = regexFields.find(message ?: "")
                    if (matchFields != null) {
                        val path = matchFields.groupValues[2]
                        val realPath = path.let { if (it == "$") "$.root" else it }
                        val fields = matchFields.groupValues[1].split(", ")

                        fields.associate { rawField ->
                            val field = rawField.applyNamingStrategy(namingStrategy)
                            "$path.$field" to "Property `$field` at `$realPath` is required"
                        }
                    } else {
                        emptyMap()
                    }
                }
            }

            call.respond(
                status = BadRequest,
                message =
                    ProblemDetail.fromStatus(
                        status = BadRequest,
                        detail = "Missing required fields",
                        properties = missingFields(cause.cause?.cause?.message),
                    ),
            )
        } else {
            // Generic BadRequestException
            call.respond(
                status = BadRequest,
                message =
                    ProblemDetail.fromStatus(
                        status = BadRequest,
                        detail = cause.message,
                    ),
            )
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
     */
    suspend fun handleGenericException(
        call: ApplicationCall,
        cause: Throwable,
        includeExceptionMessage: Boolean = false,
    ) {
        call.application.environment.log
            .error("Unhandled exception", cause)

        val detail =
            if (includeExceptionMessage) {
                "An unexpected error occurred: ${cause.message}"
            } else {
                "An unexpected error occurred."
            }

        call.respond(
            status = InternalServerError,
            message = ProblemDetail.fromStatus(status = InternalServerError, detail = detail),
        )
    }
}

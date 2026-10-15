package com.github.joaoseidel.ktor.toolkit.problemdetails

import com.github.joaoseidel.ktor.toolkit.problemdetails.ResponseHandlers.PROPERTY_REASON
import com.github.joaoseidel.ktor.toolkit.problemdetails.ResponseHandlers.ROOT_POINTER
import com.github.joaoseidel.ktor.toolkit.problemdetails.data.ProblemDetail
import com.github.joaoseidel.ktor.toolkit.problemdetails.exception.HttpStatusException
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
import kotlinx.serialization.json.JsonDecodingException
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

    private const val ROOT_POINTER = "$"

    private val PROPERTY_REASON = Regex("`(.*?)`\\s(.*)")

    /**
     * The fields a [MissingFieldException] names are a property of the exception, but the path is
     * something `Json` appends to the message on the way out and nowhere else.
     */
    private val MISSING_FIELD_PATH = Regex(" at path: (\\$\\.?\\S*)")

    private class ParsedReason(
        val key: String,
        private val prefix: String?,
        val message: String,
    ) {
        fun render(messages: List<String>): String {
            val text = messages.joinToString("; ")
            return if (prefix == null) text else "$prefix $text"
        }
    }

    private fun parseReason(
        reason: String,
        namingStrategy: JsonNamingStrategy?,
    ): ParsedReason {
        val match = PROPERTY_REASON.find(reason) ?: return ParsedReason(ROOT_POINTER, prefix = null, message = reason)

        val segments = match.groupValues[1].split(".")
        val field = segments.last().applyNamingStrategy(namingStrategy)
        val container = segments.dropLast(1).joinToString(".") { segment -> segment.applyNamingStrategy(namingStrategy) }

        return ParsedReason(
            key = if (container.isEmpty()) "$ROOT_POINTER.$field" else "$ROOT_POINTER.$container.$field",
            prefix = "Property `$field` at `$ROOT_POINTER.${container.ifEmpty { "root" }}`",
            message = match.groupValues[2],
        )
    }

    /**
     * Handles an [HttpStatusException] by transforming it into a [ProblemDetail] response.
     *
     * The response carries the exception's status code, its detail message, and any additional
     * properties it was given.
     *
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
     * A property that breaks several rules at once reports all of them under its one key, and an
     * object-level failure — what `invariant` records — is reported against [ROOT_POINTER], since
     * it names no property to key on.
     *
     * @param namingStrategy Applied to the field names quoted in the response, so they match the
     *        names the client actually sent. Match it to the application's own strategy.
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
            cause.reasons
                .map { reason -> parseReason(reason, namingStrategy) }
                .groupBy(ParsedReason::key)
                .mapValues { (_, reasons) -> reasons.first().render(reasons.map(ParsedReason::message)) }

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
     * A [MissingFieldException] underneath names every missing field at the path it sits on; a
     * [JsonDecodingException] — malformed JSON, or a value of the wrong shape — names the path that
     * failed and what was expected there, without the excerpt of the input the exception carries.
     * Anything else falls back to the exception's own message, which for a binding failure describes
     * the request rather than the server.
     *
     * @param namingStrategy Applied to the field names quoted in the response, so they match the
     *        names the client actually sent.
     * @param json The serializer used for the problem body.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun handleBadRequestException(
        call: ApplicationCall,
        cause: BadRequestException,
        namingStrategy: JsonNamingStrategy? = null,
        json: Json = ProblemJson,
    ) {
        val chain = generateSequence(cause as Throwable) { it.cause }
        val missingFields = chain.filterIsInstance<MissingFieldException>().firstOrNull()
        val decoding = chain.filterIsInstance<JsonDecodingException>().firstOrNull()

        val problem =
            when {
                missingFields != null ->
                    ProblemDetail.fromStatus(
                        status = BadRequest,
                        detail = "Missing required fields",
                        properties = missingFieldProperties(missingFields, namingStrategy),
                        instance = call.problemInstance,
                    )
                decoding != null ->
                    ProblemDetail.fromStatus(
                        status = BadRequest,
                        detail = "Malformed request body",
                        properties = mapOf((decoding.path ?: ROOT_POINTER) to decoding.shortMessage),
                        instance = call.problemInstance,
                    )
                else ->
                    ProblemDetail.fromStatus(
                        status = BadRequest,
                        detail = cause.message,
                        instance = call.problemInstance,
                    )
            }

        call.respondProblem(problem, json)
    }

    private fun missingFieldProperties(
        failure: MissingFieldException,
        namingStrategy: JsonNamingStrategy?,
    ): Map<String, String> {
        val match = MISSING_FIELD_PATH.find(failure.message.toString())
        val path = if (match == null) ROOT_POINTER else match.groupValues[1]
        val readablePath = if (path == ROOT_POINTER) "$.root" else path

        return failure.missingFields.associate { rawField ->
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

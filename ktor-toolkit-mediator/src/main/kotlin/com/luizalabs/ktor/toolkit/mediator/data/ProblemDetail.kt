package com.luizalabs.ktor.toolkit.mediator.data

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * Represents a structured format for providing problem details in HTTP responses.
 * This implementation is based on the Problem Details RFC 7807 specification and helps
 * in standardizing error responses in web applications.
 *
 * @property title A short, human-readable summary of the problem.
 * @property status The HTTP status code associated with this problem.
 * @property detail A detailed, human-readable explanation specific to this problem. Optional.
 * @property properties Additional key-value pairs offering more context about the problem. Optional.
 */
@ConsistentCopyVisibility
@Serializable
data class ProblemDetail private constructor(
    val title: String,
    val status: Int,
    val detail: String? = null,
    val properties: Map<String, String>? = null,
) {
    companion object {
        /**
         * Creates a [ProblemDetail] instance using the provided HTTP status, an optional detailed
         * message, and optional additional properties.
         *
         * @param status The HTTP status code represented as an [io.ktor.http.HttpStatusCode].
         * @param detail An optional, detailed, human-readable explanation of the problem.
         * @param properties An optional map of additional properties providing context about the problem.
         * @return A [ProblemDetail] instance representing the specified status, detail, and properties.
         */
        fun fromStatus(
            status: HttpStatusCode,
            detail: String? = null,
            properties: Map<String, String>? = null,
        ): ProblemDetail =
            ProblemDetail(
                title = status.description,
                status = status.value,
                detail = detail,
                properties = properties,
            )
    }
}

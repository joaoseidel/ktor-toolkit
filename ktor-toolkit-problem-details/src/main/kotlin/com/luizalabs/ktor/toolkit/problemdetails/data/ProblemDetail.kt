package com.luizalabs.ktor.toolkit.problemdetails.data

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

/**
 * An RFC 9457 (formerly RFC 7807) *problem detail*: the standard shape for an HTTP error body.
 *
 * Serve it as `application/problem+json` — [com.luizalabs.ktor.toolkit.problemdetails.respondProblem]
 * does that for you.
 *
 * @property type A URI identifying the problem kind. Defaults to [BLANK_TYPE], which the RFC
 * defines as "no further information beyond the status code".
 * @property title A short, human-readable summary of the problem.
 * @property status The HTTP status code associated with this problem.
 * @property detail A human-readable explanation specific to this occurrence of the problem.
 * @property instance A URI identifying this specific occurrence — typically the request path.
 * @property properties Additional key-value pairs offering more context, such as per-field
 * validation messages.
 */
@ConsistentCopyVisibility
@Serializable
data class ProblemDetail private constructor(
    val type: String = BLANK_TYPE,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val properties: Map<String, String>? = null,
) {
    /**
     * Fills in [instance] with [path] unless this problem already names one.
     *
     * A hand-written mapping rarely bothers with `instance`, and the request path is what it would
     * have said. One that does name an instance keeps it.
     */
    internal fun orInstance(path: String): ProblemDetail = if (instance != null) this else copy(instance = path)

    companion object {
        /** The RFC's default `type`, meaning the status code is the whole story. */
        const val BLANK_TYPE: String = "about:blank"

        /**
         * Creates a [ProblemDetail] from an HTTP status, using the status description as the title.
         *
         * @param status The HTTP status code.
         * @param detail A human-readable explanation of this occurrence.
         * @param properties Additional context about the problem.
         * @param type A URI identifying the problem kind. Defaults to [BLANK_TYPE].
         * @param instance A URI identifying this occurrence, typically the request path.
         */
        fun fromStatus(
            status: HttpStatusCode,
            detail: String? = null,
            properties: Map<String, String>? = null,
            type: String = BLANK_TYPE,
            instance: String? = null,
        ): ProblemDetail =
            ProblemDetail(
                type = type,
                title = status.description,
                status = status.value,
                detail = detail,
                instance = instance,
                properties = properties,
            )
    }
}

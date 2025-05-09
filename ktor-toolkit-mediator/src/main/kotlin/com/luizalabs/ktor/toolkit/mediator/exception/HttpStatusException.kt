package com.luizalabs.ktor.toolkit.mediator.exception

import io.ktor.http.HttpStatusCode

/**
 * Represents an HTTP status exception commonly used to convey structured error information
 * in server applications. This exception is designed to carry an HTTP status code, an optional
 * detailed message describing the error, and additional properties providing context about the
 * error.
 *
 * @property status The HTTP status code associated with the error.
 * @property detail An optional detailed description of the error.
 * @property properties A map of additional properties providing context or metadata about the error.
 */
class HttpStatusException(
    val status: HttpStatusCode,
    val detail: String? = null,
    val properties: Map<String, String>? = null,
) : RuntimeException(detail)

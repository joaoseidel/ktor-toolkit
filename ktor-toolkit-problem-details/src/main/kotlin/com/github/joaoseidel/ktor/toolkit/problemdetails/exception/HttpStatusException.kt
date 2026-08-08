package com.github.joaoseidel.ktor.toolkit.problemdetails.exception

import io.ktor.http.HttpStatusCode

/**
 * An exception that already knows which HTTP status it should become.
 *
 * `problemDetails { }` maps it straight to a problem body, so throwing one from anywhere under a
 * route is how a handler reports a failure without responding itself:
 *
 * ```kotlin
 * throw HttpStatusException(HttpStatusCode.Conflict, "That ISBN is already catalogued")
 * ```
 *
 * Reach for it in an adapter, where the status is genuinely the right vocabulary. A domain type
 * should throw its own exception — `BookNotFoundException` — and let `on<BookNotFoundException>`
 * choose the status once, in the web layer; a domain that picks status codes has become one.
 *
 * @property status The status the response should carry.
 * @property detail What went wrong, for a human reading the response. Also the exception message.
 * @property properties Extra fields to publish alongside `detail` in the problem body.
 */
class HttpStatusException(
    val status: HttpStatusCode,
    val detail: String? = null,
    val properties: Map<String, String>? = null,
) : RuntimeException(detail)

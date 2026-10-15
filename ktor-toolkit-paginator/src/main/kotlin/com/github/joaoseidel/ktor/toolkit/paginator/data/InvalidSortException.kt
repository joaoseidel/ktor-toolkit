package com.github.joaoseidel.ktor.toolkit.paginator.data

/**
 * A `?sortBy=` key outside the endpoint's allow-list.
 *
 * It is always the client's mistake, so it should answer 400. Map this type rather than every [IllegalArgumentException]: a `require` that fails
 * inside the service is a bug, and it should stay a 500. It extends [IllegalArgumentException] so a handler written for the older behaviour still
 * catches it.
 *
 * @property property The key the client sent.
 */
class InvalidSortException(
    message: String,
    val property: String,
) : IllegalArgumentException(message)

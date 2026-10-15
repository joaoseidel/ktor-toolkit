package com.github.joaoseidel.ktor.toolkit.filter.data

/**
 * A `?filterBy=` condition that cannot be applied: malformed, naming a property outside the allow-list, or carrying a
 * value of the wrong type.
 *
 * It is always the client's mistake, so it should answer 400. It extends [IllegalArgumentException] so the mapping the
 * toolkit already recommends for an unknown sort key covers it too; map it on its own to publish [property].
 *
 * @property property The property the condition named, when parsing got that far.
 */
class InvalidFilterException(
    message: String,
    val property: String? = null,
) : IllegalArgumentException(message)

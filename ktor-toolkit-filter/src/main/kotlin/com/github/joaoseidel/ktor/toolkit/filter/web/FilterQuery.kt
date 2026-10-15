package com.github.joaoseidel.ktor.toolkit.filter.web

import com.github.joaoseidel.ktor.toolkit.filter.data.Filter
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_IN
import com.github.joaoseidel.ktor.toolkit.filter.data.InvalidFilterException
import io.ktor.http.Parameters

/**
 * Reads `?filterBy=` into the [Filter]s a listing must satisfy, all of them at once.
 *
 * ```
 * ?filterBy=price:gte:1000,title:like:kotlin
 * ?filterBy=status:in:draft|published
 * ?filterBy=title:eq:Hello\, world
 * ?filterBy=price:gte:1000&filterBy=price:lte:5000
 * ```
 *
 * Each condition is `property:operator:value`, where the value is everything after the second `:`. Conditions are
 * separated by `,` or by repeating the parameter, `in` and `notIn` separate their values with `|`, and a `\` makes the
 * next character literal, so `\,`, `\|` and `\\` stand for themselves.
 *
 * Unlike paging, a malformed condition fails the request: a filter is data, and dropping one would quietly answer
 * with rows the client asked to exclude.
 */
object FilterQuery {
    /** The query parameter conditions are read from. */
    const val PARAMETER: String = "filterBy"

    /** Upper bound on the conditions one request may carry, so a request cannot ask for an arbitrarily complex query. */
    const val DEFAULT_MAX_FILTERS: Int = 10

    /**
     * Parses every `filterBy` parameter of [queryParameters]. An absent or blank parameter yields no filters.
     *
     * @param maxFilters The most conditions this endpoint accepts.
     * @throws InvalidFilterException if a condition is malformed, or there are more than [maxFilters].
     */
    fun parse(
        queryParameters: Parameters,
        maxFilters: Int = DEFAULT_MAX_FILTERS,
    ): List<Filter> {
        val conditions =
            queryParameters
                .getAll(PARAMETER)
                .orEmpty()
                .flatMap { it.splitKeepingEscapes(',') }
                .map(String::trim)
                .filter(String::isNotEmpty)

        if (conditions.size > maxFilters) {
            throw InvalidFilterException("At most $maxFilters filters are accepted, got ${conditions.size}.")
        }

        return conditions.map(::parseCondition)
    }

    private fun parseCondition(condition: String): Filter {
        val parts = condition.split(':', limit = 3)
        if (parts.size < 3) {
            throw InvalidFilterException("\"$condition\" is not a property:operator:value condition.")
        }

        val (property, token, value) = parts
        val operator =
            Filter.Operator.fromToken(token)
                ?: throw InvalidFilterException(
                    "\"$token\" is not a filter operator. Use one of: ${Filter.Operator.entries.joinToString { it.token }}.",
                    property,
                )
        val values = if (operator == IN || operator == NOT_IN) value.splitKeepingEscapes('|') else listOf(value)

        return Filter(property, operator, values.map { it.unescape() })
    }

    private fun String.splitKeepingEscapes(separator: Char): List<String> =
        buildList {
            val piece = StringBuilder()
            var escaping = false

            for (char in this@splitKeepingEscapes) {
                when {
                    escaping -> piece.append('\\').append(char).also { escaping = false }
                    char == '\\' -> escaping = true
                    char == separator -> add(piece.toString()).also { piece.clear() }
                    else -> piece.append(char)
                }
            }

            if (escaping) piece.append('\\')
            add(piece.toString())
        }

    private val ESCAPED = Regex("""\\(.)""")

    private fun String.unescape(): String = ESCAPED.replace(this, "$1")
}

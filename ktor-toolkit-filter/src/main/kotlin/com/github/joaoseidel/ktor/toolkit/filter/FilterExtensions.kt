package com.github.joaoseidel.ktor.toolkit.filter

import com.github.joaoseidel.ktor.toolkit.filter.data.Filter
import com.github.joaoseidel.ktor.toolkit.filter.data.InvalidFilterException
import com.github.joaoseidel.ktor.toolkit.filter.web.FilterQuery
import io.ktor.server.application.ApplicationCall

/**
 * Parses the `?filterBy=` conditions of the current call. See [FilterQuery] for the syntax.
 *
 * Unlike `call.pagination`, this fails the request on a malformed condition, by throwing [InvalidFilterException].
 * Use [filters] with an argument when the endpoint accepts more or fewer than
 * [FilterQuery.DEFAULT_MAX_FILTERS] conditions.
 */
val ApplicationCall.filters: List<Filter>
    get() = filters()

/**
 * Parses the `?filterBy=` conditions of the current call, accepting at most [maxFilters] of them.
 *
 * @throws InvalidFilterException if a condition is malformed, or there are more than [maxFilters].
 */
fun ApplicationCall.filters(maxFilters: Int = FilterQuery.DEFAULT_MAX_FILTERS): List<Filter> = FilterQuery.parse(request.queryParameters, maxFilters)

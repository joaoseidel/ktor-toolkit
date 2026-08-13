package com.github.joaoseidel.ktor.toolkit.paginator.data

/**
 * The pagination and sorting a query should apply.
 *
 * This is what a route hands to a repository, obtained from
 * [com.github.joaoseidel.ktor.toolkit.paginator.toPagination].
 *
 * @property page The requested page index and page size.
 * @property sortBy Sorting criteria, in order of precedence.
 */
data class Pagination(
    val page: Page,
    val sortBy: List<Sort> = emptyList(),
)

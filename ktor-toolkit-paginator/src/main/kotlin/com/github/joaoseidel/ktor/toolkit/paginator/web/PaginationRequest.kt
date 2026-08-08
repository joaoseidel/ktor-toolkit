package com.github.joaoseidel.ktor.toolkit.paginator.web

import com.github.joaoseidel.ktor.toolkit.paginator.data.Page
import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

/**
 * What a client asked for in `?page=`, `?pageSize=` and `?sortBy=`, already clamped.
 *
 * The web-side counterpart of [com.github.joaoseidel.ktor.toolkit.paginator.data.Pagination]: build
 * one with [from] and convert it with `toPagination()` before handing it to a repository.
 *
 * @property page The page index and size to read, within this endpoint's bounds.
 * @property sortBy Sorting criteria, in order of precedence, in the order the client listed them.
 */
@Serializable
data class PaginationRequest(
    val page: Page = Page(DEFAULT_PAGE, DEFAULT_PAGE_SIZE),
    val sortBy: List<Sort> = emptyList(),
) {
    companion object {
        /** Page index used when the request does not carry one. */
        const val DEFAULT_PAGE: Int = 0

        /** Page size used when the request does not carry one. */
        const val DEFAULT_PAGE_SIZE: Int = 10

        /** Upper bound applied to a client-supplied page size, so a request cannot ask for the whole table. */
        const val DEFAULT_MAX_PAGE_SIZE: Int = 100

        /**
         * Creates a [PaginationRequest] instance based on the given pagination and sorting parameters.
         *
         * Values are clamped rather than rejected: a negative page becomes 0, and the page size is
         * coerced into `1..maxPageSize`.
         *
         * @param page The page index to fetch, starting from 0.
         * @param pageSize The number of elements to retrieve per page.
         * @param sortBy A list of sorting instructions where each string defines the property to sort by
         *                 and optionally includes a prefix (`-`) to specify descending order.
         * @param maxPageSize The largest page size this endpoint is willing to serve.
         */
        fun from(
            page: Int,
            pageSize: Int,
            sortBy: List<String>,
            maxPageSize: Int = DEFAULT_MAX_PAGE_SIZE,
        ): PaginationRequest =
            PaginationRequest(
                page = Page(page.coerceAtLeast(0), pageSize.coerceIn(1, maxPageSize)),
                sortBy = sortBy.mapNotNull { token -> token.trim().takeIf(String::isNotEmpty)?.let(Sort::fromString) },
            )

        /**
         * Creates a [PaginationRequest] instance based on query parameters for pagination and sorting.
         *
         * Malformed input never fails the request — an unparseable `page` or `pageSize` falls back to
         * its default, and out-of-range values are clamped. This keeps a hand-typed URL from turning
         * into a 500, and stops `?pageSize=10000000` from being a denial-of-service vector.
         *
         * @param queryParameters A set of query parameters where:
         * - `page` denotes the page index to fetch, starting from 0.
         * - `pageSize` specifies the number of items per page.
         * - `sortBy` contains a comma-separated list of sorting criteria. Each criterion can optionally begin
         *   with a `-` indicating descending order.
         * @param defaultPageSize The page size to use when the request does not specify one.
         * @param maxPageSize The largest page size this endpoint is willing to serve.
         * @return A [PaginationRequest] instance containing the parsed pagination and sorting data.
         */
        fun from(
            queryParameters: Parameters,
            defaultPageSize: Int = DEFAULT_PAGE_SIZE,
            maxPageSize: Int = DEFAULT_MAX_PAGE_SIZE,
        ): PaginationRequest {
            val page = queryParameters["page"]?.toIntOrNull() ?: DEFAULT_PAGE
            val pageSize = queryParameters["pageSize"]?.toIntOrNull() ?: defaultPageSize
            // An absent parameter and an empty one mean the same thing: `from` drops blank tokens.
            val sortBy = queryParameters["sortBy"].orEmpty().split(",")
            return from(page, pageSize, sortBy, maxPageSize)
        }
    }
}

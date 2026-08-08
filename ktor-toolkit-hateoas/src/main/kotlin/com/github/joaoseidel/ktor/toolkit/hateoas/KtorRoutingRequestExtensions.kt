package com.github.joaoseidel.ktor.toolkit.hateoas

import com.github.joaoseidel.ktor.toolkit.hateoas.data.Link
import com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse
import io.ktor.http.ParametersBuilder
import io.ktor.http.formUrlEncode
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingRequest

/** Query parameters owned by the pagination links; any other parameter is carried over verbatim. */
private val PAGINATION_PARAMETERS = setOf("page", "pageSize")

/**
 * Creates the pagination links for a [PagedResponse], preserving every other query parameter of
 * the current [RoutingRequest].
 *
 * `self` is always present. `next`, `prev`, `first` and `last` are emitted only when they point
 * at a page that actually exists.
 *
 * @param request The current routing request.
 * @param pageNumber The current page index, starting from 0.
 * @param pageSize The current page size.
 * @param totalPages The number of pages available, as reported by [PagedResponse.Metadata.totalPages].
 * @return A list of pagination-related [Link] objects.
 */
@PublishedApi
internal fun createPaginationLinks(
    request: RoutingRequest,
    pageNumber: Int,
    pageSize: Int,
    totalPages: Int,
): List<Link> {
    val basePath = request.path()
    val carriedOver =
        ParametersBuilder()
            .apply {
                request.queryParameters
                    .entries()
                    .filter { (key, _) -> key !in PAGINATION_PARAMETERS }
                    .forEach { (key, values) -> appendAll(key, values) }
            }.build()

    // Percent-encodes keys and values, so a filter such as `?q=a&b` survives the round trip.
    fun href(page: Int): String {
        val parameters =
            ParametersBuilder().apply {
                appendAll(carriedOver)
                append("page", page.toString())
                append("pageSize", pageSize.toString())
            }
        return "$basePath?${parameters.build().formUrlEncode()}"
    }

    val lastPage = totalPages - 1

    return buildList {
        add(Link("self", href(pageNumber)))

        if (pageNumber < lastPage) add(Link("next", href(pageNumber + 1)))
        if (pageNumber > 0) add(Link("prev", href(pageNumber - 1)))
        if (pageNumber > 0) add(Link("first", href(0)))
        if (pageNumber < lastPage) add(Link("last", href(lastPage)))
    }
}

/**
 * The pagination links for [response], derived from the current request.
 *
 * `self` is always present; `next`, `prev`, `first` and `last` appear only where the page they
 * point at exists. Every query parameter other than `page` and `pageSize` is carried over, so a
 * filtered listing stays filtered as the client follows `next`.
 */
fun RoutingRequest.createPaginationLinks(response: PagedResponse<*>): List<Link> =
    createPaginationLinks(
        this,
        response.metadata.page,
        response.metadata.pageSize,
        response.metadata.totalPages,
    )

/** The pagination links for [response] — see the [RoutingRequest] overload. */
fun RoutingCall.createPaginationLinks(response: PagedResponse<*>): List<Link> = request.createPaginationLinks(response)

package com.github.joaoseidel.ktor.toolkit.hateoas

import com.github.joaoseidel.ktor.toolkit.hateoas.data.Link
import com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse
import io.ktor.http.ParametersBuilder
import io.ktor.http.formUrlEncode
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingRequest

private val PAGINATION_PARAMETERS = setOf("page", "pageSize")

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

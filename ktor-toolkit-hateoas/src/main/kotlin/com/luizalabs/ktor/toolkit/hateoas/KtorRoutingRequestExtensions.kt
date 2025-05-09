package com.luizalabs.ktor.toolkit.hateoas

import com.luizalabs.ktor.toolkit.hateoas.data.Link
import com.luizalabs.ktor.toolkit.paginator.web.PagedResponse
import io.ktor.server.request.path
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingRequest

/**
 * Creates pagination links using a [RoutingRequest] to maintain query parameters.
 *
 * @param request The current routing request
 * @param pageNumber The current page number
 * @param pageSize The current page size
 * @param totalPages The total number of pages
 * @return A list of pagination-related [com.luizalabs.ktor.toolkit.hateoas.data.Link] objects
 */
@PublishedApi
internal fun createPaginationLinks(
    request: RoutingRequest,
    pageNumber: Int,
    pageSize: Int,
    totalPages: Int,
): List<Link> {
    val links = mutableListOf<Link>()
    val basePath = request.path()

    // Get current query parameters minus pagination ones to preserve them in links
    val currentQueryParams =
        request.queryParameters
            .entries()
            .filter { (key, _) -> key != "page" && key != "pageSize" }
            .joinToString("&") { (key, values) ->
                values.joinToString("&") { value -> "$key=$value" }
            }

    val baseQueryString = if (currentQueryParams.isBlank()) "?" else "?$currentQueryParams&"

    // Calculate navigation availability
    val hasNext = pageNumber < totalPages
    val hasPrevious = pageNumber > 0

    // Build self-link with the current page and size
    links.add(Link("self", "$basePath${baseQueryString}page=$pageNumber&pageSize=$pageSize"))

    // Add next and previous links if they exist
    if (hasNext) {
        links.add(Link("next", "$basePath${baseQueryString}page=${pageNumber + 1}&pageSize=$pageSize"))
    }

    if (hasPrevious) {
        links.add(Link("prev", "$basePath${baseQueryString}page=${pageNumber - 1}&pageSize=$pageSize"))
    }

    // Add first and last links if they exist
    if (pageNumber > 0 && totalPages > 1) {
        links.add(Link("first", "$basePath${baseQueryString}page=0&pageSize=$pageSize"))
    }

    if (pageNumber < totalPages - 1) {
        links.add(Link("last", "$basePath${baseQueryString}page=${totalPages - 1}&pageSize=$pageSize"))
    }

    return links
}

/**
 * Extension function for [RoutingRequest] to create pagination links from a [PagedResponse] directly.
 *
 * @param response The paginated response containing pagination information
 * @return A list of pagination-related Link objects
 */
fun RoutingRequest.createPaginationLinks(response: PagedResponse<*>): List<Link> =
    createPaginationLinks(
        this,
        response.metadata.page,
        response.metadata.pageSize,
        response.metadata.totalPages,
    )

/**
 * Extension function for [RoutingCall] to create pagination links from a [PagedResponse] directly.
 *
 * @param response The paginated response containing pagination information
 * @return A list of pagination-related Link objects
 */
fun RoutingCall.createPaginationLinks(response: PagedResponse<*>): List<Link> = request.createPaginationLinks(response)

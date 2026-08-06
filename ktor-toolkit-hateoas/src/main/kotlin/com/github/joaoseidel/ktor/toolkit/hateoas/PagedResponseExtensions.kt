package com.github.joaoseidel.ktor.toolkit.hateoas

import com.github.joaoseidel.ktor.toolkit.hateoas.data.Link
import com.github.joaoseidel.ktor.toolkit.hateoas.data.Resource
import com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingRequest

/**
 * Wraps a page in a [Resource], carrying the pagination links for the current request.
 *
 * The links preserve every other query parameter, so a filtered listing stays filtered as the
 * client follows `next`.
 *
 * @param request The current routing request, which the links are derived from.
 * @param links Additional links to publish alongside the pagination ones.
 */
fun <T> PagedResponse<T>.toResource(
    request: RoutingRequest,
    links: List<Link> = emptyList(),
): Resource<PagedResponse<T>> = Resource(this, request.createPaginationLinks(this) + links)

/**
 * Wraps a page in a [Resource], carrying the pagination links for the current call.
 *
 * @param call The current routing call, whose request the links are derived from.
 * @param links Additional links to publish alongside the pagination ones.
 */
fun <T> PagedResponse<T>.toResource(
    call: RoutingCall,
    links: List<Link> = emptyList(),
): Resource<PagedResponse<T>> = toResource(call.request, links)

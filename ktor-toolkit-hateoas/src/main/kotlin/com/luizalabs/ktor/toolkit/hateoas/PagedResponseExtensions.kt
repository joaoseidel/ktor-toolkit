package com.luizalabs.ktor.toolkit.hateoas

import com.luizalabs.ktor.toolkit.hateoas.data.Link
import com.luizalabs.ktor.toolkit.hateoas.data.Resource
import com.luizalabs.ktor.toolkit.paginator.web.PagedResponse
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.RoutingRequest

/**
 * Creates pagination links using a [RoutingRequest] to maintain query parameters.
 *
 * @param call The current routing call
 */
fun <T> PagedResponse<T>.toResource(call: RoutingCall): Resource<PagedResponse<T>> =
    Resource(this)
        .withLinks(call.createPaginationLinks(this))

/**
 * Creates pagination links using a [RoutingRequest] to maintain query parameters.
 *
 * @param call The current routing call
 * @param links A list of custom links to be added to the resource
 */
fun <T> PagedResponse<T>.toResource(
    call: RoutingCall,
    links: List<Link>,
): Resource<PagedResponse<T>> =
    Resource(this, links)
        .withLinks(call.createPaginationLinks(this))
        .withLinks(links)

/**
 * Creates pagination links using a [RoutingRequest] to maintain query parameters.
 *
 * @param request The current routing request
 */
fun <T> PagedResponse<T>.toResource(request: RoutingRequest): Resource<PagedResponse<T>> =
    Resource(this)
        .withLinks(request.createPaginationLinks(this))

/**
 * Creates pagination links using a [RoutingRequest] to maintain query parameters.
 *
 * @param request The current routing request
 * @param links A list of custom links to be added to the resource
 */
fun <T> PagedResponse<T>.toResource(
    request: RoutingRequest,
    links: List<Link>,
): Resource<PagedResponse<T>> =
    Resource(this, links)
        .withLinks(request.createPaginationLinks(this))
        .withLinks(links)

package com.github.joaoseidel.ktor.toolkit.paginator

import com.github.joaoseidel.ktor.toolkit.paginator.data.Pagination
import com.github.joaoseidel.ktor.toolkit.paginator.web.PaginationRequest
import io.ktor.server.application.ApplicationCall

/**
 * Converts a [PaginationRequest] into the [Pagination] a repository consumes.
 *
 * @receiver The [PaginationRequest] instance containing the pagination and sorting information.
 * @return A [Pagination] object encapsulating the pagination and sorting configurations.
 */
fun PaginationRequest.toPagination(): Pagination = Pagination(page, sortBy)

/**
 * Parses the `?page=`, `?pageSize=` and `?sortBy=` query parameters of the current call.
 *
 * Malformed values fall back to their defaults and out-of-range ones are clamped, so this never
 * fails the request. Use [paginationRequest] when the endpoint needs different bounds.
 */
val ApplicationCall.pagination: Pagination
    get() = paginationRequest().toPagination()

/**
 * Parses the pagination query parameters of the current call with explicit bounds.
 *
 * @param defaultPageSize The page size to use when the request does not specify one.
 * @param maxPageSize The largest page size this endpoint is willing to serve.
 */
fun ApplicationCall.paginationRequest(
    defaultPageSize: Int = PaginationRequest.DEFAULT_PAGE_SIZE,
    maxPageSize: Int = PaginationRequest.DEFAULT_MAX_PAGE_SIZE,
): PaginationRequest = PaginationRequest.from(request.queryParameters, defaultPageSize, maxPageSize)

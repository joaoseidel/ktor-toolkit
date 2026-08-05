package com.luizalabs.ktor.toolkit.paginator

import com.luizalabs.ktor.toolkit.paginator.data.Pagination
import com.luizalabs.ktor.toolkit.paginator.web.PaginationRequest

/**
 * Converts a [PaginationRequest] instance into a [Pagination].
 *
 * This function facilitates the conversion of a request model containing pagination
 * and sorting details into a [Pagination] object, which is used for processing paginated
 * and sorted data retrieval operations.
 *
 * @receiver The [PaginationRequest] instance containing the pagination and sorting information.
 * @return A [Pagination] object encapsulating the pagination and sorting configurations.
 */
fun PaginationRequest.toPagination(): Pagination = Pagination(page, sortBy)

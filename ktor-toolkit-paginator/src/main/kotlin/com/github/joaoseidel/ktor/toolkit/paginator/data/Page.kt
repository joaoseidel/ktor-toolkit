package com.github.joaoseidel.ktor.toolkit.paginator.data

import kotlinx.serialization.Serializable

/**
 * Which slice of a result to read: an index, and how many rows it spans.
 *
 * Nothing is validated here, because a [Page] is also the shape a repository is handed and a test
 * builds by hand. The clamping happens where untrusted input enters, in
 * [com.github.joaoseidel.ktor.toolkit.paginator.web.PaginationRequest.from], and a non-positive
 * [pageSize] is rejected where it would produce nonsense metadata, in
 * [com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse.from].
 *
 * @property page The current page index, starting from 0.
 * @property pageSize The number of items to be retrieved per page.
 */
@Serializable
data class Page(
    val page: Int,
    val pageSize: Int,
)

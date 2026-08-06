package com.github.joaoseidel.ktor.toolkit.paginator.data

import kotlinx.serialization.Serializable

/**
 * A page of results, together with the pagination and sorting that produced it.
 *
 * This is the repository-facing shape: map it to a [com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse]
 * before returning it from a route.
 *
 * @param T The type of elements contained in the paginated data.
 * @property page The pagination details, including the current page index and page size.
 * @property sortBy The list of sorting criteria applied to the dataset.
 * @property content The list of data elements on the current page.
 * @property totalElements The total number of elements in the dataset, across every page.
 */
@Serializable
data class Paged<T>(
    val page: Page = Page(0, 10),
    val sortBy: List<Sort> = emptyList(),
    val content: List<T> = emptyList(),
    val totalElements: Long,
)

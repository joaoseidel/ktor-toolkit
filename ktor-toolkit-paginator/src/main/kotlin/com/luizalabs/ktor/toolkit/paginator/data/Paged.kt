package com.luizalabs.ktor.toolkit.paginator.data

import kotlinx.serialization.Serializable

/**
 * Represents a paginated response containing a subset of data along with pagination and sorting details.
 *
 * The [Paged] class is used to encapsulate a paginated subset of items, along with metadata about the
 * current page, sorting criteria, and the total number of elements in the dataset.
 *
 * @param T The type of elements contained in the paginated data.
 * @property page The pagination details, including the current page number and page size.
 * @property sort The list of sorting criteria applied to the dataset.
 * @property content The list of data elements on the current page.
 * @property totalElements The total number of elements in the dataset.
 */
@ConsistentCopyVisibility
@Serializable
data class Paged<T> private constructor(
    val page: Page = Page(0, 10),
    val sortedBy: List<Sort> = emptyList(),
    val content: List<T> = emptyList(),
    val totalElements: Long,
) {
    companion object {
        /**
         * Creates a [Paged] instance containing a subset of data with associated pagination and sorting details.
         *
         * @param T The type of elements contained in the paginated data.
         * @param page The pagination details, including the current page index and page size.
         * @param sort The list of sorting criteria applied to the dataset.
         * @param content The list of data elements on the current page.
         * @param totalElements The total number of elements in the dataset.
         * @return A [Paged] instance initialized with the provided parameters.
         */
        fun <T> from(
            page: Page,
            sortedBy: List<Sort>,
            content: List<T>,
            totalElements: Long,
        ): Paged<T> = Paged(page, sortedBy, content, totalElements)
    }
}

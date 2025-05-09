package com.luizalabs.ktor.toolkit.paginator.web

import com.luizalabs.ktor.toolkit.paginator.data.Paged
import com.luizalabs.ktor.toolkit.paginator.data.Sort
import kotlinx.serialization.Serializable

/**
 * Represents a paginated response model containing metadata and content.
 *
 * @param T The type of the content within the paginated response.
 * @property metadata Contains information about the pagination such as current page, page size, total pages, etc.
 * @property content A list of entities that encapsulate content of type [T]. Can be null, but defaults to an empty list.
 */
@ConsistentCopyVisibility
@Serializable
data class PagedResponse<T> private constructor(
    val metadata: Metadata,
    val content: List<T>? = emptyList(),
) {
    /**
     * Represents metadata for paginated resources.
     *
     * This class encapsulates details related to pagination, sorting, and navigation for resources
     * retrieved in a paginated format. It provides information about the current page, page size,
     * total number of pages, total number of elements, sorting, and navigation availability.
     *
     * @property page The current page number, starting from 0.
     * @property pageSize The number of elements per page.
     * @property totalPages The total number of pages available.
     * @property totalElements The total number of elements across all pages.
     * @property hasNext Indicates whether there is a next page available.
     * @property isSorted Indicates whether the data is sorted.
     * @property sort A list of sorting criteria applied to the data, where each criterion specifies
     * the property being sorted and the direction (ascending or descending).
     */
    @ConsistentCopyVisibility
    @Serializable
    data class Metadata internal constructor(
        val page: Int,
        val pageSize: Int,
        val totalPages: Int,
        val totalElements: Long,
        val hasNext: Boolean,
        val hasPrevious: Boolean,
        val isSorted: Boolean,
        val sortCriteria: List<Sort> = emptyList(),
    )

    companion object {
        /**
         * Constructs a [PagedResponse] using the provided paged data and optional content transformation.
         *
         * @param paged The paginated data containing details like the current page, total elements,
         *                    sorting information, and more.
         * @param contentTransformer An optional function to transform the content items before including
         *                          them in the response. If not provided, items are used as-is.
         * @return A [PagedResponse] containing the metadata and transformed content
         *         for the current paginated response.
         */
        fun <T, R> from(
            paged: Paged<T>,
            contentTransformer: (((T) -> R)?)? = null,
        ): PagedResponse<R> {
            val pageSpec = paged.page
            val pageNumber = pageSpec.page
            val pageSize = pageSpec.pageSize
            val sortCriteria = paged.sortedBy
            val totalElements = paged.totalElements

            val isSorted = sortCriteria.isNotEmpty()

            var totalPages = (totalElements / pageSize).toInt()
            if (totalElements % pageSize == 0L && totalElements != 0L) totalPages -= 1
            val hasNext = pageNumber < totalPages
            val hasPrevious = pageNumber > 0

            val transformedContent =
                if (contentTransformer != null) {
                    paged.content.map(contentTransformer)
                } else {
                    @Suppress("UNCHECKED_CAST")
                    paged.content as List<R>
                }

            return PagedResponse(
                metadata =
                    Metadata(
                        page = pageNumber,
                        pageSize = pageSpec.pageSize,
                        totalPages = totalPages,
                        totalElements = totalElements,
                        hasNext = hasNext,
                        hasPrevious = hasPrevious,
                        isSorted = isSorted,
                        sortCriteria = sortCriteria,
                    ),
                content = transformedContent,
            )
        }
    }
}

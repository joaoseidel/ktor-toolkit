package com.github.joaoseidel.ktor.toolkit.paginator.web

import com.github.joaoseidel.ktor.toolkit.paginator.data.Paged
import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort
import kotlinx.serialization.Serializable

/**
 * A page of results as a client sees it: the entities, plus the metadata needed to navigate them.
 *
 * This is the wire shape — build it with [from] from the [Paged] a use case returned, and wrap it
 * with `toResource(call)` from the HATEOAS module to publish `next` and `prev` as links.
 *
 * Instances only come from [from], so the metadata is always consistent with the content.
 *
 * @param T The type of the content within the paginated response.
 * @property metadata Where this page sits in the whole result, and how it was ordered.
 * @property content The entities on the current page. Empty when the page is past the end of the data.
 */
@ConsistentCopyVisibility
@Serializable
data class PagedResponse<T> private constructor(
    val metadata: Metadata,
    val content: List<T> = emptyList(),
) {
    /**
     * Where a page sits in the whole result, and how the result was ordered.
     *
     * Every field is derived by [from] rather than reported by the data source, so a repository
     * only has to return the slice and the total.
     *
     * @property page The current page index, starting from 0.
     * @property pageSize The number of elements per page.
     * @property totalPages The number of pages available. Zero when there are no elements; the
     * last valid page index is therefore `totalPages - 1`.
     * @property totalElements The total number of elements across all pages.
     * @property hasNext Indicates whether there is a next page available.
     * @property hasPrevious Indicates whether there is a previous page available.
     * @property isSorted Indicates whether the data is sorted.
     * @property sortCriteria A list of sorting criteria applied to the data, where each criterion specifies
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
         * Derives the wire shape from a [Paged], mapping each element on the way out.
         *
         * The metadata is computed here rather than carried: `totalPages`, `hasNext` and
         * `hasPrevious` all follow from the page size and the total, so a repository never has to
         * report them.
         *
         * ```kotlin
         * PagedResponse.from(books) { it.toResponse() }
         * ```
         *
         * @param paged The page to describe, as a use case returned it.
         * @param contentTransformer Maps each element to its response type. Omit it only when the
         * elements are already the response type — `R` is then inferred as `T` and the content is
         * passed through unmapped.
         * @throws IllegalArgumentException if the page size is not positive.
         */
        fun <T, R> from(
            paged: Paged<T>,
            contentTransformer: ((T) -> R)? = null,
        ): PagedResponse<R> {
            val pageSpec = paged.page
            val pageNumber = pageSpec.page
            val pageSize = pageSpec.pageSize
            val sortCriteria = paged.sortBy
            val totalElements = paged.totalElements

            require(pageSize > 0) { "pageSize must be greater than 0, but was $pageSize" }

            val isSorted = sortCriteria.isNotEmpty()

            // Ceiling division: 25 elements over a page size of 10 spans 3 pages.
            val totalPages = ((totalElements + pageSize - 1) / pageSize).toInt()
            val hasNext = pageNumber < totalPages - 1
            val hasPrevious = pageNumber > 0

            val transformedContent =
                if (contentTransformer != null) {
                    paged.content.map(contentTransformer)
                } else {
                    // Sound only where `R` was inferred as `T`, which is what omitting the
                    // transformer means. Naming both explicitly — `from<Book, BookResponse>(paged)`
                    // — defeats it, and the heap pollution surfaces at the first read of `content`.
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

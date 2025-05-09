package com.luizalabs.ktor.toolkit.paginator.web

import com.luizalabs.ktor.toolkit.paginator.data.Page
import com.luizalabs.ktor.toolkit.paginator.data.Sort
import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

/**
 * Represents a model for handling paginated and sortable requests.
 *
 * This class is designed to manage pagination details and sorting preferences
 * for data retrieval operations. It encapsulates details about which page to fetch,
 * the number of elements per page, and sorting configurations for the data.
 *
 * @property page Specifies the pagination details, including the page number and the size of the page.
 * @property sortedBy A list of sorting criteria, where each criterion specifies the property
 * and the direction (ascending or descending) to sort by.
 */
@Serializable
data class PaginationRequest(
    val page: Page = Page(0, 10),
    val sortBy: List<Sort> = emptyList(),
) {
    companion object {
        /**
         * Creates a [PaginationRequest] instance based on the given pagination and sorting parameters.
         *
         * @param page The page number to fetch, typically starting from 0.
         * @param pageSize The number of elements to retrieve per page.
         * @param sortedBy A list of sorting instructions where each string defines the property to sort by
         *                 and optionally includes a prefix (`-`) to specify descending order.
         */
        fun from(
            page: Int,
            pageSize: Int,
            sortBy: List<String>,
        ): PaginationRequest =
            PaginationRequest(
                page = Page(page, pageSize),
                sortBy = sortBy.map { Sort.fromString(it) },
            )

        /**
         * Creates a [PaginationRequest] instance based on query parameters for pagination and sorting.
         *
         * @param queryParameters A set of query parameters where:
         * - "page" denotes the page number to fetch, starting from 0.
         * - "pageSize" specifies the number of items per page.
         * - "sortedBy" contains a comma-separated list of sorting criteria. Each criterion can optionally begin
         *   with a `-` indicating descending order.
         * @return A [PaginationRequest] instance containing the parsed pagination and sorting data.
         */
        fun from(queryParameters: Parameters): PaginationRequest {
            val page = queryParameters["page"]?.toInt() ?: 0
            val pageSize = queryParameters["pageSize"]?.toInt() ?: 10
            val sortBy = queryParameters["sortBy"]?.split(",") ?: emptyList()
            return from(page, pageSize, sortBy)
        }
    }
}

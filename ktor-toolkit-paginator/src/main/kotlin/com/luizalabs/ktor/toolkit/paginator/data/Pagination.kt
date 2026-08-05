package com.luizalabs.ktor.toolkit.paginator.data

/**
 * Represents a paginated request specification with sorting options.
 *
 * The [Pagination] class is used to define the pagination and sorting criteria for retrieving
 * data that is divided into pages. It consists of a [Page] object to specify the current page
 * and size, along with an optional list of [Sort] objects to define sorting rules.
 *
 * @property page The pagination information including the current page and size of the page.
 * @property sort A list of sorting definitions, specifying the properties and directions for ordering.
 */
data class Pagination(
    val page: Page,
    val sortedBy: List<Sort> = emptyList(),
)

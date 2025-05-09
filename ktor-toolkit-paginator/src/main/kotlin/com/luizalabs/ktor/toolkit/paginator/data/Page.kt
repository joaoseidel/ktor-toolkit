package com.luizalabs.ktor.toolkit.paginator.data

import kotlinx.serialization.Serializable

/**
 * Represents a pagination definition with the current page number and page size.
 *
 * The [Page] class is used for specifying pagination parameters, typically in
 * contexts where a data set is divided into discrete pages for retrieval.
 *
 * @property page The current page index, starting from 0.
 * @property pageSize The number of items to be retrieved per page.
 */
@Serializable
data class Page(
    val page: Int,
    val pageSize: Int,
)

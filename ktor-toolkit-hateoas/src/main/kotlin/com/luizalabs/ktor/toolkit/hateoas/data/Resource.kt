package com.luizalabs.ktor.toolkit.hateoas.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a generic resource model used for wrapping content and associated hypermedia links.
 *
 * The [Resource] is a data container designed to encapsulate a resource's content alongside
 * its navigable links, conforming to the HATEOAS principle. It supports serialization for usage in
 * APIs and is typically used to represent a resource within a hypermedia-driven application.
 *
 * @param T The type of the content being wrapped by the [Resource].
 * @property content The resource's content of type [T], representing the primary data.
 * @property links A list of [Link] objects representing hypermedia links associated with the resource.
 */
@Serializable(with = ResourceSerializer::class)
data class Resource<T>(
    val content: T,
    @SerialName("_links")
    val links: List<Link> = emptyList(),
) {
    /**
     * Returns a new instance of [Resource] with the specified [link] added to the current list of links.
     *
     * @param link The [Link] object to be added to the resource.
     * @return A new [Resource] instance with the updated list of links.
     */
    fun withLink(link: Link): Resource<T> = this.copy(links = this.links + link)

    /**
     * Returns a new instance of [Resource] with the specified list of [newLinks]
     * added to the current list of links.
     *
     * @param newLinks A list of [Link] objects to be added to the resource.
     * @return A new [Resource] instance with the updated list of links.
     */
    fun withLinks(newLinks: List<Link>): Resource<T> = this.copy(links = this.links + newLinks)
}

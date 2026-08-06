package com.github.joaoseidel.ktor.toolkit.hateoas.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Content, together with the links a client can follow from it.
 *
 * Serializes as the content's own fields plus a `_links` array, so wrapping a response in a
 * [Resource] does not nest it. Build one with the [resource] DSL, or construct it directly and add
 * links with [withLink].
 *
 * @param T The type of the content being wrapped.
 * @property content The resource's content, the primary data of the representation.
 * @property links The hypermedia links associated with the content.
 */
@Serializable(with = ResourceSerializer::class)
data class Resource<T>(
    val content: T,
    @SerialName("_links")
    val links: List<Link> = emptyList(),
) {
    /**
     * Returns a copy of this resource with [link] appended.
     *
     * @param link The link to publish.
     */
    fun withLink(link: Link): Resource<T> = copy(links = links + link)

    /**
     * Returns a copy of this resource with a link to [href] appended.
     *
     * @param rel How the link relates to the content, such as `self` or `next`.
     * @param href Where the link points.
     * @param method The HTTP method used to follow it. Defaults to [HttpMethod.Get].
     */
    fun withLink(
        rel: String,
        href: String,
        method: HttpMethod = HttpMethod.Get,
    ): Resource<T> = withLink(Link(rel, href, method))

    /**
     * Returns a copy of this resource with [newLinks] appended.
     *
     * @param newLinks The links to publish.
     */
    fun withLinks(newLinks: List<Link>): Resource<T> = copy(links = links + newLinks)
}

/**
 * Wraps [content] in a [Resource], declaring its links in a block.
 *
 * ```kotlin
 * resource(book) {
 *     link("self", "/books/${book.id}")
 *     link("delete", "/books/${book.id}", HttpMethod.Delete)
 * }
 * ```
 *
 * @param content The content to wrap.
 * @param block Declares the links to publish, in the order they should appear.
 */
fun <T> resource(
    content: T,
    block: LinksBuilder.() -> Unit = {},
): Resource<T> = Resource(content, LinksBuilder().apply(block).build())

/** Collects the links of a [resource] block. */
@HateoasDsl
class LinksBuilder internal constructor() {
    private val links = mutableListOf<Link>()

    /**
     * Publishes a link to [href].
     *
     * @param rel How the link relates to the content, such as `self` or `next`.
     * @param href Where the link points.
     * @param method The HTTP method used to follow it. Defaults to [HttpMethod.Get].
     */
    fun link(
        rel: String,
        href: String,
        method: HttpMethod = HttpMethod.Get,
    ) {
        links += Link(rel, href, method)
    }

    /**
     * Publishes an already built link, for one that came from elsewhere.
     *
     * @param link The link to publish.
     */
    fun link(link: Link) {
        links += link
    }

    /**
     * Publishes several already built links, such as the pagination ones.
     *
     * @param newLinks The links to publish.
     */
    fun links(newLinks: List<Link>) {
        links += newLinks
    }

    internal fun build(): List<Link> = links.toList()
}

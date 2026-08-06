package com.github.joaoseidel.ktor.toolkit.hateoas.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

/**
 * Represents a hyperlink model used for resource representation.
 *
 * The [Link] class is typically used in the context of Hypermedia as the Engine of Application
 * State (HATEOAS) to expose navigable links associated with a resource. It contains information such
 * as the relationship of the link to the resource, the URL of the link, and the HTTP method that
 * can be used to interact with the link.
 *
 * @property rel Describes the relationship of the hyperlink to the resource. Must not be blank.
 * @property href Specifies the URL that the hyperlink points to. Must not be blank.
 * @property method The HTTP method used to follow the link. Kept as a [String] so a deserialized
 * link round-trips whatever verb the producer sent, including non-standard ones.
 *
 * @throws IllegalArgumentException if [rel] or [href] are blank.
 */
@ConsistentCopyVisibility
@Serializable
data class Link private constructor(
    val rel: String,
    val href: String,
    val method: String,
) {
    // In the primary constructor, so it also guards values arriving from deserialization.
    init {
        require(rel.isNotBlank()) { "rel must not be blank" }
        require(href.isNotBlank()) { "href must not be blank" }
    }

    /**
     * Builds a link from a typed [HttpMethod].
     *
     * @param rel Describes the relationship of the hyperlink to the resource. Must not be blank.
     * @param href Specifies the URL that the hyperlink points to. Must not be blank.
     * @param method The HTTP method used to follow the link. Defaults to [HttpMethod.Get].
     */
    constructor(
        rel: String,
        href: String,
        method: HttpMethod = HttpMethod.Get,
    ) : this(rel, href, method.value)
}

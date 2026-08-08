package com.github.joaoseidel.ktor.toolkit.hateoas.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

/**
 * Somewhere a client can go from a resource, and how to get there.
 *
 * Published inside a [Resource]'s `_links`, either through the [resource] DSL or `withLink`.
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

package com.luizalabs.ktor.toolkit.hateoas.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable

/**
 * Represents a hyperlink model used for resource representation.
 *
 * The [Link] class is typically used in the context of Hypermedia as the Engine of Application
 * State (HATEOAS) to expose navigable links associated with a resource. It contains information such
 * as the relationship of the link to the resource, the URL of the link, and the HTTP method that
 * can be used to interact with the link.
 */
@ConsistentCopyVisibility
@Serializable
data class Link private constructor(
    val rel: String,
    val href: String,
    val method: String,
) {
    /**
     * Constructor for the [Link] class, allowing an [io.ktor.http.HttpMethod] object to be directly passed.
     *
     * @param rel Describes the relationship of the hyperlink to the resource. Must not be blank.
     * @param href Specifies the URL that the hyperlink points to. Must not be blank.
     * @param method Defines the HTTP method that can be used to interact with the link. Default is [io.ktor.http.HttpMethod.Companion.Get].
     *
     * @throws IllegalArgumentException if [rel] or [href] are blank.
     */
    constructor(
        rel: String,
        href: String,
        method: HttpMethod = HttpMethod.Get,
    ) : this(rel, href, method.value) {
        require(rel.isNotBlank()) { "rel must not be blank" }
        require(href.isNotBlank()) { "href must not be blank" }
    }
}

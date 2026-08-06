package com.github.joaoseidel.ktor.toolkit.expander.web

import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

/**
 * Parsed ?expand=field1,field2,field1.nested query parameter as a tree.
 * Field names are lower-cased and whitespace-trimmed.
 */
@Serializable
data class ExpandRequest(
    val fields: Map<String, ExpandRequest>,
) {
    /** True if the client requested expansion of this top-level field (case-insensitive). */
    fun wants(field: String): Boolean = field.lowercase().trim() in fields

    /**
     * Returns the sub-tree for a nested field.
     * expand.child("author").wants("books") → true for ?expand=author.books
     */
    fun child(field: String): ExpandRequest = fields[field.lowercase().trim()] ?: NONE

    /**
     * Flattens this tree into the dotted paths it implies, for data sources that take a fetch or
     * join list rather than a tree.
     *
     * `?expand=author.books,author.posts` → `["author", "author.books", "author.posts"]`.
     * Intermediate paths are always included, so each level can be resolved in turn.
     */
    fun toFetchPaths(): List<String> =
        buildList {
            fun walk(
                prefix: String,
                node: ExpandRequest,
            ) {
                add(prefix)
                node.fields.forEach { (key, sub) -> walk("$prefix.$key", sub) }
            }
            fields.forEach { (key, sub) -> walk(key, sub) }
        }

    val isEmpty: Boolean get() = fields.isEmpty()

    companion object {
        val NONE = ExpandRequest(emptyMap())

        fun from(queryParameters: Parameters): ExpandRequest {
            val raw = queryParameters["expand"] ?: return NONE
            val paths = raw.split(",").map { it.lowercase().trim() }.filter { it.isNotEmpty() }
            return if (paths.isEmpty()) NONE else fromPaths(paths)
        }

        private fun fromPaths(paths: List<String>): ExpandRequest {
            val tree = mutableMapOf<String, MutableList<String>>()
            for (path in paths) {
                val dot = path.indexOf('.')
                if (dot < 0) {
                    tree.getOrPut(path) { mutableListOf() }
                } else {
                    tree
                        .getOrPut(path.substring(0, dot)) { mutableListOf() }
                        .add(path.substring(dot + 1))
                }
            }
            return ExpandRequest(
                tree.mapValues { (_, children) ->
                    if (children.isEmpty()) NONE else fromPaths(children)
                },
            )
        }
    }
}

package com.github.joaoseidel.ktor.toolkit.expander.web

import io.ktor.http.Parameters
import kotlinx.serialization.Serializable

/**
 * What a client asked to expand, as a tree: `?expand=author.books,author.posts` becomes `author`
 * holding `books` and `posts`.
 *
 * A tree rather than a list of paths, because expansion is resolved one level at a time — each
 * level batches its own lookup and hands the level below to whatever it resolved. Names are
 * lowercased and trimmed on the way in, so matching is case-insensitive throughout.
 *
 * Obtain one with `call.expand`; nothing here fails on malformed input, and an absent or empty
 * parameter yields [NONE].
 *
 * @property fields The fields requested at this level, each mapped to what was requested under it.
 */
@Serializable
data class ExpandRequest(
    val fields: Map<String, ExpandRequest>,
) {
    /** Whether [field] was requested at this level. Case- and whitespace-insensitive. */
    fun wants(field: String): Boolean = field.lowercase().trim() in fields

    /**
     * What was requested *under* [field], for resolving the next level down.
     *
     * `expand.child("author").wants("books")` is true for `?expand=author.books`. A field that was
     * not requested, or was requested with nothing under it, yields [NONE] rather than `null`, so a
     * walk down the tree never has to null-check.
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

    /** Whether nothing was requested at this level, so there is no work to do. */
    val isEmpty: Boolean get() = fields.isEmpty()

    companion object {
        /** Nothing requested — what an absent `?expand=` parses to, and what [child] falls back to. */
        val NONE: ExpandRequest = ExpandRequest(emptyMap())

        /**
         * Parses `?expand=` out of [queryParameters].
         *
         * Never fails: an absent, empty or all-blank parameter yields [NONE], and a name that no
         * response field matches is simply never asked about. Only the first `expand` parameter is
         * read — the syntax is comma-separated, not repeated.
         */
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

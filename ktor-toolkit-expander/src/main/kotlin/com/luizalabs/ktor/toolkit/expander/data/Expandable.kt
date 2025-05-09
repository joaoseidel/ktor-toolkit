package com.luizalabs.ktor.toolkit.expander.data

import kotlinx.serialization.Serializable

/**
 * A field that is either a bare reference string or a fully resolved object.
 *
 * Serialized as:
 *   Ref("user:abc")        → "user:abc"
 *   Resolved(userResponse) → { "id": "...", ... }
 *
 * Because the @Serializable annotation references ExpandableSerializer by class,
 * the kotlinx.serialization plugin calls ExpandableSerializer(T.serializer())
 * automatically when Expandable<T> appears in any @Serializable data class —
 * no per-field @Serializable(with=...) annotation needed.
 */
@Serializable(with = ExpandableSerializer::class)
sealed interface Expandable<out T> {
    data class Ref(
        val id: String,
    ) : Expandable<Nothing>

    data class Resolved<out T>(
        val value: T,
    ) : Expandable<T>

    /**
     * A resolved object that will be serialized with only the requested [fields] included.
     * Used by [com.luizalabs.ktor.toolkit.expander.data.ExpandSpec] when the client requests
     * field-level projection via dot notation, e.g. `?expand=author.name,author.username`.
     *
     * [fields] must contain the JSON key names (lowercased) to keep in the output.
     * The serializer filters the fully-encoded object down to only those keys.
     */
    data class Partial<out T>(
        val value: T,
        val fields: Set<String>,
    ) : Expandable<T>
}

/**
 * Conditionally resolves this Expandable.
 * - If already Resolved, returns self (block is skipped).
 * - If Ref, calls block(id). On null result, keeps the original Ref.
 */
suspend fun <T> Expandable<T>.resolve(block: suspend (String) -> T?): Expandable<T> =
    when (this) {
        is Expandable.Resolved -> this
        is Expandable.Partial -> this
        is Expandable.Ref -> block(id)?.let { Expandable.Resolved(it) } ?: this
    }

/**
 * Batch-resolves a list of Expandable fields in a single [block] invocation.
 * All Ref IDs are collected into a set and passed once to [block].
 * Already-Resolved entries pass through unchanged.
 * Refs absent from the returned map stay as Ref.
 */
suspend fun <T> List<Expandable<T>>.resolveAll(block: suspend (Set<String>) -> Map<String, T>): List<Expandable<T>> {
    val refIds = filterIsInstance<Expandable.Ref>().map { it.id }.toSet()
    if (refIds.isEmpty()) return this
    val resolved = block(refIds)
    return map { expandable ->
        when (expandable) {
            is Expandable.Resolved -> expandable
            is Expandable.Partial -> expandable
            is Expandable.Ref -> resolved[expandable.id]?.let { Expandable.Resolved(it) } ?: expandable
        }
    }
}

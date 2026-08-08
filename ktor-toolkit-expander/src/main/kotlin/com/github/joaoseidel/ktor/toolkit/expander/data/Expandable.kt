package com.github.joaoseidel.ktor.toolkit.expander.data

import kotlinx.serialization.Serializable

/**
 * A response field that is either a bare reference or the object it refers to.
 *
 * Declaring one as `Expandable<UserResponse>` rather than `UserResponse` is what lets a single DTO
 * serve `GET /reviews` and `GET /reviews?expand=author` — the field serializes as the id string
 * until something resolves it, and as the object once it has:
 *
 * ```
 * Ref("user:abc")        → "user:abc"
 * Resolved(userResponse) → { "id": "user:abc", "name": "…" }
 * ```
 *
 * Naming [ExpandableSerializer] by class rather than by instance is what makes that automatic: the
 * kotlinx.serialization plugin supplies the content serializer wherever an `Expandable<T>` appears
 * in a `@Serializable` class, so no field needs its own `@Serializable(with = …)`.
 *
 * Resolve them declaratively with [ExpandSpec], which batches a whole page into one query per
 * field, or by hand with [resolve] and [resolveAll] for a one-off.
 *
 * @param T The type the reference resolves to.
 */
@Serializable(with = ExpandableSerializer::class)
sealed interface Expandable<out T> {
    /**
     * An unresolved reference, serialized as the bare [id] string.
     *
     * This is the state a field arrives in and stays in when nothing asked for it — and also when
     * something did but the data source had no such id, so a dangling reference degrades to the id
     * rather than failing the response.
     */
    data class Ref(
        val id: String,
    ) : Expandable<Nothing>

    /** A reference that has been resolved, serialized as the whole object. */
    data class Resolved<out T>(
        val value: T,
    ) : Expandable<T>

    /**
     * A resolved object serialized with only [fields] kept, for a client that asked for columns
     * rather than the whole object — `?expand=author.name,author.username`.
     *
     * [fields] holds JSON key names, lowercased; the serializer encodes [value] in full and then
     * drops every key not listed, so a name matching nothing simply yields an empty object.
     * [ExpandSpec] produces these; you rarely construct one.
     */
    data class Partial<out T>(
        val value: T,
        val fields: Set<String>,
    ) : Expandable<T>
}

/**
 * Resolves a single reference, for a field no [ExpandSpec] covers.
 *
 * [block] runs only for a [Expandable.Ref] — an already resolved or projected value is returned
 * untouched — and returning `null` from it leaves the reference as it was. Prefer an [ExpandSpec]
 * over calling this per item: one call per element is the N+1 this module exists to avoid.
 *
 * @param block Resolves one id, or returns `null` when there is no such object.
 */
suspend fun <T> Expandable<T>.resolve(block: suspend (String) -> T?): Expandable<T> =
    when (this) {
        is Expandable.Resolved -> this
        is Expandable.Partial -> this
        is Expandable.Ref -> block(id)?.let { Expandable.Resolved(it) } ?: this
    }

/**
 * Resolves a whole list of references in one call, for a field no [ExpandSpec] covers.
 *
 * [block] is invoked once with the union of the unresolved ids — never per element, and not at all
 * when there are none to resolve. An id absent from the returned map keeps its [Expandable.Ref],
 * and already resolved or projected entries pass through untouched.
 *
 * @param block Resolves a set of ids into `id to value`, in a single query.
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

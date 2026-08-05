package com.luizalabs.ktor.toolkit.expander.data

import com.luizalabs.ktor.toolkit.expander.web.ExpandRequest

/** Resolves a set of ref IDs into `id to value`, optionally projecting down to `fields`. */
private typealias Batch<F> = suspend (ids: Set<String>, fields: Set<String>) -> Map<String, F>

/**
 * Declarative specification for expanding a response type [T].
 *
 * Register fields once; apply to single items or lists:
 *   `spec.apply(item, expand)`        — single item
 *   `spec.apply(items, expand)`       — list, one batch call per registered field
 *
 * Field kinds:
 *   [Builder.field]            — single [Expandable] field (e.g. `author`)
 *   [Builder.optionalField]    — nullable single field (e.g. `mergedInto`)
 *   [Builder.listField]        — list field (e.g. `chapters`)
 *   [Builder.polymorphicField] — single field resolved by a per-item type discriminator
 *
 * Supports arbitrary nesting via the `nested` parameter on any field kind:
 *   `?expand=author.books` resolves `author` first, then applies a nested
 *   [ExpandSpec] to each resolved value using `expand.child("author")`.
 *
 * The batch lambda receives the **union** of all unresolved ref IDs — one query covers a whole
 * page, for every field kind.
 *
 * Field-level projection (`?expand=author.name,author.username`) is detected automatically.
 * When projection is requested the batch receives a non-empty `fields` set so the
 * data source can issue a selective query (e.g. `SELECT id, name, username FROM …`).
 * When `fields` is empty the caller should fetch all fields (`SELECT *`).
 */
class ExpandSpec<T> private constructor(
    private val fields: List<ExpandFieldSpec<T>>,
) {
    /** Names of all registered expandable fields. Used to detect projection requests. */
    val knownFields: Set<String> get() = fields.map { it.name }.toSet()

    /** Applies all registered field expansions to a single [item]. */
    suspend fun apply(
        item: T,
        expand: ExpandRequest,
    ): T = apply(listOf(item), expand).first()

    /** Applies all registered field expansions to [items], batching each field in one call. */
    suspend fun apply(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        var result = items
        for (field in fields) result = field.applyTo(result, expand)
        return result
    }

    class Builder<T> {
        private val fields = mutableListOf<ExpandFieldSpec<T>>()

        /**
         * Registers a single [Expandable] field (e.g. `author: Expandable<UserResponse>`).
         *
         * @param name   The `?expand=` key (case-insensitive).
         * @param getter Extracts the [Expandable] field from the item.
         * @param setter Returns a copy of the item with the field replaced (data-class `copy`).
         * @param nested Optional spec applied to the resolved value (enables `author.books`).
         * @param batch  Resolves a set of ref IDs → `id to value` map in a single call.
         *               Its second parameter is the set of JSON field names requested by the
         *               client (lowercased). When non-empty the caller may issue a selective query;
         *               when empty the caller should return all fields.
         */
        fun <F> field(
            name: String,
            getter: (T) -> Expandable<F>,
            setter: T.(Expandable<F>) -> T,
            nested: ExpandSpec<F>? = null,
            batch: Batch<F>,
        ) {
            fields += SingleFieldSpec(name, getter, setter, batch, nested)
        }

        /**
         * Registers an optional (nullable) single [Expandable] field
         * (e.g. `mergedInto: Expandable<TagResponse>?`).
         *
         * Behaves like [field] but leaves the item untouched when the getter returns null.
         *
         * @param name   The `?expand=` key (case-insensitive).
         * @param getter Extracts the nullable [Expandable] field from the item.
         * @param setter Returns a copy of the item with the field replaced.
         * @param nested Optional spec applied to the resolved value.
         * @param batch  Resolves a set of ref IDs → `id to value` map in a single call.
         */
        fun <F> optionalField(
            name: String,
            getter: (T) -> Expandable<F>?,
            setter: T.(Expandable<F>) -> T,
            nested: ExpandSpec<F>? = null,
            batch: Batch<F>,
        ) {
            fields += SingleFieldSpec(name, getter, setter, batch, nested)
        }

        /**
         * Registers a list [Expandable] field (e.g. `chapters: List<Expandable<ChapterResponse>>?`).
         *
         * @param name   The `?expand=` key (case-insensitive).
         * @param getter Extracts the nullable list field from the item.
         * @param setter Returns a copy of the item with the list replaced.
         * @param nested Optional spec applied to each resolved value.
         * @param batch  Resolves a set of ref IDs → `id to value` map in a single call.
         *               Its second parameter mirrors the same contract as in [field].
         */
        fun <F> listField(
            name: String,
            getter: (T) -> List<Expandable<F>>?,
            setter: T.(List<Expandable<F>>) -> T,
            nested: ExpandSpec<F>? = null,
            batch: Batch<F>,
        ) {
            fields += ListFieldSpec(name, getter, setter, batch, nested)
        }

        /**
         * Registers a polymorphic [Expandable] field, where the concrete type — and therefore the
         * data source to resolve it against — is decided per item.
         *
         * @param name      The `?expand=` key.
         * @param getter    Extracts the [Expandable] field from the item.
         * @param setter    Returns a copy of the item with the field replaced.
         * @param type      Extracts a type discriminator from the item (e.g. `it.organizerType`).
         * @param batchers  A map of type discriminator to its specific [ExpandSpec] and batcher.
         */
        fun <F> polymorphicField(
            name: String,
            getter: (T) -> Expandable<F>,
            setter: T.(Expandable<F>) -> T,
            type: (T) -> String,
            batchers: Map<String, Pair<ExpandSpec<out F>?, Batch<F>>>,
        ) {
            fields += PolymorphicFieldSpec(name, getter, setter, type, batchers)
        }

        internal fun build(): ExpandSpec<T> = ExpandSpec(fields.toList())
    }

    companion object {
        fun <T> build(block: Builder<T>.() -> Unit): ExpandSpec<T> = Builder<T>().apply(block).build()
    }
}

// Sealed interface — every field kind exposes only T, hiding F inside each implementation.
// This lets ExpandSpec store List<ExpandFieldSpec<T>> with no casts.
private sealed interface ExpandFieldSpec<T> {
    val name: String

    suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T>
}

/** Handles both `field` and `optionalField`: a null getter result simply leaves the item alone. */
private class SingleFieldSpec<T, F>(
    override val name: String,
    private val getter: (T) -> Expandable<F>?,
    private val setter: T.(Expandable<F>) -> T,
    private val batch: Batch<F>,
    private val nested: ExpandSpec<F>?,
) : ExpandFieldSpec<T> {
    override suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        if (!expand.wants(name)) return items

        val childExpand = expand.child(name)
        val projection = projectionFields(childExpand, nested)
        val resolved = batch.forRefs(items.mapNotNull(getter).refIds(), projection)

        return items.map { item ->
            val expandable = getter(item) ?: return@map item
            item.setter(expandable.resolveWith(resolved).finish(nested, projection, childExpand))
        }
    }
}

private class ListFieldSpec<T, F>(
    override val name: String,
    private val getter: (T) -> List<Expandable<F>>?,
    private val setter: T.(List<Expandable<F>>) -> T,
    private val batch: Batch<F>,
    private val nested: ExpandSpec<F>?,
) : ExpandFieldSpec<T> {
    override suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        if (!expand.wants(name)) return items

        val childExpand = expand.child(name)
        val projection = projectionFields(childExpand, nested)
        val resolved = batch.forRefs(items.flatMap { getter(it).orEmpty() }.refIds(), projection)

        return items.map { item ->
            val expandables = getter(item) ?: return@map item
            item.setter(expandables.map { it.resolveWith(resolved).finish(nested, projection, childExpand) })
        }
    }
}

private class PolymorphicFieldSpec<T, F>(
    override val name: String,
    private val getter: (T) -> Expandable<F>,
    private val setter: T.(Expandable<F>) -> T,
    private val type: (T) -> String,
    private val batchers: Map<String, Pair<ExpandSpec<out F>?, Batch<F>>>,
) : ExpandFieldSpec<T> {
    override suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        if (!expand.wants(name)) return items

        val childExpand = expand.child(name)

        // One batch call per discriminator, not per item.
        val resolved = mutableMapOf<String, F>()
        for ((discriminator, group) in items.groupBy(type)) {
            val (nested, batch) = batchers[discriminator] ?: continue
            resolved += batch.forRefs(group.map(getter).refIds(), projectionFields(childExpand, nested))
        }

        return items.map { item ->
            val (nested, _) = batchers[type(item)] ?: return@map item

            @Suppress("UNCHECKED_CAST")
            val nestedSpec = nested as ExpandSpec<F>?
            val projection = projectionFields(childExpand, nested)

            item.setter(getter(item).resolveWith(resolved).finish(nestedSpec, projection, childExpand))
        }
    }
}

/** The IDs of every entry still awaiting resolution. */
private fun <F> Iterable<Expandable<F>>.refIds(): Set<String> = filterIsInstance<Expandable.Ref>().mapTo(mutableSetOf()) { it.id }

/** Skips the round trip when nothing needs resolving, so a batcher never sees an empty ID set. */
private suspend fun <F> Batch<F>.forRefs(
    ids: Set<String>,
    projection: Set<String>,
): Map<String, F> = if (ids.isEmpty()) emptyMap() else this(ids, projection)

/** Swaps a [Expandable.Ref] for its resolved value; an ID absent from [resolved] stays a ref. */
private fun <F> Expandable<F>.resolveWith(resolved: Map<String, F>): Expandable<F> =
    if (this is Expandable.Ref) resolved[id]?.let { Expandable.Resolved(it) } ?: this else this

/**
 * Applies the nested spec and the field projection to a freshly resolved value.
 *
 * Unresolved refs and already-projected values pass through untouched.
 */
private suspend fun <F> Expandable<F>.finish(
    nested: ExpandSpec<F>?,
    projection: Set<String>,
    childExpand: ExpandRequest,
): Expandable<F> {
    if (this !is Expandable.Resolved) return this

    val expanded = if (nested != null) nested.apply(value, childExpand) else value
    return if (projection.isEmpty()) Expandable.Resolved(expanded) else Expandable.Partial(expanded, projection)
}

/**
 * The field names in [childExpand] that are not registered as expandable fields of [nestedSpec].
 *
 * Such unregistered paths are field-level projection selectors — `?expand=author.name,author.username`
 * asks for two columns of `author`, not for two nested expansions.
 */
private fun projectionFields(
    childExpand: ExpandRequest,
    nestedSpec: ExpandSpec<*>?,
): Set<String> {
    val known = nestedSpec?.knownFields ?: emptySet()
    return if (childExpand.fields.keys.any { it !in known }) childExpand.fields.keys else emptySet()
}

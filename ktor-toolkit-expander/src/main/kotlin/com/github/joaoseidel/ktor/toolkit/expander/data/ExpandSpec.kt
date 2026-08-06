package com.github.joaoseidel.ktor.toolkit.expander.data

import com.github.joaoseidel.ktor.toolkit.expander.web.ExpandRequest

/** Resolves a set of ref IDs into `id to value`, optionally projecting down to `fields`. */
private typealias Batch<F> = suspend (ids: Set<String>, fields: Set<String>) -> Map<String, F>

/**
 * Declarative specification for expanding a response type [T].
 *
 * Register fields once; apply to single items or lists:
 *   `spec.apply(item, expand)`        — single item
 *   `spec.apply(items, expand)`       — list, one batch call per registered field
 *
 * ```kotlin
 * val reviewSpec = ExpandSpec.build<Review> {
 *     field("author", get = { it.author }, set = { copy(author = it) }) {
 *         batch { ids, fields -> userRepository.findAll(ids, fields) }
 *         nested {
 *             listField("books", get = { it.books }, set = { copy(books = it) }) {
 *                 batch { ids, _ -> bookRepository.findAll(ids) }
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Field kinds:
 *   [Builder.field]            — single [Expandable] field, nullable or not (e.g. `author`)
 *   [Builder.listField]        — list field (e.g. `chapters`)
 *   [Builder.polymorphicField] — single field resolved by a per-item type discriminator
 *
 * Nesting is arbitrary: `?expand=author.books` resolves `author` first, then applies the nested
 * spec to each resolved value using `expand.child("author")`.
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

    /** Registers the expandable fields of [T]. Obtained through [build]. */
    @ExpandDsl
    class Builder<T> internal constructor() {
        private val fields = mutableListOf<ExpandFieldSpec<T>>()

        /**
         * Registers a single [Expandable] field, such as `author: Expandable<UserResponse>`.
         *
         * A nullable field works the same way: when [get] returns null the item is left untouched,
         * so `mergedInto: Expandable<TagResponse>?` needs no separate registration.
         *
         * @param name The `?expand=` key (case-insensitive).
         * @param get Extracts the [Expandable] field from the item.
         * @param set Returns a copy of the item with the field replaced (data-class `copy`).
         * @param configure Declares how to resolve the field — see [FieldBuilder].
         */
        fun <F> field(
            name: String,
            get: (T) -> Expandable<F>?,
            set: T.(Expandable<F>) -> T,
            configure: FieldBuilder<F>.() -> Unit,
        ) {
            val (batch, nested) = FieldBuilder<F>().apply(configure).build(name)
            fields += SingleFieldSpec(name, get, set, batch, nested)
        }

        /**
         * Registers a list [Expandable] field, such as `chapters: List<Expandable<ChapterResponse>>?`.
         *
         * @param name The `?expand=` key (case-insensitive).
         * @param get Extracts the list from the item. A null list leaves the item untouched.
         * @param set Returns a copy of the item with the list replaced.
         * @param configure Declares how to resolve the field — see [FieldBuilder].
         */
        fun <F> listField(
            name: String,
            get: (T) -> List<Expandable<F>>?,
            set: T.(List<Expandable<F>>) -> T,
            configure: FieldBuilder<F>.() -> Unit,
        ) {
            val (batch, nested) = FieldBuilder<F>().apply(configure).build(name)
            fields += ListFieldSpec(name, get, set, batch, nested)
        }

        /**
         * Registers an [Expandable] field whose concrete type — and therefore the data source to
         * resolve it against — is decided per item.
         *
         * ```kotlin
         * polymorphicField("organizer", get = { it.organizer }, set = { copy(organizer = it) },
         *                  type = { it.organizerType }) {
         *     case("user") { batch { ids, fields -> userRepository.findAll(ids, fields) } }
         *     case("team") { batch { ids, fields -> teamRepository.findAll(ids, fields) } }
         * }
         * ```
         *
         * An item whose discriminator has no registered case is left untouched.
         *
         * @param name The `?expand=` key.
         * @param get Extracts the [Expandable] field from the item.
         * @param set Returns a copy of the item with the field replaced.
         * @param type Extracts the type discriminator from the item (e.g. `it.organizerType`).
         * @param configure Declares one [PolymorphicBuilder.case] per discriminator.
         */
        fun <F> polymorphicField(
            name: String,
            get: (T) -> Expandable<F>,
            set: T.(Expandable<F>) -> T,
            type: (T) -> String,
            configure: PolymorphicBuilder<F>.() -> Unit,
        ) {
            fields += PolymorphicFieldSpec(name, get, set, type, PolymorphicBuilder<F>().apply(configure).build(name))
        }

        internal fun build(): ExpandSpec<T> {
            fields.forEach { require(it.name.isNotBlank()) { "An expandable field needs a name" } }

            // Two fields under one key would run two batches for the same `?expand=` request, the
            // second silently overwriting the first.
            val duplicates =
                fields
                    .groupingBy { it.name }
                    .eachCount()
                    .filterValues { it > 1 }
                    .keys
            require(duplicates.isEmpty()) { "Duplicate expandable fields: ${duplicates.joinToString()}" }

            return ExpandSpec(fields.toList())
        }
    }

    /** Declares how one field resolves its refs, and what to expand within the result. */
    @ExpandDsl
    class FieldBuilder<F> internal constructor() {
        private var batch: Batch<F>? = null
        private var nested: ExpandSpec<*>? = null

        /**
         * Resolves a set of ref IDs into `id to value`, in a single call for the whole page.
         *
         * The second parameter is the set of JSON field names the client asked for (lowercased).
         * When it is non-empty the source may issue a selective query; when empty it should return
         * all fields. IDs absent from the returned map stay unresolved refs.
         */
        fun batch(resolve: Batch<F>) {
            batch = resolve
        }

        /** Applies an existing spec to each resolved value, enabling `?expand=author.books`. */
        fun nested(spec: ExpandSpec<out F>) {
            nested = spec
        }

        /** Declares the nested spec inline, for one that is not shared with another field. */
        fun nested(configure: Builder<F>.() -> Unit) {
            nested = Builder<F>().apply(configure).build()
        }

        @Suppress("UNCHECKED_CAST")
        internal fun build(name: String): Pair<Batch<F>, ExpandSpec<F>?> {
            val resolve =
                requireNotNull(batch) { "Expandable field \"$name\" needs a batch { } block to resolve its refs" }

            // Safe: a spec declared for a subtype of F is only ever applied to values the batcher
            // of that same declaration produced.
            return resolve to nested as ExpandSpec<F>?
        }
    }

    /** Declares one resolution strategy per type discriminator, for [Builder.polymorphicField]. */
    @ExpandDsl
    class PolymorphicBuilder<F> internal constructor() {
        private val cases = mutableMapOf<String, Pair<Batch<F>, ExpandSpec<F>?>>()

        /**
         * Declares how items carrying [discriminator] resolve their refs.
         *
         * @param discriminator The value the field's `type` extractor returns for these items.
         * @param configure Declares the batch, and optionally a nested spec — see [FieldBuilder].
         */
        fun case(
            discriminator: String,
            configure: FieldBuilder<F>.() -> Unit,
        ) {
            require(discriminator !in cases) { "Duplicate case \"$discriminator\"" }
            cases[discriminator] = FieldBuilder<F>().apply(configure).build(discriminator)
        }

        internal fun build(name: String): Map<String, Pair<Batch<F>, ExpandSpec<F>?>> {
            require(cases.isNotEmpty()) { "Polymorphic field \"$name\" needs at least one case { } block" }
            return cases.toMap()
        }
    }

    companion object {
        /** Builds a spec for [T] from the fields [block] registers. */
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

/** A single field, nullable or not: a null getter result simply leaves the item alone. */
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
    private val cases: Map<String, Pair<Batch<F>, ExpandSpec<F>?>>,
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
            val (batch, nested) = cases[discriminator] ?: continue
            resolved += batch.forRefs(group.map(getter).refIds(), projectionFields(childExpand, nested))
        }

        return items.map { item ->
            val (_, nested) = cases[type(item)] ?: return@map item
            val projection = projectionFields(childExpand, nested)

            item.setter(getter(item).resolveWith(resolved).finish(nested, projection, childExpand))
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
private fun <F> Expandable<F>.resolveWith(resolved: Map<String, F>): Expandable<F> {
    if (this !is Expandable.Ref) return this
    val value = resolved[id] ?: return this
    return Expandable.Resolved(value)
}

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
    val known = if (nestedSpec == null) emptySet() else nestedSpec.knownFields
    return if (childExpand.fields.keys.any { it !in known }) childExpand.fields.keys else emptySet()
}

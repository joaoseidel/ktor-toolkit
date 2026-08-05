package com.luizalabs.ktor.toolkit.expander.data

import com.luizalabs.ktor.toolkit.expander.web.ExpandRequest

/**
 * Declarative specification for expanding a response type [T].
 *
 * Register fields once; apply to single items or lists:
 *   `spec.apply(item, expand)`        — single item
 *   `spec.apply(items, expand)`       — list, one batch call per registered field
 *
 * Two field types:
 *   [Builder.field]      — single [Expandable]<F> field (e.g. `author`)
 *   [Builder.listField]  — list [Expandable]<F> field (e.g. `chapters`)
 *
 * Supports arbitrary nesting via the `nested` parameter on either field type:
 *   `?expand=author.books` resolves `author` first, then applies a nested
 *   [ExpandSpec]<UserResponse> to each resolved value using `expand.child("author")`.
 *
 * The batch lambda receives the **union** of all unresolved ref IDs — for list
 * fields this means one query covers all items' refs in a single call.
 *
 * Field-level projection (`?expand=author.name,author.username`) is detected automatically.
 * When projection is requested the batch receives a non-empty [fields] set so the
 * data source can issue a selective query (e.g. `SELECT id, name, username FROM …`).
 * When [fields] is empty the caller should fetch all fields (`SELECT *`).
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
    ): T {
        var result = item
        for (field in fields) result = field.applyTo(result, expand)
        return result
    }

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
         * Registers a single [Expandable]<F> field (e.g. `author: Expandable<UserResponse>`).
         *
         * @param name   The `?expand=` key (case-insensitive).
         * @param getter Extracts the [Expandable] field from the item.
         * @param setter Returns a copy of the item with the field replaced (data-class `copy`).
         * @param nested Optional spec applied to the resolved value (enables `author.books`).
         * @param batch  Resolves a set of ref IDs → `id to value` map in a single call.
         *               Second parameter [fields] is the set of JSON field names requested by the
         *               client (lowercased). When non-empty the caller may issue a selective query;
         *               when empty the caller should return all fields.
         */
        fun <F> field(
            name: String,
            getter: (T) -> Expandable<F>,
            setter: T.(Expandable<F>) -> T,
            nested: ExpandSpec<F>? = null,
            batch: suspend (ids: Set<String>, fields: Set<String>) -> Map<String, F>,
        ) {
            fields += SingleFieldSpec(name, getter, setter, batch, nested)
        }

        /**
         * Registers an optional (nullable) single [Expandable]<F> field
         * (e.g. `mergedInto: Expandable<TagResponse>?`).
         *
         * Behaves like [field] but skips expansion when the getter returns null.
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
            batch: suspend (ids: Set<String>, fields: Set<String>) -> Map<String, F>,
        ) {
            fields += OptionalFieldSpec(name, getter, setter, batch, nested)
        }

        /**
         * Registers a list [Expandable]<F> field (e.g. `chapters: List<Expandable<ChapterResponse>>?`).
         *
         * The [batch] lambda receives the **union** of all unresolved ref IDs across all
         * items when called from [apply] — one query covers the entire page.
         *
         * @param name   The `?expand=` key (case-insensitive).
         * @param getter Extracts the nullable list field from the item.
         * @param setter Returns a copy of the item with the list replaced.
         * @param nested Optional spec applied to each resolved value.
         * @param batch  Resolves a set of ref IDs → `id to value` map in a single call.
         *               Second parameter [fields] mirrors the same contract as in [field].
         */
        fun <F> listField(
            name: String,
            getter: (T) -> List<Expandable<F>>?,
            setter: T.(List<Expandable<F>>) -> T,
            nested: ExpandSpec<F>? = null,
            batch: suspend (ids: Set<String>, fields: Set<String>) -> Map<String, F>,
        ) {
            fields += ListFieldSpec(name, getter, setter, batch, nested)
        }

        /**
         * Registers a polymorphic [Expandable]<F> field.
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
            batchers: Map<String, Pair<ExpandSpec<out F>?, suspend (ids: Set<String>, fields: Set<String>) -> Map<String, F>>>,
        ) {
            fields += PolymorphicFieldSpec(name, getter, setter, type, batchers)
        }

        internal fun build(): ExpandSpec<T> = ExpandSpec(fields.toList())
    }

    companion object {
        fun <T> build(block: Builder<T>.() -> Unit): ExpandSpec<T> = Builder<T>().apply(block).build()
    }
}

// Sealed interface — both field types expose only T, hiding F inside each impl.
// This lets ExpandSpec store List<ExpandFieldSpec<T>> with no casts.
private sealed interface ExpandFieldSpec<T> {
    val name: String

    suspend fun applyTo(
        item: T,
        expand: ExpandRequest,
    ): T

    suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T>
}

private class SingleFieldSpec<T, F>(
    override val name: String,
    private val getter: (T) -> Expandable<F>,
    private val setter: T.(Expandable<F>) -> T,
    private val batch: suspend (Set<String>, Set<String>) -> Map<String, F>,
    private val nested: ExpandSpec<F>? = null,
) : ExpandFieldSpec<T> {
    override suspend fun applyTo(
        item: T,
        expand: ExpandRequest,
    ): T {
        if (!expand.wants(name)) return item
        val childExpand = expand.child(name)
        val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()
        var expandable = getter(item)
        expandable = expandable.resolve { id -> batch(setOf(id), projFields)[id] }
        if (nested != null && expandable is Expandable.Resolved) {
            expandable = Expandable.Resolved(nested.apply(expandable.value, childExpand))
        }
        if (projFields.isNotEmpty() && expandable is Expandable.Resolved) {
            expandable = Expandable.Partial(expandable.value, projFields)
        }
        return item.setter(expandable)
    }

    override suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        if (!expand.wants(name)) return items
        val childExpand = expand.child(name)
        val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()
        var expandables = items.map { getter(it) }
        expandables = expandables.resolveAll { ids -> batch(ids, projFields) }
        if (nested != null) {
            expandables =
                expandables.map { exp ->
                    when (exp) {
                        is Expandable.Resolved -> Expandable.Resolved(nested.apply(exp.value, childExpand))
                        is Expandable.Partial -> exp
                        is Expandable.Ref -> exp
                    }
                }
        }
        if (projFields.isNotEmpty()) {
            expandables =
                expandables.map { exp ->
                    if (exp is Expandable.Resolved) Expandable.Partial(exp.value, projFields) else exp
                }
        }
        return items.zip(expandables).map { (item, expandable) -> item.setter(expandable) }
    }
}

private class ListFieldSpec<T, F>(
    override val name: String,
    private val getter: (T) -> List<Expandable<F>>?,
    private val setter: T.(List<Expandable<F>>) -> T,
    private val batch: suspend (Set<String>, Set<String>) -> Map<String, F>,
    private val nested: ExpandSpec<F>? = null,
) : ExpandFieldSpec<T> {
    override suspend fun applyTo(
        item: T,
        expand: ExpandRequest,
    ): T {
        if (!expand.wants(name)) return item
        val expandables = getter(item) ?: return item
        val childExpand = expand.child(name)
        val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()
        var resolved = expandables.resolveAll { ids -> batch(ids, projFields) }
        if (nested != null) {
            resolved =
                resolved.map { exp ->
                    when (exp) {
                        is Expandable.Resolved -> Expandable.Resolved(nested.apply(exp.value, childExpand))
                        is Expandable.Partial -> exp
                        is Expandable.Ref -> exp
                    }
                }
        }
        if (projFields.isNotEmpty()) {
            resolved =
                resolved.map { exp ->
                    if (exp is Expandable.Resolved) Expandable.Partial(exp.value, projFields) else exp
                }
        }
        return item.setter(resolved)
    }

    override suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        if (!expand.wants(name)) return items
        val childExpand = expand.child(name)
        val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()
        // One pass — collect every unresolved ref ID from every item
        val allIds =
            items
                .flatMap { item -> getter(item)?.filterIsInstance<Expandable.Ref>()?.map { it.id } ?: emptyList() }
                .toSet()
        if (allIds.isEmpty()) return items
        val resolved = batch(allIds, projFields)
        val needsProjection = projFields.isNotEmpty()
        return items.map { item ->
            val expandables = getter(item) ?: return@map item
            val expanded =
                expandables
                    .map { exp ->
                        when (exp) {
                            is Expandable.Resolved -> exp
                            is Expandable.Partial -> exp
                            is Expandable.Ref -> resolved[exp.id]?.let { Expandable.Resolved(it) } ?: exp
                        }
                    }.let { exps ->
                        if (nested == null) {
                            exps
                        } else {
                            exps.map { exp ->
                                when (exp) {
                                    is Expandable.Resolved -> Expandable.Resolved(nested.apply(exp.value, childExpand))
                                    is Expandable.Partial -> exp
                                    is Expandable.Ref -> exp
                                }
                            }
                        }
                    }.let { exps ->
                        if (needsProjection) {
                            exps.map { exp ->
                                if (exp is Expandable.Resolved) Expandable.Partial(exp.value, projFields) else exp
                            }
                        } else {
                            exps
                        }
                    }
            item.setter(expanded)
        }
    }
}

private class OptionalFieldSpec<T, F>(
    override val name: String,
    private val getter: (T) -> Expandable<F>?,
    private val setter: T.(Expandable<F>) -> T,
    private val batch: suspend (Set<String>, Set<String>) -> Map<String, F>,
    private val nested: ExpandSpec<F>? = null,
) : ExpandFieldSpec<T> {
    override suspend fun applyTo(
        item: T,
        expand: ExpandRequest,
    ): T {
        if (!expand.wants(name)) return item
        var expandable = getter(item) ?: return item
        val childExpand = expand.child(name)
        val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()
        expandable = expandable.resolve { id -> batch(setOf(id), projFields)[id] }
        if (nested != null && expandable is Expandable.Resolved) {
            expandable = Expandable.Resolved(nested.apply(expandable.value, childExpand))
        }
        if (projFields.isNotEmpty() && expandable is Expandable.Resolved) {
            expandable = Expandable.Partial(expandable.value, projFields)
        }
        return item.setter(expandable)
    }

    override suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        if (!expand.wants(name)) return items
        val childExpand = expand.child(name)
        val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()
        var expandables = items.map { getter(it) }
        val nonNullExpandables = expandables.filterNotNull()
        val resolved = nonNullExpandables.resolveAll { ids -> batch(ids, projFields) }
        var resolvedIdx = 0
        expandables =
            expandables.map { exp ->
                if (exp == null) null else resolved[resolvedIdx++]
            }
        val withNested =
            if (nested != null) {
                expandables.map { exp ->
                    when (exp) {
                        is Expandable.Resolved -> Expandable.Resolved(nested.apply(exp.value, childExpand))
                        is Expandable.Partial, is Expandable.Ref, null -> exp
                    }
                }
            } else {
                expandables
            }
        val withProjection =
            if (projFields.isNotEmpty()) {
                withNested.map { exp ->
                    if (exp is Expandable.Resolved) Expandable.Partial(exp.value, projFields) else exp
                }
            } else {
                withNested
            }
        return items.zip(withProjection).map { (item, expandable) ->
            if (expandable != null) item.setter(expandable) else item
        }
    }
}

private class PolymorphicFieldSpec<T, F>(
    override val name: String,
    private val getter: (T) -> Expandable<F>,
    private val setter: T.(Expandable<F>) -> T,
    private val type: (T) -> String,
    private val batchers: Map<String, Pair<ExpandSpec<out F>?, suspend (Set<String>, Set<String>) -> Map<String, F>>>,
) : ExpandFieldSpec<T> {
    override suspend fun applyTo(
        item: T,
        expand: ExpandRequest,
    ): T {
        if (!expand.wants(name)) return item
        val t = type(item)
        val (nested, batch) = batchers[t] ?: return item
        val childExpand = expand.child(name)
        val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()

        var expandable = getter(item)
        expandable = expandable.resolve { id -> batch(setOf(id), projFields)[id] }

        if (nested != null && expandable is Expandable.Resolved) {
            @Suppress("UNCHECKED_CAST")
            val nestedSpec = nested as ExpandSpec<F>
            expandable = Expandable.Resolved(nestedSpec.apply(expandable.value, childExpand))
        }
        if (projFields.isNotEmpty() && expandable is Expandable.Resolved) {
            expandable = Expandable.Partial(expandable.value, projFields)
        }
        return item.setter(expandable)
    }

    override suspend fun applyTo(
        items: List<T>,
        expand: ExpandRequest,
    ): List<T> {
        if (!expand.wants(name)) return items
        val childExpand = expand.child(name)

        val groups = items.groupBy { type(it) }
        val allResolved = mutableMapOf<String, F>()

        for ((t, group) in groups) {
            val (nested, batch) = batchers[t] ?: continue
            val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()
            val ids =
                group
                    .map { getter(it) }
                    .filterIsInstance<Expandable.Ref>()
                    .map { it.id }
                    .toSet()
            if (ids.isNotEmpty()) {
                val batchResolved = batch(ids, projFields)
                allResolved.putAll(batchResolved)
            }
        }

        return items.map { item ->
            val t = type(item)
            val (nested, _) = batchers[t] ?: return@map item
            val projFields = if (projectionNeeded(childExpand, nested)) childExpand.fields.keys else emptySet()

            var exp = getter(item)
            if (exp is Expandable.Ref) {
                exp = allResolved[exp.id]?.let { Expandable.Resolved(it) } ?: exp
            }

            if (nested != null && exp is Expandable.Resolved) {
                @Suppress("UNCHECKED_CAST")
                val nestedSpec = nested as ExpandSpec<F>
                exp = Expandable.Resolved(nestedSpec.apply(exp.value, childExpand))
            }
            if (projFields.isNotEmpty() && exp is Expandable.Resolved) {
                exp = Expandable.Partial(exp.value, projFields)
            }
            item.setter(exp)
        }
    }
}

/**
 * Returns true if [childExpand] contains any field name that is NOT registered
 * as an expandable field in [nestedSpec]. Such unregistered paths are treated as
 * field-level projection selectors (e.g. `?expand=author.name,author.username`).
 */
private fun projectionNeeded(
    childExpand: ExpandRequest,
    nestedSpec: ExpandSpec<*>?,
): Boolean = childExpand.fields.keys.any { it !in (nestedSpec?.knownFields ?: emptySet()) }

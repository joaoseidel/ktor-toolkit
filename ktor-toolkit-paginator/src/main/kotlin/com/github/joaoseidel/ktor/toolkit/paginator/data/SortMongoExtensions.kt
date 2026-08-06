package com.github.joaoseidel.ktor.toolkit.paginator.data

import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.DESC
import com.mongodb.client.model.Sorts
import org.bson.conversions.Bson
import kotlin.reflect.KProperty1

/**
 * Converts this [Sort] into a MongoDB sort document, resolved against [sortableFields].
 *
 * Only the listed fields are sortable — this is the allowlist that keeps a client-supplied
 * `?sortBy=` from reaching a field the endpoint never meant to expose, and from asking the server
 * for an unindexed sort.
 *
 * @throws IllegalArgumentException if [Sort.property] does not name one of [sortableFields].
 */
fun Sort.toMongoSortExpression(sortableFields: List<String>): Bson {
    val field =
        sortableFields.find { it == property }
            ?: throw IllegalArgumentException("Property \"$property\" does not match any sortable field. Is it misspelled?")

    return when (direction) {
        ASC -> Sorts.ascending(field)
        DESC -> Sorts.descending(field)
    }
}

/**
 * Combines this list of [Sort] into one MongoDB sort document, preserving the order of precedence.
 *
 * Unlike the Exposed conversion, this collapses to a single [Bson] rather than a list: that is what
 * `find(...).sort(...)` takes. No criteria produce an empty document, which MongoDB reads as
 * "unordered".
 *
 * @throws IllegalArgumentException if any [Sort.property] does not name one of [sortableFields].
 */
fun List<Sort>.toMongoSortExpression(sortableFields: List<String>): Bson = Sorts.orderBy(map { it.toMongoSortExpression(sortableFields) })

/** Vararg convenience for [toMongoSortExpression]. */
fun Sort.toMongoSortExpression(vararg sortableFields: String): Bson = toMongoSortExpression(sortableFields.asList())

/** Vararg convenience for [toMongoSortExpression]. */
fun List<Sort>.toMongoSortExpression(vararg sortableFields: String): Bson = toMongoSortExpression(sortableFields.asList())

/**
 * Resolves against property references, so a renamed field is a compiler error rather than an
 * allowlist entry that silently stops matching.
 *
 * Point these at the document class the driver maps, not at the domain entity — the allowlist has
 * to name the field as it is stored.
 */
fun Sort.toMongoSortExpression(vararg sortableProperties: KProperty1<*, *>): Bson = toMongoSortExpression(sortableProperties.map { it.name })

/** Resolves against property references — see the [Sort] overload. */
fun List<Sort>.toMongoSortExpression(vararg sortableProperties: KProperty1<*, *>): Bson = toMongoSortExpression(sortableProperties.map { it.name })

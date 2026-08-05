package com.luizalabs.ktor.toolkit.paginator.data

import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.DESC
import io.github.joaoseidel.geldsl.core.GelOrderingExpression
import io.github.joaoseidel.geldsl.core.entity.properties.GelPropertyRef
import io.github.joaoseidel.geldsl.core.entity.properties.asc
import io.github.joaoseidel.geldsl.core.entity.properties.desc

/**
 * Converts this [Sort] into a Gel ordering expression, resolved against [sortableProps].
 *
 * Only the listed properties are sortable — this is the allow-list that keeps a client-supplied
 * `?sortBy=` from reaching a property the endpoint never meant to expose.
 *
 * @throws IllegalArgumentException if [Sort.property] does not name one of [sortableProps].
 */
fun Sort.toGelOrderingExpression(sortableProps: List<GelPropertyRef>): GelOrderingExpression {
    val match =
        sortableProps.find { it.name == property }
            ?: throw IllegalArgumentException("Property \"$property\" does not match any sortable property. Is it misspelled?")

    return when (direction) {
        ASC -> match.asc()
        DESC -> match.desc()
    }
}

/**
 * Converts this list of [Sort] into Gel ordering expressions, preserving order of precedence.
 *
 * @throws IllegalArgumentException if any [Sort.property] does not name one of [sortableProps].
 */
fun List<Sort>.toGelOrderingExpression(sortableProps: List<GelPropertyRef>): List<GelOrderingExpression> =
    map { it.toGelOrderingExpression(sortableProps) }

/** Vararg convenience for [toGelOrderingExpression]. */
fun Sort.toGelOrderingExpression(vararg sortableProps: GelPropertyRef): GelOrderingExpression = toGelOrderingExpression(sortableProps.asList())

/** Vararg convenience for [toGelOrderingExpression]. */
fun List<Sort>.toGelOrderingExpression(vararg sortableProps: GelPropertyRef): List<GelOrderingExpression> =
    toGelOrderingExpression(sortableProps.asList())

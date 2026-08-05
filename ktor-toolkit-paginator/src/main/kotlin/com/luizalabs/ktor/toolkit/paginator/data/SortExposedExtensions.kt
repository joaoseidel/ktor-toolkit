package com.luizalabs.ktor.toolkit.paginator.data

import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.DESC
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table

/**
 * Converts this [Sort] into an Exposed `ORDER BY` term, resolved against [sortableColumns].
 *
 * Only the listed columns are sortable — this is the allow-list that keeps a client-supplied
 * `?sortBy=` from reaching a column the endpoint never meant to expose.
 *
 * @throws IllegalArgumentException if [Sort.property] does not name one of [sortableColumns].
 */
fun Sort.toExposedQueryExpression(sortableColumns: List<Column<*>>): Pair<Expression<*>, SortOrder> {
    val column =
        sortableColumns.find { it.name == property }
            ?: throw IllegalArgumentException("Property \"$property\" does not match any sortable column. Is it misspelled?")

    return when (direction) {
        ASC -> column to SortOrder.ASC
        DESC -> column to SortOrder.DESC
    }
}

/**
 * Converts this list of [Sort] into Exposed `ORDER BY` terms, preserving order of precedence.
 *
 * @throws IllegalArgumentException if any [Sort.property] does not name one of [sortableColumns].
 */
fun List<Sort>.toExposedQueryExpression(sortableColumns: List<Column<*>>): List<Pair<Expression<*>, SortOrder>> =
    map { it.toExposedQueryExpression(sortableColumns) }

/** Resolves against every column of [table]. */
fun Sort.toExposedQueryExpression(table: Table): Pair<Expression<*>, SortOrder> = toExposedQueryExpression(table.columns)

/** Resolves against every column of [table]. */
fun List<Sort>.toExposedQueryExpression(table: Table): List<Pair<Expression<*>, SortOrder>> = toExposedQueryExpression(table.columns)

/** Vararg convenience for [toExposedQueryExpression]. */
fun Sort.toExposedQueryExpression(vararg sortableColumns: Column<*>): Pair<Expression<*>, SortOrder> =
    toExposedQueryExpression(sortableColumns.asList())

/** Vararg convenience for [toExposedQueryExpression]. */
fun List<Sort>.toExposedQueryExpression(vararg sortableColumns: Column<*>): List<Pair<Expression<*>, SortOrder>> =
    toExposedQueryExpression(sortableColumns.asList())

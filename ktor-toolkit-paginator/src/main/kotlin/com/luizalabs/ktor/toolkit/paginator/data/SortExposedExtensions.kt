package com.luizalabs.ktor.toolkit.paginator.data

import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.DESC
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table

/**
 * Converts the [Sort] instance into an expression that can be used in a query,
 * associating a specific column with a sorting order.
 *
 * @param table The [Table] containing the columns used for sorting.
 */
fun Sort.toExposedQueryExpression(table: Table): Pair<Expression<*>, SortOrder> =
    this.toExposedQueryExpression(*table.columns.toTypedArray())

/**
 * Converts a list of [Sort] objects into a list of expressions for constructing query sorting logic.
 *
 * Each [Sort] in the list is mapped to an expression and a sort order based on the provided table's columns.
 *
 * @param table The [Table] whose columns will be used to map the properties in the [Sort] objects.
 */
fun List<Sort>.toExposedQueryExpression(table: Table): List<Pair<Expression<*>, SortOrder>> =
    this.toExposedQueryExpression(*table.columns.toTypedArray())

/**
 * Converts a [Sort] object into a query expression using a list of sortable columns.
 *
 * This method resolves the property specified in the [Sort] instance against the provided
 * list of columns. It returns a query expression that pairs a matching column with the
 * specified sort order (ascending or descending).
 *
 * @param sortableColumns A list of columns that can be used for sorting. Each column represents
 * a potential field to be matched with the property in the [Sort] object.
 * @throws IllegalArgumentException If the property in the [Sort] instance does not match
 * any column in the provided list.
 */
fun Sort.toExposedQueryExpression(sortableColumns: List<Column<*>>): Pair<Expression<*>, SortOrder> =
    this.toExposedQueryExpression(*sortableColumns.toTypedArray())

/**
 * Converts a list of [Sort] instances into query expressions based on the provided sortable columns.
 * Each [Sort] property is matched against the provided list of sortable columns, and the corresponding query expressions
 * are generated to represent the sorting configuration.
 *
 * @param sortableColumns A list of columns that can be used for sorting, typically representing database fields.
 * These columns are matched against the `property` of each [Sort] object to map the sort criteria.
 * @throws IllegalArgumentException If any [Sort] has a `property` that does not match any of the provided columns.
 */
fun List<Sort>.toExposedQueryExpression(sortableColumns: List<Column<*>>): List<Pair<Expression<*>, SortOrder>> =
    this.toExposedQueryExpression(*sortableColumns.toTypedArray())

/**
 * Converts the [Sort] instance into a pairing of an expression and a sort order
 * based on the provided sortable columns.
 *
 * @param sortableColumns The columns that can be used for sorting, provided as vararg parameters.
 * @return A [Pair] where the first element is the selected column as an [Expression] and the second is the associated [SortOrder].
 * @throws IllegalArgumentException If the `property` in the [Sort] instance does not match any of the provided sortable columns.
 */
fun Sort.toExposedQueryExpression(vararg sortableColumns: Column<*>): Pair<Expression<*>, SortOrder> {
    val column =
        sortableColumns.find { it.name == property }
            ?: throw IllegalArgumentException("Property \"$property\" does not match any sortable column. Is it misspelled?")

    return when (direction) {
        ASC -> column to SortOrder.ASC
        DESC -> column to SortOrder.DESC
    }
}

/**
 * Converts a list of [Sort] instances into a list of expressions and sort orders,
 * based on the provided sortable columns.
 *
 * Each [Sort] in the list is mapped to a [Pair], where the first element is an
 * [Expression] representing the sortable column and the second is the corresponding [SortOrder].
 *
 * @param sortableColumns The columns that can be used as sorting criteria, provided as vararg parameters.
 * These are matched against the `property` fields of the [Sort] instances to determine the mapping.
 * @throws IllegalArgumentException If any [Sort] instance has a `property` that does not match any of the given sortable columns.
 */
fun List<Sort>.toExposedQueryExpression(vararg sortableColumns: Column<*>): List<Pair<Expression<*>, SortOrder>> =
    map { it.toExposedQueryExpression(*sortableColumns) }

package com.github.joaoseidel.ktor.toolkit.filter.data

import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EQ
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EXISTS
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.GT
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.GTE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LIKE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LT
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LTE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_EQ
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_LIKE
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.EnumerationColumnType
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.StringColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.core.or

/**
 * Converts this [Filter] into an Exposed condition, resolved against [filterableColumns].
 *
 * Only the listed columns are filterable — the allow-list that keeps a client-supplied `?filterBy=` from reaching a
 * column the endpoint never meant to expose. Each operand is typed by the column it targets: text for a string column,
 * a number for a numeric one, `true`/`false` for a boolean, the constant's name for an enum, and whatever the column
 * itself parses for any other type.
 *
 * The negative operators — `notEq`, `notLike`, `notIn` — also match `NULL` on a nullable column, where SQL alone would
 * drop those rows. A row without a value is not equal to anything, and MongoDB answers the same way.
 *
 * @throws InvalidFilterException if [Filter.property] names none of [filterableColumns], an operand does not parse as
 * the column's type, or `like` targets a column that is not text.
 */
fun Filter.toExposedCondition(filterableColumns: List<Column<*>>): Op<Boolean> {
    val column =
        filterableColumns.find { it.name == property }
            ?: throw InvalidFilterException("Property \"$property\" cannot be filtered. Is it misspelled?", property)

    return column.conditionFor(this)
}

/**
 * Converts these [Filter]s into one Exposed condition that requires all of them. No filters match every row.
 *
 * @throws InvalidFilterException if any filter cannot be applied — see the [Filter] overload.
 */
fun List<Filter>.toExposedCondition(filterableColumns: List<Column<*>>): Op<Boolean> =
    map { it.toExposedCondition(filterableColumns) }.reduceOrNull { all, next -> all and next } ?: Op.TRUE

/** Resolves against every column of [table]. */
fun Filter.toExposedCondition(table: Table): Op<Boolean> = toExposedCondition(table.columns)

/** Resolves against every column of [table]. */
fun List<Filter>.toExposedCondition(table: Table): Op<Boolean> = toExposedCondition(table.columns)

/** Vararg convenience for [toExposedCondition]. */
fun Filter.toExposedCondition(vararg filterableColumns: Column<*>): Op<Boolean> = toExposedCondition(filterableColumns.asList())

/** Vararg convenience for [toExposedCondition]. */
fun List<Filter>.toExposedCondition(vararg filterableColumns: Column<*>): Op<Boolean> = toExposedCondition(filterableColumns.asList())

// Safe: every operand passed to these columns comes from operandFor, which parses it with the column's own type.
@Suppress("UNCHECKED_CAST")
private fun Column<*>.conditionFor(filter: Filter): Op<Boolean> {
    val column = this as Column<Any>
    val comparable = this as Column<Comparable<Any>>

    fun operand(raw: String): Any = operandFor(filter, raw)

    fun comparableOperand(): Comparable<Any> =
        operand(filter.value) as? Comparable<Any>
            ?: throw InvalidFilterException("\"${filter.property}\" cannot be compared with ${filter.operator.token}.", filter.property)

    return when (filter.operator) {
        EQ -> column eq operand(filter.value)
        NOT_EQ -> orNull(column neq operand(filter.value))
        GT -> comparable greater comparableOperand()
        GTE -> comparable greaterEq comparableOperand()
        LT -> comparable less comparableOperand()
        LTE -> comparable lessEq comparableOperand()
        LIKE -> textFor(filter).lowerCase() like containing(filter.value)
        NOT_LIKE -> orNull(textFor(filter).lowerCase() notLike containing(filter.value))
        IN -> column inList filter.values.map(::operand)
        NOT_IN -> orNull(column notInList filter.values.map(::operand))
        EXISTS -> if (filter.exists) column.isNotNull() else column.isNull()
    }
}

private fun Column<*>.orNull(condition: Op<Boolean>): Op<Boolean> = if (columnType.nullable) condition or isNull() else condition

@Suppress("UNCHECKED_CAST")
private fun Column<*>.textFor(filter: Filter): Column<String> =
    if (columnType is StringColumnType) {
        this as Column<String>
    } else {
        throw InvalidFilterException("\"${filter.property}\" is not text, so it cannot be searched with ${filter.operator.token}.", filter.property)
    }

// Escaped here rather than with LikePattern.ofLiteral, which asks the current dialect and so cannot be built outside a
// transaction.
private fun containing(text: String): LikePattern {
    val literal =
        text
            .lowercase()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    return LikePattern("%$literal%", '\\')
}

/**
 * Reads [raw] as a value of this column's type.
 *
 * Decimals, booleans and ordinal enums are parsed here because their column types do not read text. Booleans are
 * parsed strictly: Exposed's own reading depends on the database dialect and takes anything but `true` as `false`.
 */
private fun Column<*>.operandFor(
    filter: Filter,
    raw: String,
): Any {
    val type = columnType
    val value =
        try {
            when (type) {
                is DecimalColumnType -> raw.toBigDecimal()
                is BooleanColumnType -> raw.toBooleanStrict()
                is EnumerationColumnType<*> ->
                    type.klass.java.enumConstants
                        .firstOrNull { it.name == raw }
                else -> type.valueFromDB(raw)
            }
        } catch (_: Exception) {
            null
        }

    return value ?: throw InvalidFilterException("\"$raw\" is not a valid value for \"${filter.property}\".", filter.property)
}

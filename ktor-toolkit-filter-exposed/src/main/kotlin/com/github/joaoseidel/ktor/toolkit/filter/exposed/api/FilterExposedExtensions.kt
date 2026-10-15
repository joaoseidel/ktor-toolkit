package com.github.joaoseidel.ktor.toolkit.filter.exposed.api

import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.EQ
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.EXISTS
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.GT
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.GTE
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.LIKE
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.LT
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.LTE
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.NOT_EQ
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.NOT_IN
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.NOT_LIKE
import com.github.joaoseidel.ktor.toolkit.filter.api.error.InvalidFilterException
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.CustomStringFunction
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.EnumerationColumnType
import org.jetbrains.exposed.v1.core.EqOp
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.GreaterEqOp
import org.jetbrains.exposed.v1.core.GreaterOp
import org.jetbrains.exposed.v1.core.LessEqOp
import org.jetbrains.exposed.v1.core.LessOp
import org.jetbrains.exposed.v1.core.LikePattern
import org.jetbrains.exposed.v1.core.NeqOp
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryParameter
import org.jetbrains.exposed.v1.core.StringColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.notLike
import org.jetbrains.exposed.v1.core.ops.SingleValueInListOp
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

private fun Column<*>.conditionFor(filter: Filter): Op<Boolean> {
    fun operand(raw: String): QueryParameter<*> = operandFor(filter, raw)

    fun comparableOperand(): QueryParameter<*> =
        operand(filter.value).takeIf { it.value is Comparable<*> }
            ?: throw InvalidFilterException("\"${filter.property}\" cannot be compared with ${filter.operator.token}.", filter.property)

    return when (filter.operator) {
        EQ -> EqOp(this, operand(filter.value))
        NOT_EQ -> orNull(NeqOp(this, operand(filter.value)))
        GT -> GreaterOp(this, comparableOperand())
        GTE -> GreaterEqOp(this, comparableOperand())
        LT -> LessOp(this, comparableOperand())
        LTE -> LessEqOp(this, comparableOperand())
        LIKE -> lowerTextFor(filter).like(containing(filter.value))
        NOT_LIKE -> orNull(lowerTextFor(filter).notLike(containing(filter.value)))
        IN -> SingleValueInListOp(this, filter.values.map { operand(it).value }, isInList = true)
        NOT_IN -> orNull(SingleValueInListOp(this, filter.values.map { operand(it).value }, isInList = false))
        EXISTS -> if (filter.exists) isNotNull() else isNull()
    }
}

private fun Column<*>.orNull(condition: Op<Boolean>): Op<Boolean> = if (columnType.nullable) condition or isNull() else condition

private fun Column<*>.lowerTextFor(filter: Filter): Expression<String?> =
    if (columnType is StringColumnType) {
        CustomStringFunction("LOWER", this)
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
): QueryParameter<*> {
    val operand =
        try {
            when (val type = columnType) {
                is DecimalColumnType -> QueryParameter(raw.toBigDecimal(), type)
                is BooleanColumnType -> QueryParameter(raw.toBooleanStrict(), type)
                is EnumerationColumnType<*> -> type.constantNamed(raw)
                else -> parameterFrom(raw)
            }
        } catch (_: Exception) {
            null
        }

    return operand ?: throw InvalidFilterException("\"$raw\" is not a valid value for \"${filter.property}\".", filter.property)
}

private fun <E : Enum<E>> EnumerationColumnType<E>.constantNamed(raw: String): QueryParameter<E>? =
    klass.java.enumConstants
        .firstOrNull { it.name == raw }
        ?.let { QueryParameter(it, this) }

private fun <T> Column<T>.parameterFrom(raw: String): QueryParameter<T & Any>? = columnType.valueFromDB(raw)?.let { QueryParameter(it, columnType) }

private val Filter.value: String get() = values.single()

private val Filter.exists: Boolean get() = value == "true"

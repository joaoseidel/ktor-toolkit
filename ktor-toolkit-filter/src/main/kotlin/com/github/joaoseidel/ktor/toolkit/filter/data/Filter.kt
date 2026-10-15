package com.github.joaoseidel.ktor.toolkit.filter.data

import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EXISTS
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_IN
import kotlinx.serialization.Serializable
import kotlin.reflect.KProperty1

/**
 * One condition a listing must satisfy: a property, an operator, and the operands as the client wrote them.
 *
 * Like a sort, a filter carries a property *name* and *text*, so it crosses from a query string to a repository without
 * either end depending on the other's types. Both are checked only when a data source resolves the filter against an
 * allow-list — `toExposedCondition` types each operand with the column it targets, `toMongoFilter` with the declared
 * field — which is what stops a client-supplied `?filterBy=` reaching a column the endpoint never meant to expose.
 *
 * @property property The name of the property to test.
 * @property operator How to test it.
 * @property values The operands, as text: exactly one for most operators, at least one for [Operator.IN] and
 * [Operator.NOT_IN], and `true` or `false` for [Operator.EXISTS].
 * @throws InvalidFilterException if [property] is blank or [values] do not fit [operator].
 */
@Serializable
data class Filter(
    val property: String,
    val operator: Operator,
    val values: List<String>,
) {
    // In the primary constructor, so it also guards filters arriving from deserialization.
    init {
        if (property.isBlank()) throw InvalidFilterException("A filter needs a property to test.")

        val condition = "$property:${operator.token}"
        when (operator) {
            IN, NOT_IN ->
                if (values.isEmpty()) throw InvalidFilterException("\"$condition\" needs at least one value.", property)
            EXISTS ->
                if (values.singleOrNull() !in EXISTS_VALUES) throw InvalidFilterException("\"$condition\" takes true or false.", property)
            else ->
                if (values.size != 1) throw InvalidFilterException("\"$condition\" takes exactly one value.", property)
        }
    }

    /**
     * Filters on a property reference, so a rename cannot leave a stale filter key behind.
     *
     * @param property The property to test. Its name becomes the filter key.
     * @param operator How to test it.
     * @param values The operands, as text.
     */
    constructor(
        property: KProperty1<*, *>,
        operator: Operator,
        vararg values: String,
    ) : this(property.name, operator, values.asList())

    /** The only operand, for every operator but [Operator.IN] and [Operator.NOT_IN]. */
    internal val value: String get() = values.single()

    /** What an [Operator.EXISTS] condition asks for: `true` for present, `false` for absent. */
    internal val exists: Boolean get() = value == "true"

    /**
     * How a [Filter] tests its property.
     *
     * @property token How the operator is written in `?filterBy=`.
     */
    @Serializable
    enum class Operator(
        val token: String,
    ) {
        EQ("eq"),
        NOT_EQ("notEq"),
        GT("gt"),
        GTE("gte"),
        LT("lt"),
        LTE("lte"),

        /** Contains the operand, ignoring case. */
        LIKE("like"),

        /** Does not contain the operand, ignoring case. */
        NOT_LIKE("notLike"),
        IN("in"),
        NOT_IN("notIn"),

        /** Has a value (`true`) or has none (`false`). */
        EXISTS("exists"),
        ;

        companion object {
            /** The operator written as [token], or `null` when none is. Tokens are case-sensitive. */
            fun fromToken(token: String): Operator? = entries.find { it.token == token }
        }
    }
}

private val EXISTS_VALUES = setOf("true", "false")

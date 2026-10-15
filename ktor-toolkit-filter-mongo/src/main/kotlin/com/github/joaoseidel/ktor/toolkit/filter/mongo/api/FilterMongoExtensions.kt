package com.github.joaoseidel.ktor.toolkit.filter.mongo.api

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
import com.github.joaoseidel.ktor.toolkit.filter.api.data.FilterDsl
import com.github.joaoseidel.ktor.toolkit.filter.api.error.InvalidFilterException
import com.github.joaoseidel.ktor.toolkit.filter.mongo.runtime.FieldType
import com.github.joaoseidel.ktor.toolkit.filter.mongo.runtime.UnreachableBytecode
import com.mongodb.client.model.Filters
import org.bson.conversions.Bson
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * The fields a MongoDB listing can be filtered on, each with the type its operands are read as.
 *
 * MongoDB has no schema to type an operand by, so the allow-list carries the types itself. Build it once per
 * collection with [filterableFields] and hand it to [toMongoFilter].
 */
class FilterableFields internal constructor(
    internal val fields: Map<String, FieldType>,
)

/**
 * Declares the fields a MongoDB listing can be filtered on:
 *
 * ```kotlin
 * val filterable = filterableFields {
 *     field(BookDocument::title)
 *     field(BookDocument::price)
 *     field<Instant>("published_at")
 * }
 * ```
 *
 * Point property references at the document class the driver maps, not at the domain entity: the field has to be
 * named as it is stored.
 *
 * @throws IllegalArgumentException if a field's type is not one of those [FilterableFieldsBuilder.field] supports.
 */
fun filterableFields(block: FilterableFieldsBuilder.() -> Unit): FilterableFields =
    FilterableFields(FilterableFieldsBuilder().apply(block).fields.toMap())

/** Collects the fields of a [filterableFields] block. */
@FilterDsl
class FilterableFieldsBuilder internal constructor() {
    internal val fields = linkedMapOf<String, FieldType>()

    /**
     * Makes [property] filterable, its operands read as the type it holds.
     *
     * Supported types: `String`, `Int`, `Long`, `Double`, `BigDecimal`, `Boolean`, enums (matched by constant name),
     * `kotlin.time.Instant`, `java.time.Instant`, `java.time.LocalDate` and `ObjectId`.
     */
    @UnreachableBytecode
    inline fun <reified V : Any> field(property: KProperty1<*, V?>) {
        field(property.name, V::class)
    }

    /** Makes the field named [name] filterable, its operands read as [V]. Supports the types listed on the property overload. */
    @UnreachableBytecode
    inline fun <reified V : Any> field(name: String) {
        field(name, V::class)
    }

    @PublishedApi
    internal fun field(
        name: String,
        type: KClass<*>,
    ) {
        fields[name] =
            FieldType.of(type) ?: throw IllegalArgumentException("Field \"$name\" cannot be filtered: ${type.qualifiedName} is not supported.")
    }
}

/**
 * Converts this [Filter] into a MongoDB query document, resolved against [filterableFields].
 *
 * Only the declared fields are filterable, and each operand is read as the field's declared type. `like` and `notLike`
 * match the operand anywhere in the text, ignoring case; `exists` asks for a value that is present and not `null`.
 *
 * @throws InvalidFilterException if [Filter.property] is not declared, an operand does not parse as the field's type,
 * or `like` targets a field that is not text.
 */
fun Filter.toMongoFilter(filterableFields: FilterableFields): Bson {
    val type =
        filterableFields.fields[property]
            ?: throw InvalidFilterException("Property \"$property\" cannot be filtered. Is it misspelled?", property)

    fun operand(raw: String): Any =
        try {
            type.parse(raw)
        } catch (_: Exception) {
            throw InvalidFilterException("\"$raw\" is not a valid value for \"$property\".", property)
        }

    fun containing(): Pattern {
        if (!type.isText) throw InvalidFilterException("\"$property\" is not text, so it cannot be searched with ${operator.token}.", property)
        return Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE)
    }

    return when (operator) {
        EQ -> Filters.eq(property, operand(value))
        NOT_EQ -> Filters.ne(property, operand(value))
        GT -> Filters.gt(property, operand(value))
        GTE -> Filters.gte(property, operand(value))
        LT -> Filters.lt(property, operand(value))
        LTE -> Filters.lte(property, operand(value))
        LIKE -> Filters.regex(property, containing())
        NOT_LIKE -> Filters.not(Filters.regex(property, containing()))
        IN -> Filters.`in`(property, values.map(::operand))
        NOT_IN -> Filters.nin(property, values.map(::operand))
        EXISTS -> if (exists) Filters.ne(property, null) else Filters.eq(property, null)
    }
}

/**
 * Converts these [Filter]s into one MongoDB query document that requires all of them. No filters match every document.
 *
 * @throws InvalidFilterException if any filter cannot be applied — see the [Filter] overload.
 */
fun List<Filter>.toMongoFilter(filterableFields: FilterableFields): Bson =
    if (isEmpty()) Filters.empty() else Filters.and(map { it.toMongoFilter(filterableFields) })

private val Filter.value: String get() = values.single()

private val Filter.exists: Boolean get() = value == "true"

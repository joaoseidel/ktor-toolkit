package com.github.joaoseidel.ktor.toolkit.filter.data

import com.github.joaoseidel.ktor.toolkit.filter.UnreachableBytecode
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
import com.mongodb.client.model.Filters
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.LocalDate
import java.util.regex.Pattern
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.time.Instant

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
 * How the operands of one field are read.
 *
 * @property isText Whether `like` and `notLike` apply to the field.
 * @property parse Reads one operand, or throws if it does not parse.
 */
internal class FieldType(
    val isText: Boolean,
    val parse: (String) -> Any,
) {
    companion object {
        // Instants are queried as java.time.Instant whatever the document holds: the default codec registry encodes
        // it as a BSON date, and has no codec for kotlin.time.Instant. Enums are queried by name, as they are stored.
        fun of(type: KClass<*>): FieldType? =
            when {
                type == String::class -> FieldType(isText = true) { it }
                type == Int::class -> FieldType(isText = false, String::toInt)
                type == Long::class -> FieldType(isText = false, String::toLong)
                type == Double::class -> FieldType(isText = false, String::toDouble)
                type == BigDecimal::class -> FieldType(isText = false, String::toBigDecimal)
                type == Boolean::class -> FieldType(isText = false, String::toBooleanStrict)
                type == Instant::class || type == java.time.Instant::class -> FieldType(isText = false, java.time.Instant::parse)
                type == LocalDate::class -> FieldType(isText = false, LocalDate::parse)
                type == ObjectId::class -> FieldType(isText = false, ::ObjectId)
                type.java.isEnum -> {
                    val names =
                        type.java.enumConstants
                            .map { (it as Enum<*>).name }
                            .toSet()
                    FieldType(isText = false) { raw -> raw.also { require(it in names) } }
                }
                else -> null
            }
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

package com.github.joaoseidel.ktor.toolkit.filter.mongo.runtime

import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.reflect.KClass
import kotlin.time.Instant

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

package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

/**
 * Asserts that a string's length falls in `min..max`, both inclusive.
 *
 * @param min The smallest accepted size.
 * @param max The largest accepted size.
 */
@JvmName("sizeOfString")
fun PropertyValidator<*, String?>.size(
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
): ValidationRule<String?> = sizeRule(min, max)

/**
 * Asserts that a collection's size falls in `min..max`, both inclusive.
 *
 * @param min The smallest accepted size.
 * @param max The largest accepted size.
 */
@JvmName("sizeOfCollection")
fun PropertyValidator<*, Collection<*>?>.size(
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
): ValidationRule<Collection<*>?> = sizeRule(min, max)

/**
 * Asserts that a map's size falls in `min..max`, both inclusive.
 *
 * @param min The smallest accepted size.
 * @param max The largest accepted size.
 */
@JvmName("sizeOfMap")
fun PropertyValidator<*, Map<*, *>?>.size(
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
): ValidationRule<Map<*, *>?> = sizeRule(min, max)

/**
 * Asserts that an array's size falls in `min..max`, both inclusive.
 *
 * @param min The smallest accepted size.
 * @param max The largest accepted size.
 */
@JvmName("sizeOfArray")
fun PropertyValidator<*, Array<*>?>.size(
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
): ValidationRule<Array<*>?> = sizeRule(min, max)

private fun sizeRule(
    min: Int,
    max: Int,
): ValidationRule<Any?> =
    validationRule(
        positiveMessage = "size should be between $min and $max",
        negativeMessage = "size should not be between $min and $max",
    ) { value ->
        val size =
            when (value) {
                is String -> value.length

                is Collection<*> -> value.size

                is Map<*, *> -> value.size

                is Array<*> -> value.size

                // Unreachable: the receiver of every `size` overload pins the type.
                else -> return@validationRule false
            }

        size in min..max
    }

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
): ValidationRule<String?> = sizeRule(min, max, String::length)

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
): ValidationRule<Collection<*>?> = sizeRule(min, max, Collection<*>::size)

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
): ValidationRule<Map<*, *>?> = sizeRule(min, max, Map<*, *>::size)

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
): ValidationRule<Array<*>?> = sizeRule(min, max, Array<*>::size)

private fun <V : Any> sizeRule(
    min: Int,
    max: Int,
    measure: (V) -> Int,
): ValidationRule<V?> =
    validationRule(
        positiveMessage = "size should be between $min and $max",
        negativeMessage = "size should not be between $min and $max",
    ) { value -> measure(value) in min..max }

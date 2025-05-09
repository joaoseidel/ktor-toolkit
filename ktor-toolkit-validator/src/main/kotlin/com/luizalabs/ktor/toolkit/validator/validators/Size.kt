package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure the size of a property value is within a specified range.
 *
 * This method validates whether the size of the property value falls between the specified
 * minimum and maximum boundaries (inclusive). It supports property types such as String,
 * Collection, Array, and Map.
 *
 * @param min The minimum allowed size for the property. Defaults to 0.
 * @param max The maximum allowed size for the property. Defaults to [Int.MAX_VALUE].
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "size should be between $min and $max".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "size should not be between $min and $max".
 */
fun PropertyValidator<*, *>.size(
    min: Int = 0,
    max: Int = Int.MAX_VALUE,
    positiveMessage: String = "size should be between $min and $max",
    negativeMessage: String = "size should not be between $min and $max",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> =
            listOf(
                String::class.java,
                Collection::class.java,
                Array::class.java,
                Map::class.java,
            )

        override fun validate(value: Any?): Boolean {
            val size =
                when (value) {
                    is String -> value.length
                    is Collection<*> -> value.size
                    is Array<*> -> value.size
                    is Map<*, *> -> value.size
                    else -> return false
                }

            return size in min..max
        }
    }

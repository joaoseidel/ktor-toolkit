package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property is negative.
 *
 * This method validates whether the property value is a negative number (less than zero) and
 * can also check its negated condition (not negative). It supports various numeric types,
 * such as Int, Long, Float, and Double.
 *
 * @param positiveMessage The error message to be used if the property fails the validation when the rule is not negated.
 *                        Defaults to "should be negative".
 * @param negativeMessage The error message to be used if the property fails the validation when the rule is negated.
 *                        Defaults to "should not be negative".
 */
fun PropertyValidator<*, *>.negative(
    positiveMessage: String = "should be negative",
    negativeMessage: String = "should not be negative",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(Number::class.java)

        override fun validate(value: Any?) =
            when (value) {
                is Int -> value < 0
                is Long -> value < 0
                is Float -> value < 0
                is Double -> value < 0
                else -> false
            }
    }

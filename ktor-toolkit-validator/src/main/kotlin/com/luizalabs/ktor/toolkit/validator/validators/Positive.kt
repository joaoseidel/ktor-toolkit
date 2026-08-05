package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property value is positive.
 *
 * This method validates whether the property value is greater than zero
 * and can also check its negated condition (not positive). It is applicable
 * to numeric properties of types Int, Long, Float, and Double.
 *
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should be positive".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not be positive".
 */
fun PropertyValidator<*, *>.positive(
    positiveMessage: String = "should be positive",
    negativeMessage: String = "should not be positive",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(Number::class.java)

        override fun validate(value: Any?) =
            when (value) {
                is Int -> value > 0
                is Long -> value > 0
                is Float -> value > 0
                is Double -> value > 0
                else -> false
            }
    }

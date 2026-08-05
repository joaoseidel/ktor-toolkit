package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property value is less than or equal to the specified value.
 * This rule is applicable to numeric properties and can enforce both positive and negated conditions.
 *
 * @param maxValue The maximum allowed value for the property.
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should be less than or equal to $value".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not be less than or equal to $value".
 */
fun PropertyValidator<*, *>.max(
    maxValue: Number,
    positiveMessage: String = "should be less than or equal to $maxValue",
    negativeMessage: String = "should not be less than or equal to $maxValue",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(Number::class.java)

        override fun validate(value: Any?) =
            when (value) {
                is Int -> value <= maxValue.toInt()
                is Long -> value <= maxValue.toLong()
                is Float -> value <= maxValue.toFloat()
                is Double -> value <= maxValue.toDouble()
                else -> false
            }
    }

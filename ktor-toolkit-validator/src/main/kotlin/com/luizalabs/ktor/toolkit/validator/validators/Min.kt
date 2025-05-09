package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property value is greater than or equal to the specified minimum value.
 * This rule is applicable to numeric properties and can enforce both positive and negated conditions.
 *
 * @param minValue The minimum allowed value for the property.
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should be greater than or equal to $minValue".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not be greater than or equal to $minValue".
 */
fun PropertyValidator<*, *>.min(
    minValue: Number,
    positiveMessage: String = "should be greater than or equal to $minValue",
    negativeMessage: String = "should not be greater than or equal to $minValue",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(Number::class.java)

        override fun validate(value: Any?) =
            when (value) {
                is Int -> value >= minValue.toInt()
                is Long -> value >= minValue.toLong()
                is Float -> value >= minValue.toFloat()
                is Double -> value >= minValue.toDouble()
                else -> false
            }
    }

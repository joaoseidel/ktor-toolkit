package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property is null.
 *
 * This method validates whether the property value is null and can also check its negated condition (not null).
 * It can be used in expressions to enforce properties to have a null value or to validate that they are not null.
 *
 * @param positiveMessage The error message to be used if the property fails the validation when the rule is not negated.
 *                        Defaults to "should be null".
 * @param negativeMessage The error message to be used if the property fails the validation when the rule is negated.
 *                        Defaults to "should not be null".
 */
fun PropertyValidator<*, *>.nil(
    positiveMessage: String = "should be null",
    negativeMessage: String = "should not be null",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override val appliesToNull: Boolean get() = true

        override fun supportedTypes(): List<Class<*>> = emptyList()

        override fun validate(value: Any?) = value == null
    }

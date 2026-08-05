package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property is blank or not blank.
 *
 * This method validates whether the property value is empty or consists only of whitespace
 * characters and can also check its negated condition (not blank). It can be used in
 * expressions to enforce properties to have a blank value or to validate that they are not blank.
 *
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should be blank".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not be blank".
 */
fun PropertyValidator<*, *>.blank(
    positiveMessage: String = "should be blank",
    negativeMessage: String = "should not be blank",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(String::class.java)

        override fun validate(value: Any?) = value.toString().isBlank()
    }

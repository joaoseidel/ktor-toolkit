package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property is a valid email address.
 *
 * This method validates whether the property value matches a standard email address format
 * and can also verify its negated condition (not a valid email address). It supports `String`
 * type properties and checks the compliance of the value against a predefined regular
 * expression for valid email addresses.
 *
 * @param positiveMessage The error message to be used if the property fails the validation when the rule is not negated.
 *                        Defaults to "should be a valid email address".
 * @param negativeMessage The error message to be used if the property fails the validation when the rule is negated.
 *                        Defaults to "should not be a valid email address".
 */
fun PropertyValidator<*, *>.email(
    positiveMessage: String = "should be a valid email address",
    negativeMessage: String = "should not be a valid email address",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        private val emailPattern =
            Regex("[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+")

        override fun supportedTypes(): List<Class<*>> = listOf(String::class.java)

        override fun validate(value: Any?) = emailPattern.matches(value.toString())
    }

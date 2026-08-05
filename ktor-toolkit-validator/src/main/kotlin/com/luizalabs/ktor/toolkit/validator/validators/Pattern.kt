package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a property's value matches a specified regular expression pattern.
 * This rule is applicable to string properties and can enforce both positive and negated conditions.
 *
 * @param regex The regular expression pattern that the property's value should match.
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should match pattern \"$regex\"".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not match pattern \"$regex\"".
 */
fun PropertyValidator<*, *>.pattern(
    regex: Regex,
    positiveMessage: String = "should match pattern \"$regex\"",
    negativeMessage: String = "should not match pattern \"$regex\"",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(String::class.java)

        override fun validate(value: Any?) = regex.matches(value.toString())
    }

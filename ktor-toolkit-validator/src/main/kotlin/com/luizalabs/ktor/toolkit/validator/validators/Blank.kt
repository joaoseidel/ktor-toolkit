package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import com.luizalabs.ktor.toolkit.validator.validationRule

/**
 * Asserts that a string is empty or whitespace only, or — negated — that it carries something.
 *
 * Applies to a `String` property.
 */
fun PropertyValidator<*, String?>.blank(): ValidationRule<String?> =
    validationRule(
        positiveMessage = "should be blank",
        negativeMessage = "should not be blank",
        test = String::isBlank,
    )

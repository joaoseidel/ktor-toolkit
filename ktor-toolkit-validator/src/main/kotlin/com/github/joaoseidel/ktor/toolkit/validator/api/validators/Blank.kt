package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule

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

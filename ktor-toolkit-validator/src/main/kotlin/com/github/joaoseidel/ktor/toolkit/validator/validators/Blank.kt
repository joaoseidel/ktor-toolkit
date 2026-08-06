package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

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

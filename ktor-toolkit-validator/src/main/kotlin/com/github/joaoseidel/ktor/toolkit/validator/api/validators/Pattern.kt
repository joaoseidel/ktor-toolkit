package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule

/**
 * Asserts that a string matches [regex] in full.
 *
 * Applies to a `String` property.
 *
 * @param regex The pattern the value has to match.
 */
fun PropertyValidator<*, String?>.pattern(regex: Regex): ValidationRule<String?> =
    validationRule(
        positiveMessage = "should match pattern \"$regex\"",
        negativeMessage = "should not match pattern \"$regex\"",
        test = regex::matches,
    )

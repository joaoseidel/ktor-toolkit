package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

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

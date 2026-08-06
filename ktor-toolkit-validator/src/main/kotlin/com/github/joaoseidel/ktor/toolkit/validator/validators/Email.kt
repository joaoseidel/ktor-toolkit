package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

private val EMAIL_PATTERN =
    Regex("[a-zA-Z0-9+._%\\-]{1,256}@[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}(\\.[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25})+")

/**
 * Asserts that a string looks like an email address.
 *
 * Applies to a `String` property.
 */
fun PropertyValidator<*, String?>.email(): ValidationRule<String?> =
    validationRule(
        positiveMessage = "should be a valid email address",
        negativeMessage = "should not be a valid email address",
        test = EMAIL_PATTERN::matches,
    )

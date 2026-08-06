package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule

/**
 * Asserts that a property is absent, or — negated — that it is present.
 *
 * This is the one rule with an opinion about `null`; every other rule stays silent on an absent
 * value, so requiring a field and constraining it are two separate assertions:
 *
 * ```kotlin
 * property(CreateBookRequest::authorEmail) {
 *     should notBe nil()
 *     should be email()
 * }
 * ```
 *
 * Applies to a property of any type.
 */
fun PropertyValidator<*, Any?>.nil(): ValidationRule<Any?> =
    ValidationRule(
        positiveMessage = "should be null",
        negativeMessage = "should not be null",
        appliesToNull = true,
    ) { value -> value == null }

package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

/**
 * Asserts that a number is greater than zero.
 *
 * Applies to a property of any [Number] type. Zero is neither positive nor negative, so
 * `should notBe positive()` is not the same as `should be negative()`.
 */
fun PropertyValidator<*, Number?>.positive(): ValidationRule<Number?> =
    validationRule(
        positiveMessage = "should be positive",
        negativeMessage = "should not be positive",
    ) { value -> value.compareWith(0) > 0 }

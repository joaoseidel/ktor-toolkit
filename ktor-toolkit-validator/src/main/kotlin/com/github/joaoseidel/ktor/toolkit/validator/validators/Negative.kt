package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

/**
 * Asserts that a number is less than zero.
 *
 * Applies to a property of any [Number] type. Zero is neither negative nor positive, so
 * `should notBe negative()` is not the same as `should be positive()`.
 */
fun PropertyValidator<*, Number?>.negative(): ValidationRule<Number?> =
    validationRule(
        positiveMessage = "should be negative",
        negativeMessage = "should not be negative",
    ) { value -> value.compareWith(0) < 0 }

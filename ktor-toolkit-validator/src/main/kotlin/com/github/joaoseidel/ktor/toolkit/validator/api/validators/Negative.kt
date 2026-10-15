package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule
import com.github.joaoseidel.ktor.toolkit.validator.runtime.compareWith

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

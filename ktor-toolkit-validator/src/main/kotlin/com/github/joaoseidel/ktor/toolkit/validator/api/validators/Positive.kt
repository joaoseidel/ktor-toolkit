package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule
import com.github.joaoseidel.ktor.toolkit.validator.runtime.compareWith

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

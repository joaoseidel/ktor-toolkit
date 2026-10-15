package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule
import com.github.joaoseidel.ktor.toolkit.validator.runtime.compareWith

/**
 * Asserts that a number is greater than or equal to [minValue].
 *
 * Applies to a property of any [Number] type, and [minValue] need not share it.
 *
 * @param minValue The lowest accepted value.
 */
fun PropertyValidator<*, Number?>.min(minValue: Number): ValidationRule<Number?> =
    validationRule(
        positiveMessage = "should be greater than or equal to $minValue",
        negativeMessage = "should not be greater than or equal to $minValue",
    ) { value -> value.compareWith(minValue) >= 0 }

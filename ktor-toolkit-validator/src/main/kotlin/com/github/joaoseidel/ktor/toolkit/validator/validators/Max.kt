package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

/**
 * Asserts that a number is less than or equal to [maxValue].
 *
 * Applies to a property of any [Number] type, and [maxValue] need not share it.
 *
 * @param maxValue The highest accepted value.
 */
fun PropertyValidator<*, Number?>.max(maxValue: Number): ValidationRule<Number?> =
    validationRule(
        positiveMessage = "should be less than or equal to $maxValue",
        negativeMessage = "should not be less than or equal to $maxValue",
    ) { value -> value.compareWith(maxValue) <= 0 }

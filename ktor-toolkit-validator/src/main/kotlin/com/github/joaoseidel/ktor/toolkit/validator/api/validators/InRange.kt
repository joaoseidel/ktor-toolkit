package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule
import com.github.joaoseidel.ktor.toolkit.validator.runtime.compareWith

/**
 * Asserts that a number falls in `min..max`, both inclusive.
 *
 * Applies to a property of any [Number] type, and the bounds need not share it.
 *
 * @param min The lowest accepted value.
 * @param max The highest accepted value.
 */
fun PropertyValidator<*, Number?>.inRange(
    min: Number,
    max: Number,
): ValidationRule<Number?> =
    validationRule(
        positiveMessage = "should be in range of $min..$max",
        negativeMessage = "should not be in range of $min..$max",
    ) { value -> value.compareWith(min) >= 0 && value.compareWith(max) <= 0 }

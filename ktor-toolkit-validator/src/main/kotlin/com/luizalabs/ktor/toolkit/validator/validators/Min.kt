package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import com.luizalabs.ktor.toolkit.validator.validationRule

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

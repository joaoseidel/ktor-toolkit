package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

/**
 * Adds a validation rule to ensure a numeric property falls within `min..max`, both inclusive.
 *
 * Bounds and value may be any [Number]. Integral values are compared exactly; anything involving a
 * `Float` or `Double` is compared as a double.
 *
 * @param min The lowest accepted value.
 * @param max The highest accepted value.
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should be in range of $min..$max".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not be in range of $min..$max".
 */
fun PropertyValidator<*, *>.inRange(
    min: Number,
    max: Number,
    positiveMessage: String = "should be in range of $min..$max",
    negativeMessage: String = "should not be in range of $min..$max",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(Number::class.java)

        override fun validate(value: Any?): Boolean {
            val number = value as? Number ?: return false

            return if (number.isIntegral() && min.isIntegral() && max.isIntegral()) {
                number.toLong() in min.toLong()..max.toLong()
            } else {
                number.toDouble() in min.toDouble()..max.toDouble()
            }
        }
    }

private fun Number.isIntegral(): Boolean = this is Byte || this is Short || this is Int || this is Long

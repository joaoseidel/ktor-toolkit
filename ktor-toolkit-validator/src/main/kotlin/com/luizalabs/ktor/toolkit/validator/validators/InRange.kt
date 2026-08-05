package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule

fun PropertyValidator<*, *>.inRange(
    min: Int,
    max: Int,
    positiveMessage: String = "should be in range of $min..$max",
    negativeMessage: String = "should not be in range of $min..$max",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = listOf(Number::class.java)

        override fun validate(value: Any?) =
            when (value) {
                is Int -> value in min..max
                is Long -> value in min..max
                is Float -> value >= min && value <= max
                is Double -> value >= min && value <= max
                else -> false
            }
    }

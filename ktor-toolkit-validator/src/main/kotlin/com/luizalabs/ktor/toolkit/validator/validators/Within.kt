package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Adds a validation rule to ensure a property value is within a specified duration from the current time.
 *
 * This rule validates whether the property value (of supported date/time types) lies within the allowed
 * range relative to the current time. It supports both positive and negated conditions. Supported types
 * include LocalDate, LocalDateTime, and Instant.
 *
 * @param duration The maximum allowed duration from the current time within which the property value must lie.
 * @param now The reference point in time to compare against. Defaults to the current system time (`Clock.System.now()`).
 * @param positiveMessage The error message used if the property fails validation in the non-negated context.
 *                        Defaults to "should be within $duration from now".
 * @param negativeMessage The error message used if the property fails validation in the negated context.
 *                        Defaults to "should not be within $duration from now".
 */
fun PropertyValidator<*, *>.within(
    duration: Duration,
    now: Instant = Clock.System.now(),
    positiveMessage: String = "should be within $duration from now",
    negativeMessage: String = "should not be within $duration from now",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> =
            listOf(
                LocalDateTime::class.java,
                LocalDate::class.java,
                Instant::class.java,
            )

        override fun validate(value: Any?): Boolean {
            val lower = now.minus(duration)
            val upper = now.plus(duration)

            return when (value) {
                is LocalDate -> {
                    val minDate = lower.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    val maxDate = upper.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    value in minDate..maxDate
                }

                is LocalDateTime -> {
                    val valueInst = value.toInstant(TimeZone.currentSystemDefault())
                    valueInst in lower..upper
                }

                is Instant -> {
                    value in lower..upper
                }

                else -> {
                    false
                }
            }
        }
    }

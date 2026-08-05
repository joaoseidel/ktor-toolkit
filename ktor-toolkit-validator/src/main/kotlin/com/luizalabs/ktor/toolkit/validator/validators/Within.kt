package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Adds a validation rule to ensure a property value is within a specified duration from the current time.
 *
 * The window is symmetric: the value must fall in `now - duration .. now + duration`. Supported
 * types are `LocalDate`, `LocalDateTime` and `Instant`; a `LocalDate` is compared at day granularity.
 *
 * @param duration The maximum allowed distance from [now] in either direction.
 * @param now The reference point in time to compare against. Defaults to the current system time.
 * @param timeZone The zone used to resolve zone-less values. Defaults to the system zone.
 * @param positiveMessage The error message used if the property fails validation in the non-negated context.
 *                        Defaults to "should be within $duration from now".
 * @param negativeMessage The error message used if the property fails validation in the negated context.
 *                        Defaults to "should not be within $duration from now".
 */
fun PropertyValidator<*, *>.within(
    duration: Duration,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    positiveMessage: String = "should be within $duration from now",
    negativeMessage: String = "should not be within $duration from now",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = TEMPORAL_TYPES

        override fun validate(value: Any?): Boolean {
            val lower = now.minus(duration)
            val upper = now.plus(duration)

            return if (value is LocalDate) {
                value in lower.toLocalDateTime(timeZone).date..upper.toLocalDateTime(timeZone).date
            } else {
                temporalToInstant(value, timeZone)?.let { it in lower..upper } ?: false
            }
        }
    }

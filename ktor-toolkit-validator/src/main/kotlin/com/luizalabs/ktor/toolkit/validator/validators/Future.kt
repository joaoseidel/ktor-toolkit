package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Adds a validation rule to ensure a property represents a future date or timestamp
 * relative to the current time. Optionally, a maximum future duration can be specified.
 * Applicable to `LocalDate`, `LocalDateTime` and `Instant`.
 *
 * A `LocalDate` is compared at day granularity, so today is never "in the future".
 *
 * @param duration Optional maximum duration in the future allowed for the value.
 * @param now Reference "current" time for comparison (default: `Clock.System.now()`).
 * @param timeZone The zone used to resolve zone-less values. Defaults to the system zone.
 * @param positiveMessage Error message for failure in non-negated context.
 * @param negativeMessage Error message for failure in negated context.
 */
fun PropertyValidator<*, *>.future(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    positiveMessage: String = "should be ${duration?.let { "a future date of at most $it" } ?: "a future date"}",
    negativeMessage: String = "should not be ${duration?.let { "a future date of at most $it" } ?: "a future date"}",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = TEMPORAL_TYPES

        override fun validate(value: Any?): Boolean {
            val upperBound = duration?.let { now.plus(it) }

            return when (value) {
                is LocalDate -> {
                    val today = now.toLocalDateTime(timeZone).date
                    val latest = upperBound?.toLocalDateTime(timeZone)?.date
                    value > today && (latest == null || value <= latest)
                }

                is LocalDateTime -> {
                    val current = now.toLocalDateTime(timeZone)
                    val latest = upperBound?.toLocalDateTime(timeZone)
                    value > current && (latest == null || value <= latest)
                }

                is Instant -> value > now && (upperBound == null || value <= upperBound)

                else -> false
            }
        }
    }

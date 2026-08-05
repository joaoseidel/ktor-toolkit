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
 * Applicable to LocalDate, LocalDateTime, and Instant.
 *
 * @param duration Optional maximum duration in the future allowed for the value.
 * @param now Reference "current" time for comparison (default: Clock.System.now()).
 * @param positiveMessage Error message for failure in non-negated context.
 * @param negativeMessage Error message for failure in negated context.
 */
fun PropertyValidator<*, *>.future(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    positiveMessage: String = "should be ${duration?.let { "a future date of at most $it" } ?: "a future date"}",
    negativeMessage: String = "should not be ${duration?.let { "a future date of at most $it" } ?: "a future date"}",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> =
            listOf(
                LocalDateTime::class.java,
                LocalDate::class.java,
                Instant::class.java,
            )

        override fun validate(value: Any?): Boolean {
            val upper = duration?.let { now.plus(it) }

            return when (value) {
                is LocalDate -> {
                    val todayUtc = now.toLocalDateTime(TimeZone.UTC).date
                    val maxDate = upper?.toLocalDateTime(TimeZone.UTC)?.date

                    if (upper != null) {
                        value > todayUtc && value <= maxDate!!
                    } else {
                        value > todayUtc
                    }
                }

                is LocalDateTime -> {
                    val minLdt = now.toLocalDateTime(TimeZone.UTC)
                    val maxLdt = upper?.toLocalDateTime(TimeZone.UTC)

                    if (upper != null) {
                        value > minLdt && value <= maxLdt!!
                    } else {
                        value > minLdt
                    }
                }

                is Instant -> {
                    if (upper != null) {
                        value > now && value <= upper
                    } else {
                        value > now
                    }
                }

                else -> {
                    false
                }
            }
        }
    }

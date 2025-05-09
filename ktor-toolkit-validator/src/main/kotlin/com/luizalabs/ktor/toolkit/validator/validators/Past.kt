package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Adds a validation rule to ensure that a property value represents a past date or time.
 * Optionally, a duration can be specified to limit how far in the past the value can be.
 *
 * @param duration The maximum allowed duration that the value can be in the past.
 *                 If null, any past date or time is allowed. Defaults to null.
 * @param now The reference point in time to compare the value against.
 *            Defaults to the current system time.
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. The default message is generated based
 *                        on whether a duration is specified.
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. The default message is generated based
 *                        on whether a duration is specified.
 */
fun PropertyValidator<*, *>.past(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    positiveMessage: String = "should be ${duration?.let { "a past date of at most $it" } ?: "a past date"}",
    negativeMessage: String = "should not be ${duration?.let { "a past date of at most $it" } ?: "a past date"}",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> =
            listOf(
                LocalDateTime::class.java,
                LocalDate::class.java,
                Instant::class.java,
            )

        override fun validate(value: Any?): Boolean {
            val beforeNow: Boolean
            val afterCutoff: Boolean

            return when (value) {
                is LocalDate -> {
                    val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
                    beforeNow = value < today

                    if (duration != null) {
                        val cutoffDate =
                            today.minus(duration.inWholeDays.toInt(), DateTimeUnit.DAY)
                        afterCutoff = value >= cutoffDate
                        beforeNow && afterCutoff
                    } else {
                        beforeNow
                    }
                }

                is LocalDateTime -> {
                    val nowLdt = now.toLocalDateTime(TimeZone.currentSystemDefault())
                    beforeNow = value < nowLdt

                    if (duration != null) {
                        val cutoffInstant = now.minus(duration)
                        val cutoffLdt = cutoffInstant.toLocalDateTime(TimeZone.currentSystemDefault())
                        afterCutoff = value >= cutoffLdt
                        beforeNow && afterCutoff
                    } else {
                        beforeNow
                    }
                }

                is Instant -> {
                    beforeNow = value < now

                    if (duration != null) {
                        val cutoffInstant = now.minus(duration)
                        afterCutoff = value >= cutoffInstant
                        beforeNow && afterCutoff
                    } else {
                        beforeNow
                    }
                }

                else -> {
                    false
                }
            }
        }
    }

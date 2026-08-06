package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule
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
 * Asserts that a date lies behind [now], and no further behind than [duration] if one is given.
 *
 * A [LocalDate] is compared at day granularity, so today is never "in the past".
 *
 * @param duration How far back the value may be. Unbounded when null.
 * @param now The reference point in time to compare against. Defaults to the current system time;
 * pass it explicitly to make a test deterministic.
 * @param timeZone The zone used to resolve zone-less values. Defaults to the system zone.
 */
@JvmName("pastLocalDate")
fun PropertyValidator<*, LocalDate?>.past(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDate?> = pastRule(duration, now, timeZone)

/** Asserts that a date and time lies behind [now]. See the [LocalDate] overload. */
@JvmName("pastLocalDateTime")
fun PropertyValidator<*, LocalDateTime?>.past(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDateTime?> = pastRule(duration, now, timeZone)

/** Asserts that an instant lies behind [now]. See the [LocalDate] overload. */
@JvmName("pastInstant")
fun PropertyValidator<*, Instant?>.past(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<Instant?> = pastRule(duration, now, timeZone)

private fun pastRule(
    duration: Duration?,
    now: Instant,
    timeZone: TimeZone,
): ValidationRule<Any?> {
    val described = if (duration != null) "a past date of at most $duration" else "a past date"

    return validationRule(
        positiveMessage = "should be $described",
        negativeMessage = "should not be $described",
    ) { value ->
        val cutoff = duration?.let { now.minus(it) }

        when (value) {
            is LocalDate -> {
                val today = now.toLocalDateTime(timeZone).date
                // Day granularity: the cutoff is expressed in whole days.
                val earliest = duration?.let { today.minus(it.inWholeDays.toInt(), DateTimeUnit.DAY) }
                value < today && (earliest == null || value >= earliest)
            }

            is LocalDateTime -> {
                val current = now.toLocalDateTime(timeZone)
                val earliest = cutoff?.toLocalDateTime(timeZone)
                value < current && (earliest == null || value >= earliest)
            }

            is Instant -> {
                value < now && (cutoff == null || value >= cutoff)
            }

            // Unreachable: the receiver of every `past` overload pins the type.
            else -> {
                false
            }
        }
    }
}

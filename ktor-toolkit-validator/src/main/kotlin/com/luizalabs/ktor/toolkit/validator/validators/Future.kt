package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import com.luizalabs.ktor.toolkit.validator.validationRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Asserts that a date lies ahead of [now], and no further ahead than [duration] if one is given.
 *
 * A [LocalDate] is compared at day granularity, so today is never "in the future".
 *
 * @param duration How far ahead the value may be. Unbounded when null.
 * @param now The reference point in time to compare against. Defaults to the current system time;
 * pass it explicitly to make a test deterministic.
 * @param timeZone The zone used to resolve zone-less values. Defaults to the system zone.
 */
@JvmName("futureLocalDate")
fun PropertyValidator<*, LocalDate?>.future(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDate?> = futureRule(duration, now, timeZone)

/** Asserts that a date and time lies ahead of [now]. See the [LocalDate] overload. */
@JvmName("futureLocalDateTime")
fun PropertyValidator<*, LocalDateTime?>.future(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDateTime?> = futureRule(duration, now, timeZone)

/** Asserts that an instant lies ahead of [now]. See the [LocalDate] overload. */
@JvmName("futureInstant")
fun PropertyValidator<*, Instant?>.future(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<Instant?> = futureRule(duration, now, timeZone)

private fun futureRule(
    duration: Duration?,
    now: Instant,
    timeZone: TimeZone,
): ValidationRule<Any?> {
    val described = duration?.let { "a future date of at most $it" } ?: "a future date"

    return validationRule(
        positiveMessage = "should be $described",
        negativeMessage = "should not be $described",
    ) { value ->
        val upperBound = duration?.let { now.plus(it) }

        when (value) {
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

            // Unreachable: the receiver of every `future` overload pins the type.
            else -> false
        }
    }
}

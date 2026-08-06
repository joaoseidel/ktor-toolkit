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
 * Asserts that a date falls no further than [duration] from [now], in either direction.
 *
 * The window is symmetric: `now - duration .. now + duration`. A [LocalDate] is compared at day
 * granularity.
 *
 * @param duration The largest accepted distance from [now].
 * @param now The reference point in time to compare against. Defaults to the current system time;
 * pass it explicitly to make a test deterministic.
 * @param timeZone The zone used to resolve zone-less values. Defaults to the system zone.
 */
@JvmName("withinLocalDate")
fun PropertyValidator<*, LocalDate?>.within(
    duration: Duration,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDate?> = withinRule(duration, now, timeZone)

/** Asserts that a date and time falls within [duration] of [now]. See the [LocalDate] overload. */
@JvmName("withinLocalDateTime")
fun PropertyValidator<*, LocalDateTime?>.within(
    duration: Duration,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDateTime?> = withinRule(duration, now, timeZone)

/** Asserts that an instant falls within [duration] of [now]. See the [LocalDate] overload. */
@JvmName("withinInstant")
fun PropertyValidator<*, Instant?>.within(
    duration: Duration,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<Instant?> = withinRule(duration, now, timeZone)

private fun withinRule(
    duration: Duration,
    now: Instant,
    timeZone: TimeZone,
): ValidationRule<Any?> =
    validationRule(
        positiveMessage = "should be within $duration from now",
        negativeMessage = "should not be within $duration from now",
    ) { value ->
        val lower = now.minus(duration)
        val upper = now.plus(duration)

        if (value is LocalDate) {
            value in lower.toLocalDateTime(timeZone).date..upper.toLocalDateTime(timeZone).date
        } else {
            temporalToInstant(value, timeZone)?.let { it in lower..upper } ?: false
        }
    }

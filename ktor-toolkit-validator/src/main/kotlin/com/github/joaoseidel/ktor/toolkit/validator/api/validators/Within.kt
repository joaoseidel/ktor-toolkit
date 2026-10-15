package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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
): ValidationRule<LocalDate?> =
    withinRule(duration) { value ->
        value in now.minus(duration).toLocalDateTime(timeZone).date..now.plus(duration).toLocalDateTime(timeZone).date
    }

/** Asserts that a date and time falls within [duration] of [now]. See the [LocalDate] overload. */
@JvmName("withinLocalDateTime")
fun PropertyValidator<*, LocalDateTime?>.within(
    duration: Duration,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDateTime?> = withinRule(duration) { it.toInstant(timeZone) in now.minus(duration)..now.plus(duration) }

/** Asserts that an instant falls within [duration] of [now]. See the [LocalDate] overload. */
@JvmName("withinInstant")
fun PropertyValidator<*, Instant?>.within(
    duration: Duration,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<Instant?> = withinRule(duration) { it in now.minus(duration)..now.plus(duration) }

private fun <V : Any> withinRule(
    duration: Duration,
    holds: (V) -> Boolean,
): ValidationRule<V?> =
    validationRule(
        positiveMessage = "should be within $duration from now",
        negativeMessage = "should not be within $duration from now",
        test = holds,
    )

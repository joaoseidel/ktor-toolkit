package com.github.joaoseidel.ktor.toolkit.validator.api.validators

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.api.validationRule
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
): ValidationRule<LocalDate?> =
    futureRule(duration) { value ->
        val today = now.toLocalDateTime(timeZone).date
        val latest = duration?.let { now.plus(it).toLocalDateTime(timeZone).date }
        value > today && (latest == null || value <= latest)
    }

/** Asserts that a date and time lies ahead of [now]. See the [LocalDate] overload. */
@JvmName("futureLocalDateTime")
fun PropertyValidator<*, LocalDateTime?>.future(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDateTime?> =
    futureRule(duration) { value ->
        val current = now.toLocalDateTime(timeZone)
        val latest = duration?.let { now.plus(it).toLocalDateTime(timeZone) }
        value > current && (latest == null || value <= latest)
    }

/** Asserts that an instant lies ahead of [now]. See the [LocalDate] overload. */
@JvmName("futureInstant")
fun PropertyValidator<*, Instant?>.future(
    duration: Duration? = null,
    now: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<Instant?> =
    futureRule(duration) { value ->
        val upperBound = duration?.let { now.plus(it) }
        value > now && (upperBound == null || value <= upperBound)
    }

private fun <V : Any> futureRule(
    duration: Duration?,
    holds: (V) -> Boolean,
): ValidationRule<V?> {
    val described = if (duration != null) "a future date of at most $duration" else "a future date"

    return validationRule(
        positiveMessage = "should be $described",
        negativeMessage = "should not be $described",
        test = holds,
    )
}

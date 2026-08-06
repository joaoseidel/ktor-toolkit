package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Asserts that a date is strictly after [date].
 *
 * Both sides are projected onto the absolute timeline before being compared, with a [LocalDate]
 * anchored at the start of its day in [timeZone], so the property and the reference need not share
 * a type. Because [date] is a sibling field as often as a constant, `target` is in scope:
 * `should be after(target.startsAt)`.
 *
 * @param date The point to compare against. A [LocalDate], [LocalDateTime] or [Instant]; anything
 * else fails when the rule is built.
 * @param timeZone The zone used to resolve zone-less values. Defaults to the system zone.
 */
@JvmName("afterLocalDate")
fun PropertyValidator<*, LocalDate?>.after(
    date: Any,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDate?> = afterRule(date, timeZone)

/** Asserts that a date and time is strictly after [date]. See the [LocalDate] overload. */
@JvmName("afterLocalDateTime")
fun PropertyValidator<*, LocalDateTime?>.after(
    date: Any,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<LocalDateTime?> = afterRule(date, timeZone)

/** Asserts that an instant is strictly after [date]. See the [LocalDate] overload. */
@JvmName("afterInstant")
fun PropertyValidator<*, Instant?>.after(
    date: Any,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): ValidationRule<Instant?> = afterRule(date, timeZone)

private fun afterRule(
    date: Any,
    timeZone: TimeZone,
): ValidationRule<Any?> {
    val reference = requireTemporal(date, timeZone)

    return validationRule(
        positiveMessage = "should be after $date",
        negativeMessage = "should not be after $date",
    ) { value -> temporalToInstant(value, timeZone)?.let { it > reference } ?: false }
}

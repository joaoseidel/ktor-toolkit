package com.luizalabs.ktor.toolkit.validator.validators

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Projects a supported temporal value onto the absolute timeline, so values of different types can
 * be compared against each other.
 *
 * A [LocalDate] is anchored at the start of its day in [timeZone].
 *
 * @return the corresponding [Instant], or `null` if [value] is not a supported temporal type.
 */
internal fun temporalToInstant(
    value: Any?,
    timeZone: TimeZone,
): Instant? =
    when (value) {
        is Instant -> value
        is LocalDateTime -> value.toInstant(timeZone)
        is LocalDate -> value.atStartOfDayIn(timeZone)
        else -> null
    }

/**
 * Resolves the reference point a comparison rule was given, failing fast if it is not a temporal
 * value.
 *
 * The reference of `before` and `after` is typed as [Any], because [LocalDate], [LocalDateTime] and
 * [Instant] share no supertype to constrain it to. Checking it here means a wrong type fails when
 * the rule is built — at application startup, where the validation block is declared — rather than
 * silently failing every request that reaches the rule.
 */
internal fun requireTemporal(
    date: Any,
    timeZone: TimeZone,
): Instant =
    requireNotNull(temporalToInstant(date, timeZone)) {
        "Expected a LocalDate, LocalDateTime or Instant to compare against, got ${date::class.simpleName}"
    }

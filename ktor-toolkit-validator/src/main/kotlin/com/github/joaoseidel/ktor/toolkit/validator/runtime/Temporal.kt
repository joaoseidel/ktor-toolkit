package com.github.joaoseidel.ktor.toolkit.validator.runtime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

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

internal fun requireTemporal(
    date: Any,
    timeZone: TimeZone,
): Instant =
    requireNotNull(temporalToInstant(date, timeZone)) {
        "Expected a LocalDate, LocalDateTime or Instant to compare against, got ${date::class.simpleName}"
    }

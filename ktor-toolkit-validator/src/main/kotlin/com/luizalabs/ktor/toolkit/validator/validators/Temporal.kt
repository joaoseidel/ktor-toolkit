package com.luizalabs.ktor.toolkit.validator.validators

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/** The date and time types every temporal validation rule in this package accepts. */
internal val TEMPORAL_TYPES: List<Class<*>> =
    listOf(
        LocalDateTime::class.java,
        LocalDate::class.java,
        Instant::class.java,
    )

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

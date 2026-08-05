package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import kotlinx.datetime.TimeZone

/**
 * Adds a validation rule to check if a property value is before a specified date.
 *
 * Supports `LocalDate`, `LocalDateTime` and `Instant` on either side, in any combination: both
 * values are projected onto the absolute timeline before being compared, with a `LocalDate`
 * anchored at the start of its day in [timeZone].
 *
 * @param date The date to compare with. Accepts `LocalDate`, `LocalDateTime` or `Instant`.
 * @param timeZone The zone used to resolve zone-less values. Defaults to the system zone.
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should be before $date".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not be before $date".
 */
fun PropertyValidator<*, *>.before(
    date: Any,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    positiveMessage: String = "should be before $date",
    negativeMessage: String = "should not be before $date",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> = TEMPORAL_TYPES

        override fun validate(value: Any?): Boolean {
            val instant = temporalToInstant(value, timeZone) ?: return false
            val reference = temporalToInstant(date, timeZone) ?: return false
            return instant < reference
        }
    }

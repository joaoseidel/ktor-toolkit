package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Adds a validation rule to check if a property value is after a specified date.
 *
 * This method validates whether the property value is after the given `date`. It supports
 * properties of type `LocalDate`, `LocalDateTime`, or `Instant`. The validation rule can also
 * include a negated condition to check if the value is not after the specified date.
 *
 * @param date The date to compare with. Accepts types `LocalDate`, `LocalDateTime`, or `Instant`.
 * @param positiveMessage The error message to be used if the property fails the validation
 *                        when the rule is not negated. Defaults to "should be after $date".
 * @param negativeMessage The error message to be used if the property fails the validation
 *                        when the rule is negated. Defaults to "should not be after $date".
 */
fun PropertyValidator<*, *>.after(
    date: Any,
    positiveMessage: String = "should be after $date",
    negativeMessage: String = "should not be after $date",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> =
            listOf(
                LocalDateTime::class.java,
                LocalDate::class.java,
                Instant::class.java,
            )

        override fun validate(value: Any?): Boolean =
            when (value) {
                is LocalDateTime if date is LocalDateTime -> {
                    value > date
                }

                is LocalDate if date is LocalDate -> {
                    value > date
                }

                is LocalDate if date is LocalDateTime -> {
                    value > date.date
                }

                is LocalDateTime if date is LocalDate -> {
                    val cutoff = date.atStartOfDayIn(TimeZone.currentSystemDefault())
                    value.toInstant(TimeZone.currentSystemDefault()) > cutoff
                }

                is Instant if date is Instant -> {
                    value > date
                }

                else -> {
                    false
                }
            }
    }

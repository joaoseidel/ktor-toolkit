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
 * Validação que garante que um valor ocorra antes de uma data/horário de referência.
 * Suporta LocalDate, LocalDateTime e Instant, e compara cruzado:
 * - LocalDateTime x LocalDateTime
 * - LocalDate x LocalDate
 * - LocalDate vs LocalDateTime
 * - LocalDateTime vs LocalDate
 *
 * @param date Instante ou data de referência.
 * @param positiveMessage Mensagem de erro no caso afirmativo.
 * @param negativeMessage Mensagem de erro no caso negado.
 */
fun PropertyValidator<*, *>.before(
    date: Any,
    positiveMessage: String = "should be before $date",
    negativeMessage: String = "should not be before $date",
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
                    value < date
                }

                is LocalDate if date is LocalDate -> {
                    value < date
                }

                is LocalDate if date is LocalDateTime -> {
                    val valueInstant = value.atStartOfDayIn(TimeZone.currentSystemDefault())
                    val dateInstant = date.toInstant(TimeZone.currentSystemDefault())
                    valueInstant < dateInstant
                }

                is LocalDateTime if date is LocalDate -> {
                    value.date < date
                }

                is Instant if date is Instant -> {
                    value < date
                }

                else -> {
                    false
                }
            }
    }

@file:OptIn(ExperimentalUuidApi::class)

package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationRule
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Adds a validation rule to ensure a property value is a valid UUID.
 *
 * This method validates whether the property value conforms to the standard UUID format. It can also validate
 * the negated condition, ensuring that the value should not be a valid UUID. The rule supports properties of
 * type [String] and [UUID].
 *
 * @param positiveMessage The error message to be used if the property fails the validation when the rule is not negated.
 *                        Defaults to "should be a valid UUID".
 * @param negativeMessage The error message to be used if the property fails the validation when the rule is negated.
 *                        Defaults to "should not be a valid UUID".
 */
fun PropertyValidator<*, *>.uuid(
    positiveMessage: String = "should be a valid UUID",
    negativeMessage: String = "should not be a valid UUID",
): ValidationRule =
    object : ValidationRule(positiveMessage, negativeMessage) {
        override fun supportedTypes(): List<Class<*>> =
            listOf(
                String::class.java,
                Uuid::class.java,
                UUID::class.java,
            )

        override fun validate(value: Any?) =
            try {
                UUID.fromString(value.toString())
                true
            } catch (e: IllegalArgumentException) {
                false
            }
    }

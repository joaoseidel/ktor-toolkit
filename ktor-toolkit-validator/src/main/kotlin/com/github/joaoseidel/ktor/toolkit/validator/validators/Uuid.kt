@file:OptIn(ExperimentalUuidApi::class)

package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Asserts that a string is a well-formed UUID.
 *
 * The same rule is offered on [UUID] and [Uuid] properties, where it can only hold — assert it
 * there when the property type might later be relaxed to a string.
 */
@JvmName("uuidOfString")
fun PropertyValidator<*, String?>.uuid(): ValidationRule<String?> = uuidRule()

/** Asserts that a [UUID] is well formed. Always holds; see the `String` overload. */
@JvmName("uuidOfJavaUuid")
fun PropertyValidator<*, UUID?>.uuid(): ValidationRule<UUID?> = uuidRule()

/** Asserts that a [Uuid] is well formed. Always holds; see the `String` overload. */
@JvmName("uuidOfKotlinUuid")
fun PropertyValidator<*, Uuid?>.uuid(): ValidationRule<Uuid?> = uuidRule()

private fun uuidRule(): ValidationRule<Any?> =
    validationRule(
        positiveMessage = "should be a valid UUID",
        negativeMessage = "should not be a valid UUID",
    ) { value ->
        when (value) {
            is UUID, is Uuid -> {
                true
            }

            else -> {
                try {
                    UUID.fromString(value.toString())
                    true
                } catch (_: IllegalArgumentException) {
                    false
                }
            }
        }
    }

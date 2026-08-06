package com.luizalabs.ktor.toolkit.validator.support

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationContext
import com.luizalabs.ktor.toolkit.validator.data.ValidationError

/**
 * A single-property object, so a rule can be exercised without inventing a request type per test.
 *
 * The property is called `value`, which is therefore the path every error in [errorsOf] and
 * [messagesOf] is recorded under.
 */
data class Holder<V>(
    val value: V,
)

/** Runs [block] against a validator over [value] and returns the errors it recorded. */
fun <V> errorsOf(
    value: V,
    block: PropertyValidator<Holder<V>, V>.() -> Unit,
): List<ValidationError> =
    ValidationContext(Holder(value))
        .apply { property(Holder<V>::value, block) }
        .errors

/** The messages of [errorsOf], for the common case where the path is not what is under test. */
fun <V> messagesOf(
    value: V,
    block: PropertyValidator<Holder<V>, V>.() -> Unit,
): List<String> = errorsOf(value, block).map { it.message }

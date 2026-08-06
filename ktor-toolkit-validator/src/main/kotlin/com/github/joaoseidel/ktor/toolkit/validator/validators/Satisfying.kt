package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.ValidationRule
import com.github.joaoseidel.ktor.toolkit.validator.validationRule

/**
 * Builds a one-off rule from a predicate, for a constraint no named rule covers.
 *
 * ```kotlin
 * property(CreateBookRequest::isbn) {
 *     should be satisfying("should be a valid ISBN") { it.isValidIsbn() }
 * }
 * ```
 *
 * The result is an ordinary [ValidationRule], so it negates with `should notBe` and composes with
 * `and` / `or` like any other. Applies to a property of any type; the predicate sees the value at
 * its declared type, and is not called at all when the property is absent.
 *
 * @param message The error recorded when the assertion fails.
 * @param predicate The condition the value has to meet.
 */
fun <V : Any> PropertyValidator<*, V?>.satisfying(
    message: String,
    predicate: (V) -> Boolean,
): ValidationRule<V?> =
    validationRule(
        positiveMessage = message,
        negativeMessage = message,
        test = predicate,
    )

package com.luizalabs.ktor.toolkit.validator.support

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.ValidationContext
import com.luizalabs.ktor.toolkit.validator.ValidationRule
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

/**
 * A validator over [value], so a rule factory can be called outside a `property` block — which is
 * what [appliedTo] needs, the rule itself rather than the outcome of asserting it.
 */
fun <V> validatorFor(value: V): PropertyValidator<Holder<V>, V> =
    PropertyValidator(Holder(value), path = "value", value = value, collected = mutableListOf())

/**
 * Applies this rule's predicate to a value of a type its receiver would never have allowed.
 *
 * [ValidationRule] is contravariant and its `test` is public, so a caller holding a rule can reach
 * the predicate past the receiver that normally pins the property type — `size()` is declared on
 * `PropertyValidator<*, String?>`, but the rule it returns is a `ValidationRule<Any?>` underneath.
 * Every rule answers `false` for a type it does not understand rather than throwing, and this is how
 * that guard gets exercised.
 */
@Suppress("UNCHECKED_CAST")
infix fun ValidationRule<*>.appliedTo(value: Any?): Boolean = (this as ValidationRule<Any?>).test(value)

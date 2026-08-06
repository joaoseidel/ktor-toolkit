package com.luizalabs.ktor.toolkit.validator

/**
 * A single validation rule: a predicate over a property value, plus the messages to record when it
 * does not hold.
 *
 * A rule is contravariant in [V], so a rule written against a supertype applies to every property
 * whose type it covers — `nil()`, a `ValidationRule<Any?>`, can be asserted on any property, and
 * `min(3)`, a `ValidationRule<Number?>`, on any numeric one.
 *
 * Rules are values: build them with [validationRule], compose them with `and` / `or` / `!`, and
 * reword them with `describedAs`. Which rules a property can be asserted against is decided by the
 * receiver each rule factory declares — see the `validators` package.
 *
 * @property positiveMessage Recorded when the rule is asserted with `should be` and does not hold.
 * @property negativeMessage Recorded when the rule is asserted with `should notBe` and does hold.
 * @property appliesToNull Whether this rule has an opinion about a `null` property value. Defaults
 * to `false`, so a rule such as `email()` stays silent on an absent optional field — combine it
 * with `should notBe nil()` to also require the field to be present. Rules whose whole purpose is
 * nullability (see `nil`) set it to `true`, and only those ever see `null` in [test].
 * @property test The predicate itself. It receives `null` only when [appliesToNull] is `true`.
 */
class ValidationRule<in V>(
    val positiveMessage: String,
    val negativeMessage: String,
    val appliesToNull: Boolean = false,
    val test: (V) -> Boolean,
)

/**
 * Builds a rule whose predicate only ever sees a present value.
 *
 * This is the factory nearly every rule wants: [ShouldScope] short-circuits on a `null` property
 * before the predicate runs, so [test] can take a non-null [V] and skip the null branch.
 *
 * @param positiveMessage Recorded when the rule is asserted with `should be` and does not hold.
 * @param negativeMessage Recorded when the rule is asserted with `should notBe` and does hold.
 * @param test The predicate, applied to a present value.
 */
fun <V : Any> validationRule(
    positiveMessage: String,
    negativeMessage: String,
    test: (V) -> Boolean,
): ValidationRule<V?> =
    ValidationRule(positiveMessage, negativeMessage) { value ->
        // Safe: `appliesToNull` is false, so ShouldScope never reaches a null value.
        test(value!!)
    }

/**
 * Requires both rules to hold.
 *
 * The composed messages read as one sentence: `blank() and size(max = 10)` reports
 * "should be blank and size should be between 0 and 10".
 */
infix fun <V> ValidationRule<V>.and(other: ValidationRule<V>): ValidationRule<V> =
    ValidationRule(
        positiveMessage = "$positiveMessage and ${other.positiveMessage}",
        negativeMessage = "$negativeMessage and ${other.negativeMessage}",
        appliesToNull = appliesToNull || other.appliesToNull,
    ) { value ->
        evaluate(value) && other.evaluate(value)
    }

/**
 * Applies a rule that may be sharing a composition with one that opts into `null`.
 *
 * A composition is asked about a `null` value as soon as *either* operand has an opinion about
 * absence, which would otherwise hand that value to an operand that does not expect it. A rule
 * without an opinion is vacuously satisfied instead, so `nil() and blank()` accepts an absent
 * value on the strength of `nil` alone.
 */
private fun <V> ValidationRule<V>.evaluate(value: V): Boolean = if (value == null && !appliesToNull) true else test(value)

/**
 * Requires at least one of the rules to hold.
 *
 * `should be (uuid() or blank())` accepts an identifier or nothing at all. Note the parentheses:
 * infix calls associate to the left, so `should be uuid() or blank()` would try to apply `or` to
 * the result of `be`.
 */
infix fun <V> ValidationRule<V>.or(other: ValidationRule<V>): ValidationRule<V> =
    ValidationRule(
        positiveMessage = "$positiveMessage or ${other.positiveMessage}",
        negativeMessage = "$negativeMessage or ${other.negativeMessage}",
        appliesToNull = appliesToNull || other.appliesToNull,
    ) { value ->
        evaluate(value) || other.evaluate(value)
    }

/**
 * Inverts a rule, swapping the two messages with it.
 *
 * `should be !blank()` and `should notBe blank()` are equivalent; the operator earns its keep
 * inside a composition, as in `should be (nil() or !blank())`.
 */
operator fun <V> ValidationRule<V>.not(): ValidationRule<V> =
    ValidationRule(
        positiveMessage = negativeMessage,
        negativeMessage = positiveMessage,
        appliesToNull = appliesToNull,
    ) { value ->
        !test(value)
    }

/**
 * Returns a copy of this rule that reports [message] however it is asserted.
 *
 * Use it when composing, where there is no applied rule to reword yet:
 * `should be (email() or blank() describedAs "should be an email address, or left out")`.
 * To reword a single applied rule, prefer the [RuleOutcome] form — `should be email() describedAs "…"`
 * needs no parentheses.
 */
infix fun <V> ValidationRule<V>.describedAs(message: String): ValidationRule<V> = ValidationRule(message, message, appliesToNull, test)

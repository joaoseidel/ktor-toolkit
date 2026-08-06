package com.github.joaoseidel.ktor.toolkit.validator

/**
 * Applies validation rules to the property a [PropertyValidator] holds.
 *
 * Rules are asserted through [be] and [notBe], which read as `should be email()` and
 * `should notBe nil()`. Both record an error on the owning validator when the assertion fails, and
 * return the [RuleOutcome] that lets the error be reworded.
 *
 * @param T The type of the object that owns the value.
 * @param V The type of the value being validated.
 */
@ValidationDsl
class ShouldScope<T, out V> internal constructor(
    private val validator: PropertyValidator<T, V>,
) {
    /**
     * Asserts that [rule] holds, recording its `positiveMessage` when it does not.
     *
     * @param rule The rule to assert. Only rules whose receiver accepts [V] can be named here.
     */
    infix fun be(rule: ValidationRule<V>): RuleOutcome = assert(rule, negate = false)

    /**
     * Asserts that [rule] does not hold, recording its `negativeMessage` when it does.
     *
     * @param rule The rule to assert against. Only rules whose receiver accepts [V] can be named here.
     */
    infix fun notBe(rule: ValidationRule<V>): RuleOutcome = assert(rule, negate = true)

    private fun assert(
        rule: ValidationRule<V>,
        negate: Boolean,
    ): RuleOutcome {
        // An absent optional field is nothing to say, unless the rule is about absence itself.
        if (validator.value == null && !rule.appliesToNull) return RuleOutcome(validator, errorIndex = null)

        val holds = rule.test(validator.value)
        if (holds != negate) return RuleOutcome(validator, errorIndex = null)

        val message = if (negate) rule.negativeMessage else rule.positiveMessage
        return RuleOutcome(validator, errorIndex = validator.addError(message))
    }
}

/**
 * The result of asserting one rule, and the handle `describedAs` uses to reword it.
 *
 * @property errorIndex Where the recorded error sits in the shared error list, or `null` when the
 * assertion held and nothing was recorded.
 */
class RuleOutcome internal constructor(
    private val validator: PropertyValidator<*, *>,
    private val errorIndex: Int?,
) {
    /**
     * Replaces the message of the error this assertion recorded, if it recorded one.
     *
     * ```kotlin
     * should be email() describedAs "should be a work email address"
     * ```
     *
     * No parentheses are needed: infix calls associate to the left, so `describedAs` is applied to
     * the outcome of `be` rather than to the rule.
     */
    infix fun describedAs(message: String) {
        errorIndex?.let { validator.replaceError(it, message) }
    }
}

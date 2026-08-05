package com.luizalabs.ktor.toolkit.validator

/**
 * A scoped mechanism for applying validation rules to a property.
 *
 * This class provides a structured way to assert validation rules
 * on properties of a target object using a fluent API. It integrates
 * with a [PropertyValidator] instance to evaluate rules and collect
 * validation errors.
 *
 * The validation rules are applied through the [be] and [notBe] functions,
 * which allow the user to specify the expected validity or invalidity of
 * a given rule on the property.
 *
 * @param T The type of the target object being validated.
 * @param V The type of the specific property being validated.
 * @property validator The [PropertyValidator] instance managing the property and validation logic.
 */
class ShouldScope<T, V>(
    private val validator: PropertyValidator<T, V>,
) {
    /**
     * Applies the specified validation rule to the property being validated.
     *
     * This function uses the provided [ValidationRule] to validate the property
     * managed by the current instance of [ShouldScope]. The validation is performed
     * without negation, meaning the rule is expected to hold true. If the rule fails,
     * an error message is added to the validator's error list.
     *
     * @param rule The [ValidationRule] to be applied to the property. Examples of rules
     * include checking if a value is null, blank, or falls within a certain range.
     */
    infix fun be(rule: ValidationRule): PropertyValidator<*, *> = rule.apply(validator, false)

    /**
     * Applies the specified validation rule to the property being validated with negation.
     *
     * This function evaluates a [ValidationRule] against the property managed by the current
     * instance of [ShouldScope]. The validation is performed with negation, meaning the rule
     * is expected not to hold true. If the rule is satisfied (when it is not supposed to be),
     * an error message is added to the validator's error list.
     *
     * @param rule The [ValidationRule] to be applied to the property in a negated context.
     * Examples of rules include verifying that a value is not null, not blank, or does not
     * fall within a specific range.
     */
    infix fun notBe(rule: ValidationRule): PropertyValidator<*, *> = rule.apply(validator, true)
}

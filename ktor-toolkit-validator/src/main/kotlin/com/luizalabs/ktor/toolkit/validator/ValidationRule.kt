package com.luizalabs.ktor.toolkit.validator

/**
 * Represents a rule for validating properties of an object.
 *
 * This abstract class defines the contract for implementing property validation
 * rules. A rule can be applied to a property via an associated [PropertyValidator],
 * either as is or negated, to assert specific conditions on the property.
 *
 * Rules implementing this class should encapsulate the specific logic for
 * validating a property and may add validation errors to the [PropertyValidator]'s
 * error list when the rule fails.
 */
abstract class ValidationRule(
    open val positiveMessage: String = "should be valid",
    open val negativeMessage: String = "should not be valid",
) {
    /**
     * Whether this rule has an opinion about a `null` property value.
     *
     * Defaults to `false`, so a rule such as `email()` stays silent on an absent optional field —
     * combine it with `should notBe nil()` to also require the field to be present. Rules whose
     * whole purpose is nullability (see `nil`) override this to `true`.
     */
    protected open val appliesToNull: Boolean get() = false

    /**
     * Applies a validation rule to a property validator with optional negation.
     *
     * This function evaluates the specified validation logic encapsulated within
     * the given [PropertyValidator] instance. The rule can be applied in a negated
     * context if [negate] is set to `true`. Depending on the evaluation result,
     * validation errors may be added to the [PropertyValidator].
     *
     * @param validator The [PropertyValidator] instance responsible for managing property validation
     *                  logic, including the target object, property, and validation errors.
     * @param negate A boolean flag indicating whether the validation rule should be applied
     *               in a negated context. If `true`, the rule logic is negated.
     * @return The updated [PropertyValidator] instance after applying the validation rule.
     */
    fun apply(
        validator: PropertyValidator<*, *>,
        negate: Boolean,
    ): PropertyValidator<*, *> {
        val value = validator.propertyValue
        val supportedTypes = supportedTypes()

        // A null value carries no type to check. Rules that care about absence (`nil`) opt in via
        // `appliesToNull`; everything else treats an absent optional field as nothing to say.
        if (value == null) {
            if (!appliesToNull) return validator

            val isNullValid = validate(null)
            if (negate == isNullValid) {
                validator.addError(if (negate) negativeMessage else positiveMessage)
            }
            return validator
        }

        val isCompatibleType = supportedTypes.isEmpty() || supportedTypes.any { it.isInstance(value) }

        if (!isCompatibleType) {
            val typesMessage =
                if (supportedTypes.size == 1) {
                    "should be of type ${supportedTypes.first().simpleName}"
                } else {
                    "should be one of these types: ${supportedTypes.joinToString(", ") { it.simpleName }}"
                }

            validator.addError(typesMessage)
            return validator
        }

        val isValid = validate(value)

        if (negate == isValid) {
            validator.addError(if (negate) negativeMessage else positiveMessage)
        }

        return validator
    }

    /**
     * Returns a list of Java classes that this validator supports.
     *
     * This method should return all the types that can be properly validated
     * by this validation rule. These types are used in error messages when
     * type validation fails. Return an empty list if the validator accepts any type.
     *
     * @return A list of Class objects representing the supported types.
     */
    abstract fun supportedTypes(): List<Class<*>>

    /**
     * Performs the actual validation logic on the provided value.
     *
     * This method contains the core validation logic specific to each validation rule.
     * It should return true if the value passes validation, or false if it fails.
     *
     * @param value The value to validate. The implementation should handle type casting.
     * @return true if validation passes, false if validation fails.
     */
    abstract fun validate(value: Any?): Boolean
}

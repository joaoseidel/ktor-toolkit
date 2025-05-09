package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import com.luizalabs.ktor.toolkit.validator.validators.nil
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.reflect.KProperty1

/**
 * Represents a validation context for a given target object.
 *
 * The [ValidationContext] class provides a framework for implementing validation
 * logic on objects of type [T]. It allows the addition of validation rules for individual
 * properties and nested objects, as well as collecting and managing validation errors.
 *
 * @param T The type of the object being validated.
 * @property target The object being validated.
 */
class ValidationContext<T>(
    private val target: T,
) {
    private val errors = mutableListOf<ValidationError>()

    /**
     * Adds a validation block for a specific property within the target object.
     *
     * This method allows specifying validation rules for an individual property of the target object.
     * It creates a [com.luizalabs.ktor.toolkit.validator.PropertyValidator] for the given property and applies the provided validation logic
     * defined in the [block]. Any validation errors encountered during the validation process are collected
     * into the context's list of errors.
     *
     * @param prop The property to be validated, represented as a [KProperty1].
     * @param block The validation logic to be applied for the property using a [com.luizalabs.ktor.toolkit.validator.PropertyValidator].
     * @return The [com.luizalabs.ktor.toolkit.validator.PropertyValidator] instance configured for the specified property.
     */
    fun <V> property(
        prop: KProperty1<T, V>,
        block: PropertyValidator<T, V>.() -> Unit,
    ): PropertyValidator<T, V> = PropertyValidator(target, prop, errors).apply(block)

    /**
     * Executes validation logic on a nested property of the target object.
     *
     * This method enables recursive validation of a nested property specified by [prop].
     * It creates a new [ValidationContext] for the nested property's value, applies the
     * validation logic defined in [block], and propagates any validation errors back to the
     * current context with an updated property path reflecting the nested structure.
     *
     * @param R The type of the nested property's value.
     * @param prop The reference to the nested property to be validated.
     * @param block The validation logic to be applied to the nested property's value.
     */
    fun <R : Any> nested(
        prop: KProperty1<T, R?>,
        positiveMessage: String = "should be null",
        negativeMessage: String = "should not be null",
        block: ValidationContext<R>.() -> Unit,
    ) {
        val nestedValue = prop.get(target)

        if (nestedValue == null) {
            PropertyValidator(target, prop, errors).apply {
                should notBe nil(positiveMessage, negativeMessage)
            }
            return
        }

        val nestedContext = ValidationContext(nestedValue)
        nestedContext.apply(block)

        val basePath = prop.name
        nestedContext.getErrors().forEach { error ->
            errors.add(ValidationError("$basePath.${error.propertyPath}", error.message))
        }
    }

    /**
     * Retrieves the list of validation errors collected in the context.
     *
     * This method returns all validation errors that were registered during the
     * validation process. Each error contains a description of the issue and the
     * property path where the error occurred.
     *
     * @return A list of [ValidationError] objects representing the validation errors.
     */
    fun getErrors(): List<ValidationError> = errors

    /**
     * Indicates whether there are any validation errors in the current context.
     *
     * This property evaluates to `true` if the `errors` list is not empty, meaning that
     * at least one validation error has been recorded. Otherwise, it evaluates to `false`.
     * It provides a quick way to check the presence of validation errors.
     */
    val hasErrors: Boolean get() = errors.isNotEmpty()

    /**
     * Validates the current state of the validation context and determines the overall validation result.
     *
     * This method checks the list of collected validation errors. If no errors are present,
     * it returns a valid result. Otherwise, it returns an invalid result containing the reasons
     * for the validation failure.
     *
     * @return A [ValidationResult.Valid] if no validation errors are found, or a [ValidationResult.Invalid]
     *         with the corresponding error reasons if validation errors are present.
     */
    fun validateResult(): ValidationResult {
        val errors = getErrors().ifEmpty { return ValidationResult.Valid }
        return ValidationResult.Invalid(reasons = errors.map { error -> error.toString() })
    }
}

package com.luizalabs.ktor.toolkit.validator

/**
 * Interface for validating request objects of a specified type.
 *
 * This interface defines a contract for implementing validation logic
 * on request objects by using a validation context. Validation rules
 * can be defined inside the `validate` method, allowing specific constraints
 * to be applied to the properties of the target object.
 *
 * The validation process leverages the [com.luizalabs.ktor.toolkit.validator.ValidationContext] to collect errors
 * and provide a structured mechanism for validating object properties and nested
 * objects. Implementations of this interface should specify the validation rules
 * inside the `validate` method.
 *
 * @param T The type of the object to be validated.
 */
interface RequestValidator<T> {
    /**
     * Defines the validation logic for the target object within the context of a [com.luizalabs.ktor.toolkit.validator.ValidationContext].
     *
     * This extension function is designed to be implemented by classes conforming to the [RequestValidator]
     * interface. It allows the specification of validation rules applied to the properties of the target object.
     *
     * The validation process leverages the [com.luizalabs.ktor.toolkit.validator.ValidationContext] for accessing the target object and accumulating
     * validation errors. Implementations can define property-level and nested object-level validations
     * within this function.
     *
     * For property validations, [com.luizalabs.ktor.toolkit.validator.ValidationContext.property] can be used to specify constraints for individual
     * properties of the target object. For nested object validations, [com.luizalabs.ktor.toolkit.validator.ValidationContext.nested] provides a way
     * to validate properties of nested objects recursively.
     *
     * Any validation errors encountered during this process should be collected into the [com.luizalabs.ktor.toolkit.validator.ValidationContext],
     * which can later be retrieved and processed by the caller.
     *
     * This method should not return any value. Instead, all validation feedback should be handled via the
     * [com.luizalabs.ktor.toolkit.validator.ValidationContext].
     *
     * @receiver The [com.luizalabs.ktor.toolkit.validator.ValidationContext] associated with the target object of type [T].
     */
    fun ValidationContext<T>.validate()
}

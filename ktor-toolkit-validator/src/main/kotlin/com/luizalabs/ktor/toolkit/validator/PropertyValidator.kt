package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import kotlin.reflect.KProperty1

/**
 * Validates a property of a target object and tracks validation errors.
 *
 * This class encapsulates the logic for validating a specific property of a target
 * object. It provides a mechanism to associate validation errors with the property
 * when certain conditions fail. The validation process can be customized using the
 * `should` property, which holds a scoped mechanism for applying validation rules.
 *
 * @param T The type of the target object being validated.
 * @param V The type of the property being validated.
 * @property target The target object containing the property to validate.
 * @property property A reference to the property being validated.
 * @property errors A mutable list to store validation errors encountered during validation.
 */
class PropertyValidator<T, V>(
    val target: T,
    val property: KProperty1<T, V>,
    val errors: MutableList<ValidationError>,
) {
    internal val propertyPath = property.name
    internal val propertyValue: V = property.get(target)

    internal fun addError(message: String) {
        errors.add(ValidationError(propertyPath, message))
    }

    /**
     * Provides a fluent API for specifying validation rules on a property.
     *
     * This property serves as an entry point to the `ShouldScope` for the current
     * property being validated. The `should` property allows for chaining validation
     * rules using a natural language style, leveraging the `be` and `notBe` methods
     * of `ShouldScope` to define validation logic. Any violations of the specified
     * rules are tracked and stored in the associated `PropertyValidator` instance.
     */
    val should = ShouldScope(this)
}

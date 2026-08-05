package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import kotlin.reflect.KProperty1

/**
 * Validates a property of a target object and tracks validation errors.
 *
 * This class encapsulates the logic for validating a specific property of a target
 * object. It provides a mechanism to associate validation errors with the property
 * when certain conditions fail. The validation process can be customized using the
 * [should] property, which holds a scoped mechanism for applying validation rules.
 *
 * Instances are created by [ValidationContext.property]; the type itself is public because it is
 * the receiver of every validation rule.
 *
 * @param T The type of the target object being validated.
 * @param V The type of the property being validated.
 * @property target The target object containing the property to validate.
 * @property property A reference to the property being validated.
 */
class PropertyValidator<T, V> internal constructor(
    val target: T,
    val property: KProperty1<T, V>,
    private val collected: MutableList<ValidationError>,
) {
    /** The errors recorded so far, shared with the owning [ValidationContext]. */
    val errors: List<ValidationError> get() = collected

    internal val propertyPath = property.name
    internal val propertyValue: V = property.get(target)

    internal fun addError(message: String) {
        collected.add(ValidationError(propertyPath, message))
    }

    /**
     * Provides a fluent API for specifying validation rules on a property.
     *
     * This property serves as an entry point to the [ShouldScope] for the current
     * property being validated, allowing rules to be expressed as `should be email()`
     * or `should notBe nil()`. Any violation is recorded on this validator.
     */
    val should = ShouldScope(this)
}

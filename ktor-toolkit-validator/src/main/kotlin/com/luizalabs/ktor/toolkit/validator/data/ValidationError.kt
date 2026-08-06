package com.luizalabs.ktor.toolkit.validator.data

import kotlinx.serialization.Serializable

/**
 * Represents a validation error associated with a specific property.
 *
 * A validation error provides details about an issue encountered during
 * the validation of an object or a particular property. It includes the
 * property path where the error occurred and a descriptive message about
 * the nature of the error.
 *
 * @property propertyPath The path to the property where the validation error occurred. Empty for an
 * error that belongs to the object as a whole, such as one recorded by
 * [com.luizalabs.ktor.toolkit.validator.ValidationContext.invariant].
 * @property message A descriptive message explaining the validation error.
 */
@Serializable
data class ValidationError(
    val propertyPath: String,
    val message: String,
) {
    // An object-level error has no property to quote, so it renders as the bare message.
    override fun toString() = if (propertyPath.isEmpty()) message else "`$propertyPath` $message"
}

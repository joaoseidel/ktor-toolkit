package com.github.joaoseidel.ktor.toolkit.validator.data

import kotlinx.serialization.Serializable

/**
 * One thing found wrong with the object under validation, and where.
 *
 * [toString] is the wire form: the reason strings a
 * [io.ktor.server.plugins.requestvalidation.ValidationResult.Invalid] carries are these, and the
 * problem-details module parses the backquoted path back out of them to key its response.
 *
 * @property propertyPath The path to the offending property, such as `title`, `publisher.name` or
 * `tags[0]`. Empty for an error that belongs to the object as a whole, such as one recorded by
 * [com.github.joaoseidel.ktor.toolkit.validator.ValidationContext.invariant].
 * @property message What is wrong, phrased to follow the path — "should not be blank".
 */
@Serializable
data class ValidationError(
    val propertyPath: String,
    val message: String,
) {
    // An object-level error has no property to quote, so it renders as the bare message.
    override fun toString(): String = if (propertyPath.isEmpty()) message else "`$propertyPath` $message"
}

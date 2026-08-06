package com.luizalabs.ktor.toolkit.validator

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

/**
 * Configures validation logic for requests of type [T] within the [RequestValidationConfig].
 *
 * This function allows defining custom validation rules for incoming requests by providing
 * a [ValidationContext] for the specified type [T]. Validation rules can be added using the
 * supplied [block], which operates on the [ValidationContext]. Errors encountered during the
 * validation process are collected and converted into a [ValidationResult.Invalid] object.
 * If no errors are found, a [ValidationResult.Valid] is returned.
 *
 * @param T The type of the request object to be validated.
 * @param block A lambda defining the validation rules for the request of type [T] using the [ValidationContext].
 */
inline fun <reified T : Any> RequestValidationConfig.withValidationContext(noinline block: ValidationContext<T>.() -> Unit) =
    validate<T> { request ->
        val context = ValidationContext(target = request)
        context.block()
        return@validate context.toValidationResult()
    }

/**
 * Applies a validation process using a provided [RequestValidator] implementation.
 *
 * This method enables the validation of request objects based on custom validation logic
 * defined in a [RequestValidator] for the specified type [T]. The validation is performed
 * within a [ValidationContext], where errors are collected during the validation process.
 * The result of the validation is either [ValidationResult.Valid] if there are no errors,
 * or [ValidationResult.Invalid] containing a list of validation error messages.
 *
 * @param T The type of the object to be validated.
 * @param validator The [RequestValidator] implementation containing the validation logic for the target object of type [T].
 */
inline fun <reified T : Any> RequestValidationConfig.withValidationContext(validator: RequestValidator<T>) =
    validate<T> { request ->
        val context = ValidationContext(target = request)
        with(receiver = validator) { context.validate() }
        return@validate context.toValidationResult()
    }

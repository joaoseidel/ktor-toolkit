package com.github.joaoseidel.ktor.toolkit.validator

import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.plugins.requestvalidation.ValidationResult

/**
 * Declares the validation rules for requests of type [T].
 *
 * ```kotlin
 * install(RequestValidation) {
 *     rulesFor<CreateBookRequest> {
 *         property(CreateBookRequest::title) { should notBe blank() }
 *     }
 * }
 * ```
 *
 * Every error [block] collects becomes a reason on a [ValidationResult.Invalid]; a request that
 * breaks no rule is [ValidationResult.Valid]. For anything beyond a couple of fields, put the rules
 * in a [RequestValidator] and declare them with [rulesFrom].
 *
 * @param T The type of the request object to validate.
 * @param block The rules to assert on it, in a [ValidationContext].
 */
inline fun <reified T : Any> RequestValidationConfig.rulesFor(noinline block: ValidationContext<T>.() -> Unit) {
    validate<T> { request ->
        val context = ValidationContext(target = request)
        context.block()
        return@validate context.toValidationResult()
    }
}

/**
 * Takes the validation rules for requests of type [T] from a [RequestValidator].
 *
 * ```kotlin
 * install(RequestValidation) {
 *     rulesFrom(CreateBookValidator())
 * }
 * ```
 *
 * The rules run exactly as the [rulesFor] block would; keeping them in a class is what makes them
 * testable on their own, and reusable across the routes that accept the same body. [T] is inferred
 * from the validator, so it is never named twice.
 *
 * @param T The type of the request object to validate.
 * @param validator The validator the rules come from.
 */
inline fun <reified T : Any> RequestValidationConfig.rulesFrom(validator: RequestValidator<T>) {
    validate<T> { request ->
        val context = ValidationContext(target = request)
        with(receiver = validator) { context.validate() }
        return@validate context.toValidationResult()
    }
}

package com.github.joaoseidel.ktor.toolkit.validator

/**
 * The rules for one request type, in a class of their own.
 *
 * The alternative is a `rulesFor<T> { }` block at the install site, which is the right shape for a
 * couple of fields. Move to a validator once the rules outgrow that: a class is testable without a
 * server — construct it, run it over a [ValidationContext], assert on `errors` — and one validator
 * serves every route that accepts the same body.
 *
 * ```kotlin
 * class CreateBookValidator : RequestValidator<CreateBookRequest> {
 *     override fun ValidationContext<CreateBookRequest>.validate() {
 *         property(CreateBookRequest::title) { should notBe blank() }
 *         nested(CreateBookRequest::publisher) {
 *             property(Publisher::name) { should notBe blank() }
 *         }
 *     }
 * }
 *
 * install(RequestValidation) { rulesFrom(CreateBookValidator()) }
 * ```
 *
 * Implementations must be safe to share: [rulesFrom] registers one instance and every request runs
 * against it concurrently, so a validator holding mutable state of its own will interleave. The
 * per-request state lives in the [ValidationContext] the receiver hands you.
 *
 * @param T The type of the object to be validated.
 */
interface RequestValidator<T> {
    /**
     * Asserts this validator's rules, recording what fails on the receiving context.
     *
     * Errors are collected rather than thrown: keep going after a failure so one request reports
     * everything wrong with it, which is what [ValidationContext.toValidationResult] then turns
     * into a single 400. Reach the object itself through [ValidationContext.target].
     *
     * @receiver The context holding the object under validation and the errors found on it.
     */
    fun ValidationContext<T>.validate()
}

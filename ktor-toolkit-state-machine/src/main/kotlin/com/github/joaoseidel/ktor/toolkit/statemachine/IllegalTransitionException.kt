package com.github.joaoseidel.ktor.toolkit.statemachine

/**
 * Raised by [StateMachine.fireOrThrow] when the machine refuses an event.
 *
 * Deliberately a plain domain exception: it carries no HTTP status, because a `-core` type that
 * picks status codes has become a web layer. Choose the status once, in the adapter, where
 * `problemDetails { }` can see it:
 *
 * ```kotlin
 * problemDetails {
 *     on<IllegalTransitionException> { HttpStatusCode.Conflict }
 * }
 * ```
 *
 * `409 Conflict` is nearly always right — the request was well formed, and it disagreed with the
 * state the resource is actually in.
 *
 * The properties are typed `Any?` because Kotlin forbids a generic [Throwable]; the machine's own
 * `S` and `E` cannot survive into the exception. Read [reason] rather than parsing the message.
 *
 * @property from The state the subject was in.
 * @property event The event that was refused.
 * @property reason Why it was refused.
 */
class IllegalTransitionException(
    val from: Any?,
    val event: Any?,
    val reason: RejectionReason,
) : RuntimeException("cannot fire $event in state $from, because ${reason.message}")

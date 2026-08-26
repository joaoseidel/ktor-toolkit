package com.github.joaoseidel.ktor.toolkit.statemachine

/**
 * What came of firing an event: the state to move to, or the reason nothing moved.
 *
 * A rejection is an ordinary outcome rather than a failure — asking a machine for a move it will
 * not make is how a caller finds out. Handle both arms, or use
 * [StateMachine.fireOrThrow] where a rejection is always an error.
 *
 * @param S The state type.
 * @param E The event type.
 * @property from The state the subject was in when the event was fired.
 * @property event The event that was fired.
 */
sealed interface TransitionResult<out S, out E> {
    val from: S
    val event: E

    /**
     * The event was accepted; every effect has already run.
     *
     * The subject is untouched — applying [to] is the caller's job, in the same unit of work that
     * persists it.
     *
     * @property to The state to move to.
     */
    data class Accepted<out S, out E>(
        override val from: S,
        override val event: E,
        val to: S,
    ) : TransitionResult<S, E>

    /**
     * The event was refused; no effect ran and nothing changed.
     *
     * @property reason Why the machine refused it.
     */
    data class Rejected<out S, out E>(
        override val from: S,
        override val event: E,
        val reason: RejectionReason,
    ) : TransitionResult<S, E>
}

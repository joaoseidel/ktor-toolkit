package com.github.joaoseidel.ktor.toolkit.statemachine

/**
 * Why a machine refused an event.
 *
 * There are only two ways to be refused, and they mean different things to a caller: the move does
 * not exist at all, or it exists and this subject may not make it right now. The first is usually a
 * client that guessed a URL; the second is a client that acted on a stale view of the resource.
 */
sealed interface RejectionReason {
    /** Why the transition did not happen, phrased to complete "…, because …". */
    val message: String

    /**
     * No transition is declared from the current state for the event that was fired.
     *
     * The machine says nothing about whether the event would be legal somewhere else — it is not a
     * move that exists here.
     */
    data object NoTransition : RejectionReason {
        override val message: String = "no transition is declared for that event"
    }

    /**
     * A transition exists, but one of its guards said no.
     *
     * @property guard The reason the failing guard was declared with, such as
     * `"must have at least one line"`. It is written to be shown to whoever made the request.
     */
    data class GuardFailed(
        val guard: String,
    ) : RejectionReason {
        override val message: String get() = guard
    }
}

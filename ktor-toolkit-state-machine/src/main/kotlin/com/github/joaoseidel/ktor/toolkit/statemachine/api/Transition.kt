package com.github.joaoseidel.ktor.toolkit.statemachine.api

import com.github.joaoseidel.ktor.toolkit.statemachine.runtime.Guard
import kotlin.reflect.KClass

/**
 * One legal move: an event that takes a subject from one state to another.
 *
 * Transitions are compiled once, when the machine is built, and are immutable afterwards. They are
 * handed back by [StateMachine.transitionsFrom] and [StateMachine.availableFor] so that a caller
 * can describe the moves rather than only make them — which is what turns them into links.
 *
 * @param S The state type.
 * @param E The event type.
 * @param C The subject type.
 * @property from The state this move starts in.
 * @property to The state it arrives at.
 * @property event The event class that triggers it. Matching is by [KClass.isInstance], so a
 * transition declared on a sealed parent covers every subtype.
 * @property rel How the move relates to the subject, such as `"pay"`. Defaults to the event's
 * decapitalized simple name and is what the HATEOAS bridge publishes as a link relation.
 */
class Transition<S : Any, E : Any, C> internal constructor(
    val from: S,
    val to: S,
    val event: KClass<out E>,
    val rel: String,
    internal val guards: List<Guard<C>>,
    internal val effects: List<suspend (C, E) -> Unit>,
    internal val exits: List<suspend (C, E) -> Unit>,
) {
    override fun toString(): String = "$from --$rel--> $to"
}

package com.github.joaoseidel.ktor.toolkit.statemachine

import java.util.ArrayDeque

/**
 * The legal moves of one aggregate's lifecycle, compiled and checked.
 *
 * Build one with [stateMachine]. The result is immutable and holds no per-subject state, so declare
 * it once as a top-level `val` beside the aggregate it describes and share it across every request:
 *
 * ```kotlin
 * val orderFlow = stateMachine<OrderState, OrderEvent, Order> {
 *     initial(DRAFT)
 *     final(SHIPPED, CANCELLED)
 *
 *     state(DRAFT) {
 *         on<Place>(PLACED) {
 *             guard("must have at least one line") { it.lines.isNotEmpty() }
 *         }
 *     }
 *     state(PLACED) {
 *         on<Pay>(PAID)
 *         on<Cancel>(CANCELLED)
 *     }
 *     state(PAID) { on<Ship>(SHIPPED) }
 * }
 * ```
 *
 * The machine never touches the subject. [fire] reports the state to move to and the caller applies
 * it, which keeps entities immutable and leaves persistence in one place:
 *
 * ```kotlin
 * when (val result = orderFlow.fire(order, order.state, Pay)) {
 *     is Accepted -> repository.save(order.copy(state = result.to))
 *     is Rejected -> log.info { result.reason.message }
 * }
 * ```
 *
 * @param S The state type — an enum in most services.
 * @param E The event type, usually a sealed hierarchy.
 * @param C The subject the moves are about, such as the aggregate itself.
 * @property initialState Where a freshly created subject starts.
 * @property states Every state the machine knows, declared or final.
 */
class StateMachine<S : Any, E : Any, C> internal constructor(
    val initialState: S,
    val states: Set<S>,
    private val finalStates: Set<S>,
    private val outgoing: Map<S, List<Transition<S, E, C>>>,
    private val entering: Map<S, List<suspend (C, E) -> Unit>>,
    private val leaving: Map<S, List<suspend (C, E) -> Unit>>,
    private val listeners: List<suspend (TransitionResult.Accepted<S, E>) -> Unit>,
) {
    /**
     * Fires [event] against a subject sitting in [from].
     *
     * On acceptance the effects run in a fixed order — the source state's `onExit`, then the
     * transition's own effects, then the target state's `onEnter`, then the machine's
     * `onTransition` listeners — and each one may suspend. On rejection **nothing runs at all**, so
     * a refused event leaves no trace to undo.
     *
     * Effects are not transactional. An exception thrown by one propagates to the caller with the
     * earlier effects already done, which is the same bargain as any other suspending call in a
     * handler; keep anything that must be atomic with the state change in the caller's unit of
     * work, beside the write that applies [TransitionResult.Accepted.to].
     *
     * @param subject The aggregate the move is about. Guards and effects receive it.
     * @param from The state it is in now. Passed explicitly because the machine does not know how
     * the subject stores its state.
     * @param event The event to fire.
     * @return [TransitionResult.Accepted] with the state to move to, or [TransitionResult.Rejected]
     * with the reason.
     */
    suspend fun fire(
        subject: C,
        from: S,
        event: E,
    ): TransitionResult<S, E> {
        val transition =
            select(from, event)
                ?: return TransitionResult.Rejected(from, event, RejectionReason.NoTransition)

        val failed = transition.guards.firstOrNull { !it.test(subject) }
        if (failed != null) return TransitionResult.Rejected(from, event, RejectionReason.GuardFailed(failed.reason))

        // Both maps are total over `states`, and `select` has already established that `from` is
        // one of them — so neither lookup can come back empty-handed.
        leaving.getValue(from).forEach { it(subject, event) }
        transition.effects.forEach { it(subject, event) }
        entering.getValue(transition.to).forEach { it(subject, event) }

        val accepted = TransitionResult.Accepted(from, event, transition.to)
        listeners.forEach { it(accepted) }
        return accepted
    }

    /**
     * Fires [event], returning the state to move to and throwing when the machine refuses.
     *
     * The right shape wherever a rejection is never routine — a `POST /orders/{id}/pay` has nothing
     * useful to do with one except report it. Map [IllegalTransitionException] to `409 Conflict` in
     * the web layer and the handler stays a single line.
     *
     * @param subject The aggregate the move is about.
     * @param from The state it is in now.
     * @param event The event to fire.
     * @return The state to move to.
     * @throws IllegalTransitionException when the machine refuses the event.
     */
    suspend fun fireOrThrow(
        subject: C,
        from: S,
        event: E,
    ): S =
        when (val result = fire(subject, from, event)) {
            is TransitionResult.Accepted -> result.to
            is TransitionResult.Rejected -> throw IllegalTransitionException(from, event, result.reason)
        }

    /**
     * Whether [event] would be accepted, without firing it or running any effect.
     *
     * @param subject The aggregate the move would be about.
     * @param from The state it is in now.
     * @param event The event to test.
     */
    suspend fun canFire(
        subject: C,
        from: S,
        event: E,
    ): Boolean {
        val transition = select(from, event) ?: return false
        return transition.guards.all { it.test(subject) }
    }

    /**
     * The moves this subject can make right now, guards included.
     *
     * This is the affordance question — "what can this order do?" — and answering it is why a guard
     * takes the subject rather than the event. Render the answer as links with `transitionLinks`,
     * or as the buttons a UI should enable.
     *
     * @param subject The aggregate to evaluate the guards against.
     * @param from The state it is in now.
     * @return The transitions out of [from] whose every guard holds, in declaration order.
     */
    suspend fun availableFor(
        subject: C,
        from: S,
    ): List<Transition<S, E, C>> = transitionsFrom(from).filter { transition -> transition.guards.all { it.test(subject) } }

    /**
     * Every move declared out of [state], regardless of any subject.
     *
     * The shape of the graph rather than what one subject may do — use it to document the
     * lifecycle. For what is actually open to a subject, use [availableFor].
     *
     * @param state The state to look at.
     */
    fun transitionsFrom(state: S): List<Transition<S, E, C>> = outgoing[state].orEmpty()

    /**
     * Whether [state] is terminal — nothing leaves it.
     *
     * @param state The state to look at.
     */
    fun isFinal(state: S): Boolean = state in finalStates

    /**
     * Every state a subject in [state] could still end up in, following any sequence of moves.
     *
     * Guards are ignored: this is what the graph allows, not what one subject can do. [state]
     * itself is included only when a cycle leads back to it.
     *
     * @param state The state to start from.
     */
    fun reachableFrom(state: S): Set<S> {
        val found = mutableSetOf<S>()
        val pending = ArrayDeque(transitionsFrom(state).map { it.to })

        while (pending.isNotEmpty()) {
            val next = pending.removeFirst()
            if (found.add(next)) pending += transitionsFrom(next).map { it.to }
        }

        return found
    }

    /** The transition [event] triggers out of [from], or `null` when there is none. */
    private fun select(
        from: S,
        event: E,
    ): Transition<S, E, C>? = transitionsFrom(from).firstOrNull { it.event.isInstance(event) }
}

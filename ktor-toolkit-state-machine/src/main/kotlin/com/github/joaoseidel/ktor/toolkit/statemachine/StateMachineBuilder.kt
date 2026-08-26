package com.github.joaoseidel.ktor.toolkit.statemachine

import java.util.ArrayDeque
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import kotlin.reflect.KClass

/**
 * Declares a state machine, checking the definition before returning it.
 *
 * ```kotlin
 * val orderFlow = stateMachine<OrderState, OrderEvent, Order> {
 *     initial(DRAFT)
 *     final(SHIPPED, CANCELLED)
 *
 *     state(DRAFT) {
 *         on<Place>(PLACED) {
 *             guard("must have at least one line") { it.lines.isNotEmpty() }
 *             effect { order, event -> audit.record(order, event) }
 *         }
 *     }
 *
 *     state(PLACED) {
 *         onExit { order, _ -> holds.release(order) }
 *         on<Pay>(PAID)
 *         on<Cancel>(CANCELLED)
 *     }
 *
 *     state(PAID) {
 *         onEnter { order, _ -> payments.confirm(order) }
 *         on<Ship>(SHIPPED)
 *     }
 *
 *     onTransition { audit.log(it) }
 * }
 * ```
 *
 * A machine that does not hold together — an unreachable state, a dead end, an undeclared target —
 * throws [StateMachineDefinitionException] here rather than on the request that first walks it. Put
 * the declaration at the top level so that check runs at startup.
 *
 * @param S The state type — an enum in most services.
 * @param E The event type, usually a sealed hierarchy.
 * @param C The subject the moves are about, such as the aggregate itself.
 * @param block Declares the states, the moves between them and what happens on the way.
 * @throws StateMachineDefinitionException when the definition is not coherent.
 */
fun <S : Any, E : Any, C> stateMachine(block: StateMachineBuilder<S, E, C>.() -> Unit): StateMachine<S, E, C> =
    StateMachineBuilder<S, E, C>().apply(block).build()

/**
 * Collects the states and moves of a [stateMachine] block.
 *
 * @param S The state type.
 * @param E The event type.
 * @param C The subject type.
 */
@StateMachineDsl
class StateMachineBuilder<S : Any, E : Any, C> internal constructor() {
    private var start: S? = null
    private val declared = LinkedHashMap<S, StateBuilder<S, E, C>>()
    private val finals = LinkedHashSet<S>()
    private val listeners = mutableListOf<suspend (TransitionResult.Accepted<S, E>) -> Unit>()

    /**
     * Declares where a freshly created subject starts.
     *
     * Required, and only once — a machine with two beginnings is a machine nobody can reason about.
     *
     * @param state The initial state.
     */
    fun initial(state: S) {
        val existing = start
        if (existing != null) {
            throw StateMachineDefinitionException("the initial state is declared twice, as $existing and then as $state")
        }
        start = state
    }

    /**
     * Declares states as terminal: nothing leaves them, and the lifecycle is over.
     *
     * A final state needs no [state] block of its own unless it wants an `onEnter` hook. Every
     * state that is not final must have somewhere to go, so this is also how a leaf state stops
     * being reported as a dead end.
     *
     * @param states The terminal states.
     */
    fun final(vararg states: S) {
        finals += states
    }

    /**
     * Declares a state and the moves out of it.
     *
     * @param state The state being described.
     * @param block Its moves, and its `onEnter` / `onExit` hooks.
     */
    fun state(
        state: S,
        block: StateBuilder<S, E, C>.() -> Unit,
    ) {
        if (state in declared) throw StateMachineDefinitionException("state $state is declared twice")
        declared[state] = StateBuilder<S, E, C>(state).apply(block)
    }

    /**
     * Runs [listener] after every accepted transition, whichever one it was.
     *
     * The place for the concern that applies to the whole lifecycle rather than to one move —
     * an audit trail, a metric, a domain event. Per-move work belongs in that move's `effect`.
     *
     * @param listener What to do with each accepted transition.
     */
    fun onTransition(listener: suspend (TransitionResult.Accepted<S, E>) -> Unit) {
        listeners += listener
    }

    /** Compiles the declaration into a machine, refusing one that does not hold together. */
    internal fun build(): StateMachine<S, E, C> {
        val initial = start ?: throw StateMachineDefinitionException("no initial state is declared; add `initial(…)`")
        val outgoing = declared.mapValues { (_, builder) -> builder.buildTransitions() }
        val known = declared.keys + finals

        if (initial !in known) {
            throw StateMachineDefinitionException(
                "the initial state $initial is not declared; add `state($initial) { … }`, or list it in `final(…)`",
            )
        }

        outgoing.forEach { (state, transitions) ->
            val undeclared = transitions.firstOrNull { it.to !in known } ?: return@forEach
            throw StateMachineDefinitionException(
                "$state moves to ${undeclared.to} on `${undeclared.rel}`, but ${undeclared.to} is not declared; " +
                    "add `state(${undeclared.to}) { … }`, or list it in `final(…)`",
            )
        }

        outgoing.forEach { (state, transitions) ->
            if (state !in finals || transitions.isEmpty()) return@forEach
            throw StateMachineDefinitionException("$state is listed in `final(…)` but declares moves out of it")
        }

        outgoing.forEach { (state, transitions) ->
            if (state in finals || transitions.isNotEmpty()) return@forEach
            throw StateMachineDefinitionException("$state is a dead end: nothing leaves it, and it is not listed in `final(…)`")
        }

        val orphans = known - reachableFrom(initial, outgoing)
        if (orphans.isNotEmpty()) {
            throw StateMachineDefinitionException(
                "no sequence of moves reaches $orphans from $initial; an unreachable state is usually a missing transition " +
                    "rather than a state to delete",
            )
        }

        return StateMachine(
            initialState = initial,
            states = known,
            finalStates = finals.toSet(),
            outgoing = outgoing,
            // Total over every known state, including one that only `final(…)` mentions, so
            // firing never has to ask whether a state has hooks before running them.
            entering = known.associateWith { declared[it]?.entering.orEmpty() },
            leaving = known.associateWith { declared[it]?.leaving.orEmpty() },
            listeners = listeners.toList(),
        )
    }

    /** Every state walkable from [initial], including [initial] itself. */
    private fun reachableFrom(
        initial: S,
        outgoing: Map<S, List<Transition<S, E, C>>>,
    ): Set<S> {
        val found = mutableSetOf(initial)
        val pending = ArrayDeque(listOf(initial))

        while (pending.isNotEmpty()) {
            outgoing[pending.removeFirst()].orEmpty().forEach { if (found.add(it.to)) pending += it.to }
        }

        return found
    }
}

/**
 * Collects the moves out of one state, and the hooks that fire around them.
 *
 * @param S The state type.
 * @param E The event type.
 * @param C The subject type.
 */
@StateMachineDsl
class StateBuilder<S : Any, E : Any, C> internal constructor(
    private val state: S,
) {
    private val transitions = mutableListOf<TransitionBuilder<S, E, C>>()

    internal val entering = mutableListOf<suspend (C, E) -> Unit>()
    internal val leaving = mutableListOf<suspend (C, E) -> Unit>()

    /**
     * Runs [action] whenever a subject arrives in this state.
     *
     * Arriving is the only way in, so this is where work that must happen *however* the state was
     * reached belongs — a notification on `PAID`, say, that three different moves can trigger.
     * Never runs for the initial state: nothing transitions into it.
     *
     * @param action What to do, given the subject and the event that brought it here.
     */
    fun onEnter(action: suspend (C, E) -> Unit) {
        entering += action
    }

    /**
     * Runs [action] whenever a subject leaves this state, before the move's own effects.
     *
     * The counterpart of [onEnter], for releasing whatever holding the state implied.
     *
     * @param action What to do, given the subject and the event taking it away.
     */
    fun onExit(action: suspend (C, E) -> Unit) {
        leaving += action
    }

    /**
     * Declares that [T] moves a subject from this state to [to].
     *
     * ```kotlin
     * state(PLACED) {
     *     on<Pay>(PAID)
     * }
     * ```
     *
     * @param T The event that triggers the move. Matching is by instance, so declaring a sealed
     * parent covers every subtype — and declaring both the parent and a subtype in one state is
     * refused, because which one applied would depend on declaration order.
     * @param to The state the move arrives at.
     */
    inline fun <reified T : E> on(to: S) = on(T::class, to) {}

    /**
     * Declares that [T] moves a subject from this state to [to], with guards or effects on the way.
     *
     * ```kotlin
     * state(DRAFT) {
     *     on<Place>(PLACED) {
     *         guard("must have at least one line") { it.lines.isNotEmpty() }
     *         effect { order, event -> audit.record(order, event) }
     *     }
     * }
     * ```
     *
     * @param T The event that triggers the move.
     * @param to The state the move arrives at.
     * @param block The move's `rel`, guards and effects.
     */
    inline fun <reified T : E> on(
        to: S,
        noinline block: TransitionBuilder<S, E, C>.() -> Unit,
    ) = on(T::class, to, block)

    /** Registers a move, taking the event as a class rather than a type argument. */
    @PublishedApi
    internal fun on(
        event: KClass<out E>,
        to: S,
        block: TransitionBuilder<S, E, C>.() -> Unit,
    ) {
        transitions += TransitionBuilder<S, E, C>(state, to, event).apply(block)
    }

    /** Compiles this state's moves, refusing two that the same event could both trigger. */
    internal fun buildTransitions(): List<Transition<S, E, C>> {
        val compiled = transitions.map { it.build() }

        compiled.forEachIndexed { index, transition ->
            val shadowed = compiled.take(index).firstOrNull { it.event.java.isAssignableFrom(transition.event.java) } ?: return@forEachIndexed
            throw StateMachineDefinitionException(
                "$state declares `${transition.rel}` on ${transition.event.simpleName} after `${shadowed.rel}` on " +
                    "${shadowed.event.simpleName}, which already matches it; an event may only lead one way out of a state",
            )
        }

        return compiled
    }
}

/**
 * `Pay` becomes `pay`.
 *
 * Spelled out rather than with `replaceFirstChar`, whose empty-name arm a reified type argument can
 * never reach — an anonymous object has no name, and no anonymous type can be named as one.
 */
private fun KClass<*>.defaultRel(): String = java.simpleName.let { it.take(1).lowercase() + it.drop(1) }

/**
 * Collects one move's link relation, guards and effects.
 *
 * @param S The state type.
 * @param E The event type.
 * @param C The subject type.
 */
@StateMachineDsl
class TransitionBuilder<S : Any, E : Any, C> internal constructor(
    private val from: S,
    private val to: S,
    private val event: KClass<out E>,
) {
    private val guards = mutableListOf<Guard<C>>()
    private val effects = mutableListOf<suspend (C, E) -> Unit>()

    /**
     * How this move relates to the subject, published as a link relation by `transitionLinks`.
     *
     * Defaults to the event's decapitalized simple name — `Pay` becomes `"pay"` — which is usually
     * the word the API already uses. Set it when the event is named for the domain and the relation
     * should be named for the client.
     */
    var rel: String = event.defaultRel()

    /**
     * Refuses the move unless [test] holds for the subject.
     *
     * Guards are checked in declaration order and the first failure wins, reporting [reason] as
     * [RejectionReason.GuardFailed] — so write [reason] for whoever made the request:
     * `"must have at least one line"`, not `"lines.isEmpty"`.
     *
     * A guard sees the subject and **not the event**. That is what lets
     * [StateMachine.availableFor] answer "what can this order do right now?" without inventing an
     * event to test with — the whole reason the machine can publish its own affordances. A check
     * that needs the event's payload is request validation, not a guard.
     *
     * @param reason Why the move is refused when [test] does not hold.
     * @param test The precondition, applied to the subject.
     */
    fun guard(
        reason: String,
        test: suspend (C) -> Boolean,
    ) {
        guards += Guard(reason, test)
    }

    /**
     * Runs [action] when the move is accepted, after the source state's `onExit`.
     *
     * Declare it more than once to run several, in order. Effects belong to this move alone; work
     * that every move should do belongs in [StateMachineBuilder.onTransition].
     *
     * @param action What to do, given the subject and the event.
     */
    fun effect(action: suspend (C, E) -> Unit) {
        effects += action
    }

    /** Compiles this move. */
    internal fun build(): Transition<S, E, C> = Transition(from, to, event, rel, guards.toList(), effects.toList())
}

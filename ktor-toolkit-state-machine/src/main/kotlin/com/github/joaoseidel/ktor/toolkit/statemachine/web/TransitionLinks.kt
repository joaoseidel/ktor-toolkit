package com.github.joaoseidel.ktor.toolkit.statemachine.web

import com.github.joaoseidel.ktor.toolkit.hateoas.data.Link
import com.github.joaoseidel.ktor.toolkit.hateoas.data.Resource
import com.github.joaoseidel.ktor.toolkit.statemachine.StateMachine
import com.github.joaoseidel.ktor.toolkit.statemachine.Transition
import io.ktor.http.HttpMethod

/**
 * The moves this subject can make right now, as links a client can follow.
 *
 * A machine already knows which transitions are open to a subject, guards included, and that is the
 * question `_links` exists to answer — so the affordances and the rules that decide them stop
 * drifting apart. A client no longer has to reimplement the lifecycle to know whether to show a
 * "Pay" button; the absence of the link is the answer.
 *
 * `resource { }` takes a non-suspending block and guards may suspend, so build the links first and
 * hand them to [LinksBuilder.links]:
 *
 * ```kotlin
 * val moves = orderFlow.transitionLinks(order, order.state) { "/orders/${order.id}/${it.rel}" }
 *
 * call.respond(
 *     resource(order.toResponse()) {
 *         link("self", "/orders/${order.id}")
 *         links(moves)
 *     },
 * )
 * ```
 *
 * Requires `ktor-toolkit-hateoas` on the classpath — it is a `compileOnly` dependency here, so a
 * consumer that does not publish links does not pay for it.
 *
 * @param S The state type.
 * @param E The event type.
 * @param C The subject type.
 * @param subject The aggregate whose guards decide which moves are open.
 * @param from The state it is in now.
 * @param method The HTTP method each link is followed with. `POST` by default, since a transition
 * is an action rather than a resource.
 * @param href Where the move's link points, given the transition. Its `rel` is usually part of it.
 * @return One link per available move, in declaration order.
 */
suspend fun <S : Any, E : Any, C> StateMachine<S, E, C>.transitionLinks(
    subject: C,
    from: S,
    method: HttpMethod = HttpMethod.Post,
    href: (Transition<S, E, C>) -> String,
): List<Link> = availableFor(subject, from).map { Link(it.rel, href(it), method) }

/**
 * Returns a copy of this resource with the subject's available moves appended as links.
 *
 * The shortcut for the common case, where the links being published are exactly the machine's
 * affordances and nothing else needs computing first:
 *
 * ```kotlin
 * call.respond(
 *     resource(order.toResponse()) { link("self", "/orders/${order.id}") }
 *         .withTransitions(orderFlow, order, order.state) { "/orders/${order.id}/${it.rel}" },
 * )
 * ```
 *
 * @param T The type of the resource's content.
 * @param S The state type.
 * @param E The event type.
 * @param C The subject type.
 * @param machine The machine describing the subject's lifecycle.
 * @param subject The aggregate whose guards decide which moves are open.
 * @param from The state it is in now.
 * @param method The HTTP method each link is followed with. `POST` by default.
 * @param href Where the move's link points, given the transition.
 */
suspend fun <T, S : Any, E : Any, C> Resource<T>.withTransitions(
    machine: StateMachine<S, E, C>,
    subject: C,
    from: S,
    method: HttpMethod = HttpMethod.Post,
    href: (Transition<S, E, C>) -> String,
): Resource<T> = withLinks(machine.transitionLinks(subject, from, method, href))

package com.github.joaoseidel.ktor.toolkit.statemachine.web

import com.github.joaoseidel.ktor.toolkit.hateoas.data.Link
import com.github.joaoseidel.ktor.toolkit.hateoas.data.resource
import com.github.joaoseidel.ktor.toolkit.statemachine.support.Order
import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderState
import com.github.joaoseidel.ktor.toolkit.statemachine.support.orderFlow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod
import jdk.jpackage.internal.Arguments.CLIOptions.context
import java.util.Collections.emptyList

class TransitionLinksTest :
    ShouldSpec(
        {
            val order = Order()
            val empty = Order(lines = emptyList())

            context("transitionLinks") {
                should("publish one link per available move, defaulting to POST") {
                    val links = orderFlow.transitionLinks(order, OrderState.DRAFT) { "/orders/${order.id}/${it.rel}" }

                    links shouldContainExactly
                        listOf(
                            Link("place", "/orders/order-1/place", HttpMethod.Post),
                            Link("cancel", "/orders/order-1/cancel", HttpMethod.Post),
                        )
                }

                should("leave out a move whose guard does not hold") {
                    val links = orderFlow.transitionLinks(empty, OrderState.DRAFT) { "/orders/${empty.id}/${it.rel}" }

                    links.map { it.rel } shouldContainExactly listOf("cancel")
                }

                should("use the method it is given") {
                    val links = orderFlow.transitionLinks(order, OrderState.PAID, HttpMethod.Put) { "/orders/${order.id}/${it.rel}" }

                    links shouldContainExactly listOf(Link("dispatch", "/orders/order-1/dispatch", HttpMethod.Put))
                }

                should("be empty in a final state") {
                    orderFlow.transitionLinks(order, OrderState.SHIPPED) { "/orders/${order.id}/${it.rel}" } shouldBe emptyList()
                }
            }

            context("withTransitions") {
                should("append the moves to the links a resource already has") {
                    val published =
                        resource(order) { link("self", "/orders/${order.id}") }
                            .withTransitions(orderFlow, order, OrderState.PAID) { "/orders/${order.id}/${it.rel}" }

                    published.links shouldContainExactly
                        listOf(
                            Link("self", "/orders/order-1", HttpMethod.Get),
                            Link("dispatch", "/orders/order-1/dispatch", HttpMethod.Post),
                        )
                }

                should("use the method it is given") {
                    val published =
                        resource(order)
                            .withTransitions(orderFlow, order, OrderState.PAID, HttpMethod.Patch) { "/orders/${order.id}/${it.rel}" }

                    published.links shouldContainExactly listOf(Link("dispatch", "/orders/order-1/dispatch", HttpMethod.Patch))
                }
            }
        },
    )

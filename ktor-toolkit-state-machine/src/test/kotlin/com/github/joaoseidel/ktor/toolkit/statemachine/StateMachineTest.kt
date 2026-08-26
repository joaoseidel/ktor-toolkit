package com.github.joaoseidel.ktor.toolkit.statemachine

import com.github.joaoseidel.ktor.toolkit.statemachine.support.Order
import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderEvent
import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderState
import com.github.joaoseidel.ktor.toolkit.statemachine.support.orderFlow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import jdk.jpackage.internal.Arguments.CLIOptions.context
import java.util.Collections.emptyList
import java.util.Collections.emptySet

class StateMachineTest :
    ShouldSpec(
        {
            val order = Order()
            val empty = Order(lines = emptyList())

            context("fire") {
                should("accept a declared move and report where to go") {
                    val result = orderFlow.fire(order, OrderState.PLACED, OrderEvent.Pay)

                    result shouldBe TransitionResult.Accepted(OrderState.PLACED, OrderEvent.Pay, OrderState.PAID)
                }

                should("leave the subject untouched") {
                    orderFlow.fire(order, OrderState.PLACED, OrderEvent.Pay)

                    order.state shouldBe OrderState.DRAFT
                }

                should("reject an event no move is declared for") {
                    val result = orderFlow.fire(order, OrderState.PAID, OrderEvent.Pay)

                    result shouldBe TransitionResult.Rejected(OrderState.PAID, OrderEvent.Pay, RejectionReason.NoTransition)
                }

                should("reject a move whose guard does not hold, naming the guard") {
                    val place = OrderEvent.Place(by = "someone")

                    val result = orderFlow.fire(empty, OrderState.DRAFT, place)

                    result shouldBe
                        TransitionResult.Rejected(
                            OrderState.DRAFT,
                            place,
                            RejectionReason.GuardFailed("must have at least one line"),
                        )
                }

                should("report the first failing guard and stop") {
                    var secondWasAsked = false
                    val machine =
                        stateMachine<OrderState, OrderEvent, Order> {
                            initial(OrderState.DRAFT)
                            final(OrderState.PLACED)

                            state(OrderState.DRAFT) {
                                on<OrderEvent.Pay>(OrderState.PLACED) {
                                    guard("must be first") { false }
                                    guard("must be second") {
                                        secondWasAsked = true
                                        false
                                    }
                                }
                            }
                        }

                    val result = machine.fire(order, OrderState.DRAFT, OrderEvent.Pay)

                    result shouldBe
                        TransitionResult.Rejected(OrderState.DRAFT, OrderEvent.Pay, RejectionReason.GuardFailed("must be first"))
                    secondWasAsked shouldBe false
                }

                should("run exit, effects, enter and listeners in that order") {
                    val trace = mutableListOf<String>()
                    val machine =
                        stateMachine<OrderState, OrderEvent, Order> {
                            initial(OrderState.DRAFT)
                            final(OrderState.PLACED)

                            state(OrderState.DRAFT) {
                                onEnter { _, _ -> trace += "enter DRAFT" }
                                onExit { _, _ -> trace += "exit DRAFT" }

                                on<OrderEvent.Pay>(OrderState.PLACED) {
                                    effect { _, _ -> trace += "first effect" }
                                    effect { _, _ -> trace += "second effect" }
                                }
                            }

                            state(OrderState.PLACED) {
                                onEnter { _, _ -> trace += "enter PLACED" }
                            }

                            onTransition { trace += "listener ${it.to}" }
                        }

                    machine.fire(order, OrderState.DRAFT, OrderEvent.Pay)

                    trace shouldContainExactly
                        listOf("exit DRAFT", "first effect", "second effect", "enter PLACED", "listener PLACED")
                }

                should("hand the subject and the event to every effect") {
                    val seen = mutableListOf<Pair<Order, OrderEvent>>()
                    val machine =
                        stateMachine<OrderState, OrderEvent, Order> {
                            initial(OrderState.DRAFT)
                            final(OrderState.PLACED)

                            state(OrderState.DRAFT) {
                                onExit { subject, event -> seen += subject to event }
                                on<OrderEvent.Cancel>(OrderState.PLACED) {
                                    effect { subject, event -> seen += subject to event }
                                }
                            }

                            state(OrderState.PLACED) {
                                onEnter { subject, event -> seen += subject to event }
                            }
                        }
                    val cancel = OrderEvent.Cancel(reason = "changed my mind")

                    machine.fire(order, OrderState.DRAFT, cancel)

                    seen shouldContainExactly List(3) { order to cancel }
                }

                should("run nothing at all when the move is refused") {
                    var ran = false
                    val machine =
                        stateMachine<OrderState, OrderEvent, Order> {
                            initial(OrderState.DRAFT)
                            final(OrderState.PLACED)

                            state(OrderState.DRAFT) {
                                onExit { _, _ -> ran = true }
                                on<OrderEvent.Pay>(OrderState.PLACED) {
                                    guard("never") { false }
                                    effect { _, _ -> ran = true }
                                }
                            }

                            state(OrderState.PLACED) {
                                onEnter { _, _ -> ran = true }
                            }

                            onTransition { ran = true }
                        }

                    machine.fire(order, OrderState.DRAFT, OrderEvent.Pay)

                    ran shouldBe false
                }

                should("match an event against a declared supertype") {
                    val machine =
                        stateMachine<OrderState, OrderEvent, Order> {
                            initial(OrderState.DRAFT)
                            final(OrderState.CANCELLED)

                            state(OrderState.DRAFT) {
                                on<OrderEvent>(OrderState.CANCELLED)
                            }
                        }

                    machine.fire(order, OrderState.DRAFT, OrderEvent.Pay) shouldBe
                        TransitionResult.Accepted(OrderState.DRAFT, OrderEvent.Pay, OrderState.CANCELLED)
                }
            }

            context("fireOrThrow") {
                should("return the state to move to") {
                    orderFlow.fireOrThrow(order, OrderState.PLACED, OrderEvent.Pay) shouldBe OrderState.PAID
                }

                should("throw with the reason when the move is refused") {
                    val thrown =
                        shouldThrow<IllegalTransitionException> {
                            orderFlow.fireOrThrow(order, OrderState.PAID, OrderEvent.Pay)
                        }

                    thrown.from shouldBe OrderState.PAID
                    thrown.event shouldBe OrderEvent.Pay
                    thrown.reason shouldBe RejectionReason.NoTransition
                }
            }

            context("canFire") {
                should("answer for a move whose guards hold") {
                    orderFlow.canFire(order, OrderState.DRAFT, OrderEvent.Place(by = "someone")) shouldBe true
                }

                should("answer for a move whose guard does not hold") {
                    orderFlow.canFire(empty, OrderState.DRAFT, OrderEvent.Place(by = "someone")) shouldBe false
                }

                should("answer for an event no move is declared for") {
                    orderFlow.canFire(order, OrderState.PAID, OrderEvent.Pay) shouldBe false
                }

                should("run no effect") {
                    var ran = false
                    val machine =
                        stateMachine<OrderState, OrderEvent, Order> {
                            initial(OrderState.DRAFT)
                            final(OrderState.PLACED)

                            state(OrderState.DRAFT) {
                                on<OrderEvent.Pay>(OrderState.PLACED) { effect { _, _ -> ran = true } }
                            }
                        }

                    machine.canFire(order, OrderState.DRAFT, OrderEvent.Pay) shouldBe true
                    ran shouldBe false
                }
            }

            context("availableFor") {
                should("list every move whose guards hold, in declaration order") {
                    orderFlow.availableFor(order, OrderState.DRAFT).map { it.rel } shouldContainExactly listOf("place", "cancel")
                }

                should("leave out a move whose guard does not hold") {
                    orderFlow.availableFor(empty, OrderState.DRAFT).map { it.rel } shouldContainExactly listOf("cancel")
                }

                should("be empty in a final state") {
                    orderFlow.availableFor(order, OrderState.SHIPPED) shouldBe emptyList()
                }
            }

            context("transitionsFrom") {
                should("ignore guards") {
                    orderFlow.transitionsFrom(OrderState.DRAFT).map { it.rel } shouldContainExactly listOf("place", "cancel")
                }

                should("be empty for a state nothing leaves") {
                    orderFlow.transitionsFrom(OrderState.CANCELLED) shouldBe emptyList()
                }
            }

            context("isFinal") {
                should("hold for a state listed as final") {
                    orderFlow.isFinal(OrderState.SHIPPED) shouldBe true
                }

                should("not hold for a state something leaves") {
                    orderFlow.isFinal(OrderState.PLACED) shouldBe false
                }
            }

            context("reachableFrom") {
                should("follow moves transitively") {
                    orderFlow.reachableFrom(OrderState.PLACED) shouldBe
                        setOf(OrderState.PAID, OrderState.CANCELLED, OrderState.SHIPPED)
                }

                should("be empty for a final state") {
                    orderFlow.reachableFrom(OrderState.SHIPPED) shouldBe emptySet()
                }

                should("include the starting state only when a cycle leads back to it") {
                    val machine =
                        stateMachine<OrderState, OrderEvent, Order> {
                            initial(OrderState.DRAFT)

                            state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.PLACED) }
                            state(OrderState.PLACED) { on<OrderEvent.Cancel>(OrderState.DRAFT) }
                        }

                    machine.reachableFrom(OrderState.DRAFT) shouldBe setOf(OrderState.PLACED, OrderState.DRAFT)
                }
            }

            context("the machine itself") {
                should("report where a subject starts") {
                    orderFlow.initialState shouldBe OrderState.DRAFT
                }

                should("report every state it knows, declared or final") {
                    orderFlow.states shouldBe
                        setOf(
                            OrderState.DRAFT,
                            OrderState.PLACED,
                            OrderState.PAID,
                            OrderState.SHIPPED,
                            OrderState.CANCELLED,
                        )
                }
            }
        },
    )

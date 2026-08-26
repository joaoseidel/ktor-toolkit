package com.github.joaoseidel.ktor.toolkit.statemachine

import com.github.joaoseidel.ktor.toolkit.statemachine.support.Order
import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderEvent
import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/** Builds a machine, so a test only has to say what is wrong with the definition. */
private fun define(block: StateMachineBuilder<OrderState, OrderEvent, Order>.() -> Unit) = stateMachine(block)

/** The message of the [StateMachineDefinitionException] that [block] is expected to raise. */
private fun refusal(block: StateMachineBuilder<OrderState, OrderEvent, Order>.() -> Unit): String =
    shouldThrow<StateMachineDefinitionException> { define(block) }.message.orEmpty()

class StateMachineBuilderTest :
    ShouldSpec(
        {
            context("a coherent definition") {
                should("accept a final state that carries an onEnter hook of its own") {
                    var arrived = false
                    val machine =
                        define {
                            initial(OrderState.DRAFT)
                            final(OrderState.PLACED)

                            state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.PLACED) }
                            state(OrderState.PLACED) { onEnter { _, _ -> arrived = true } }
                        }

                    machine.fire(Order(), OrderState.DRAFT, OrderEvent.Pay)

                    arrived shouldBe true
                    machine.isFinal(OrderState.PLACED) shouldBe true
                }

                should("accept a self-transition") {
                    val machine =
                        define {
                            initial(OrderState.DRAFT)

                            state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.DRAFT) }
                        }

                    machine.fire(Order(), OrderState.DRAFT, OrderEvent.Pay) shouldBe
                        TransitionResult.Accepted(OrderState.DRAFT, OrderEvent.Pay, OrderState.DRAFT)
                }

                should("accept a narrower move declared before a catch-all") {
                    val machine =
                        define {
                            initial(OrderState.DRAFT)
                            final(OrderState.PAID, OrderState.CANCELLED)

                            state(OrderState.DRAFT) {
                                on<OrderEvent.Pay>(OrderState.PAID)
                                on<OrderEvent>(OrderState.CANCELLED) { rel = "abandon" }
                            }
                        }

                    machine.fireOrThrow(Order(), OrderState.DRAFT, OrderEvent.Pay) shouldBe OrderState.PAID
                    machine.fireOrThrow(Order(), OrderState.DRAFT, OrderEvent.Cancel("no")) shouldBe OrderState.CANCELLED
                }
            }

            context("an incoherent definition") {
                should("refuse a machine with no initial state") {
                    refusal {
                        final(OrderState.DRAFT)
                    } shouldContain "no initial state is declared"
                }

                should("refuse a second initial state") {
                    refusal {
                        initial(OrderState.DRAFT)
                        initial(OrderState.PLACED)
                    } shouldContain "the initial state is declared twice, as DRAFT and then as PLACED"
                }

                should("refuse a state declared twice") {
                    refusal {
                        initial(OrderState.DRAFT)
                        final(OrderState.PLACED)

                        state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.PLACED) }
                        state(OrderState.DRAFT) { on<OrderEvent.Cancel>(OrderState.PLACED) }
                    } shouldContain "state DRAFT is declared twice"
                }

                should("refuse an initial state nobody declared") {
                    refusal {
                        initial(OrderState.DRAFT)
                        final(OrderState.PLACED)

                        state(OrderState.PAID) { on<OrderEvent.Pay>(OrderState.PLACED) }
                    } shouldContain "the initial state DRAFT is not declared"
                }

                should("refuse a move to a state nobody declared") {
                    refusal {
                        initial(OrderState.DRAFT)

                        state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.PAID) }
                    } shouldContain "DRAFT moves to PAID on `pay`, but PAID is not declared"
                }

                should("refuse a final state that declares a move out of it") {
                    refusal {
                        initial(OrderState.DRAFT)
                        final(OrderState.DRAFT, OrderState.PLACED)

                        state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.PLACED) }
                    } shouldContain "DRAFT is listed in `final(…)` but declares moves out of it"
                }

                should("refuse a dead end") {
                    refusal {
                        initial(OrderState.DRAFT)

                        state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.PLACED) }
                        state(OrderState.PLACED) { onEnter { _, _ -> } }
                    } shouldContain "PLACED is a dead end"
                }

                should("refuse an unreachable state") {
                    refusal {
                        initial(OrderState.DRAFT)
                        final(OrderState.PLACED, OrderState.SHIPPED)

                        state(OrderState.DRAFT) { on<OrderEvent.Pay>(OrderState.PLACED) }
                        state(OrderState.PAID) { on<OrderEvent.Ship>(OrderState.SHIPPED) }
                    } shouldContain "no sequence of moves reaches [PAID, SHIPPED] from DRAFT"
                }

                should("refuse the same event leading two ways out of one state") {
                    refusal {
                        initial(OrderState.DRAFT)
                        final(OrderState.PLACED, OrderState.CANCELLED)

                        state(OrderState.DRAFT) {
                            on<OrderEvent.Pay>(OrderState.PLACED)
                            on<OrderEvent.Pay>(OrderState.CANCELLED) { rel = "abandon" }
                        }
                    } shouldContain "DRAFT declares `abandon` on Pay after `pay` on Pay, which already matches it"
                }

                should("refuse a move shadowed by a catch-all declared before it") {
                    refusal {
                        initial(OrderState.DRAFT)
                        final(OrderState.PLACED, OrderState.CANCELLED)

                        state(OrderState.DRAFT) {
                            on<OrderEvent>(OrderState.CANCELLED) { rel = "abandon" }
                            on<OrderEvent.Pay>(OrderState.PLACED)
                        }
                    } shouldContain "DRAFT declares `pay` on Pay after `abandon` on OrderEvent, which already matches it"
                }
            }

            context("rel") {
                should("default to the decapitalized event name") {
                    define {
                        initial(OrderState.DRAFT)
                        final(OrderState.PLACED)

                        state(OrderState.DRAFT) { on<OrderEvent.Place>(OrderState.PLACED) }
                    }.transitionsFrom(OrderState.DRAFT).map { it.rel } shouldContainExactly listOf("place")
                }

                should("be overridable per move") {
                    define {
                        initial(OrderState.DRAFT)
                        final(OrderState.PLACED)

                        state(OrderState.DRAFT) { on<OrderEvent.Place>(OrderState.PLACED) { rel = "submit" } }
                    }.transitionsFrom(OrderState.DRAFT).map { it.rel } shouldContainExactly listOf("submit")
                }
            }
        },
    )

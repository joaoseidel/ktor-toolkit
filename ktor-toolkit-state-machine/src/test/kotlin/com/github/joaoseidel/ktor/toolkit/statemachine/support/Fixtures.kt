package com.github.joaoseidel.ktor.toolkit.statemachine.support

import com.github.joaoseidel.ktor.toolkit.statemachine.StateMachine
import com.github.joaoseidel.ktor.toolkit.statemachine.stateMachine

/** The lifecycle every test in this module is written against. */
enum class OrderState { DRAFT, PLACED, PAID, SHIPPED, CANCELLED }

/** The events that move an [Order] through [OrderState]. */
sealed interface OrderEvent {
    data class Place(
        val by: String,
    ) : OrderEvent

    data object Pay : OrderEvent

    data class Ship(
        val carrier: String,
    ) : OrderEvent

    data class Cancel(
        val reason: String,
    ) : OrderEvent
}

/**
 * The subject the moves are about.
 *
 * [lines] is what the `DRAFT` guard asks about, so a test picks which side of that guard it is on
 * by constructing an order with or without them.
 */
data class Order(
    val id: String = "order-1",
    val state: OrderState = OrderState.DRAFT,
    val lines: List<String> = listOf("a book"),
)

/**
 * The canonical machine: one guard, one final pair, and a move out of every non-final state.
 *
 * Tests that need to observe effects build their own machine with [stateMachine] instead — this one
 * is deliberately free of side effects so it can be shared.
 */
val orderFlow: StateMachine<OrderState, OrderEvent, Order> =
    stateMachine {
        initial(OrderState.DRAFT)
        final(OrderState.SHIPPED, OrderState.CANCELLED)

        state(OrderState.DRAFT) {
            on<OrderEvent.Place>(OrderState.PLACED) {
                guard("must have at least one line") { it.lines.isNotEmpty() }
            }
            on<OrderEvent.Cancel>(OrderState.CANCELLED)
        }

        state(OrderState.PLACED) {
            on<OrderEvent.Pay>(OrderState.PAID)
            on<OrderEvent.Cancel>(OrderState.CANCELLED)
        }

        state(OrderState.PAID) {
            on<OrderEvent.Ship>(OrderState.SHIPPED) { rel = "dispatch" }
        }
    }

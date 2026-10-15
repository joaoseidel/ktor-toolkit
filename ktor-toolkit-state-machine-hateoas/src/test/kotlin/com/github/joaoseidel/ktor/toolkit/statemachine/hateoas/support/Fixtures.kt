package com.github.joaoseidel.ktor.toolkit.statemachine.hateoas.support

import com.github.joaoseidel.ktor.toolkit.statemachine.api.StateMachine
import com.github.joaoseidel.ktor.toolkit.statemachine.api.stateMachine

enum class OrderState { DRAFT, PLACED, PAID, SHIPPED, CANCELLED }

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

data class Order(
    val id: String = "order-1",
    val state: OrderState = OrderState.DRAFT,
    val lines: List<String> = listOf("a book"),
)

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

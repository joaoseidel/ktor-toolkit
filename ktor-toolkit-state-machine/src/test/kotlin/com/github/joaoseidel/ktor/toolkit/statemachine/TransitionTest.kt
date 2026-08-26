package com.github.joaoseidel.ktor.toolkit.statemachine

import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderState
import com.github.joaoseidel.ktor.toolkit.statemachine.support.orderFlow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class TransitionTest :
    ShouldSpec(
        {
            should("render as the move it describes") {
                orderFlow.transitionsFrom(OrderState.PAID).single().toString() shouldBe "PAID --dispatch--> SHIPPED"
            }

            should("carry where it goes and what triggers it") {
                val transition = orderFlow.transitionsFrom(OrderState.PAID).single()

                transition.from shouldBe OrderState.PAID
                transition.to shouldBe OrderState.SHIPPED
                transition.event.simpleName shouldBe "Ship"
            }
        },
    )

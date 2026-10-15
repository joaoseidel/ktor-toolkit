package com.github.joaoseidel.ktor.toolkit.statemachine.api.error

import com.github.joaoseidel.ktor.toolkit.statemachine.api.RejectionReason
import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderEvent
import com.github.joaoseidel.ktor.toolkit.statemachine.support.OrderState
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class IllegalTransitionExceptionTest :
    ShouldSpec(
        {
            should("read as one sentence, naming the state, the event and the reason") {
                val thrown =
                    IllegalTransitionException(
                        OrderState.PAID,
                        OrderEvent.Pay,
                        RejectionReason.GuardFailed("must have at least one line"),
                    )

                thrown.message shouldBe "cannot fire Pay in state PAID, because must have at least one line"
            }
        },
    )

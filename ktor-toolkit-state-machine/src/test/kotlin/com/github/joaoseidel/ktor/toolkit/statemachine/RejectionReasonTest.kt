package com.github.joaoseidel.ktor.toolkit.statemachine

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class RejectionReasonTest :
    ShouldSpec(
        {
            context("NoTransition") {
                should("say that the move does not exist here") {
                    RejectionReason.NoTransition.message shouldBe "no transition is declared for that event"
                }
            }

            context("GuardFailed") {
                should("report the guard's own reason") {
                    RejectionReason.GuardFailed("must have at least one line").message shouldBe "must have at least one line"
                }
            }
        },
    )

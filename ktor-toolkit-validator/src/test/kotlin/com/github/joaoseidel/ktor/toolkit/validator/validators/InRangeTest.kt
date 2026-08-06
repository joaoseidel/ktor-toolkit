package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class InRangeTest :
    ShouldSpec({
        context("be inRange") {
            should("accept a value inside the range") {
                messagesOf(5) { should be inRange(1, 10) } shouldBe emptyList()
            }

            should("treat both bounds as inclusive") {
                messagesOf(1) { should be inRange(1, 10) } shouldBe emptyList()
                messagesOf(10) { should be inRange(1, 10) } shouldBe emptyList()
            }

            should("reject a value outside it") {
                messagesOf(0) { should be inRange(1, 10) } shouldBe listOf("should be in range of 1..10")
                messagesOf(11) { should be inRange(1, 10) }.size shouldBe 1
            }

            should("reject everything when the range is empty") {
                messagesOf(5) { should be inRange(10, 1) }.size shouldBe 1
            }
        }

        context("notBe inRange") {
            should("accept a value outside the range") {
                messagesOf(11) { should notBe inRange(1, 10) } shouldBe emptyList()
            }

            should("reject a value inside it") {
                messagesOf(5) { should notBe inRange(1, 10) } shouldBe listOf("should not be in range of 1..10")
            }
        }

        context("across numeric types") {
            should("compare integral values exactly") {
                messagesOf(Long.MAX_VALUE) { should be inRange(0, Long.MAX_VALUE) } shouldBe emptyList()
            }

            should("compare fractional values as doubles") {
                messagesOf(1.5) { should be inRange(1.0, 2.0) } shouldBe emptyList()
                messagesOf(2.5) { should be inRange(1.0, 2.0) }.size shouldBe 1
            }

            should("compare a BigDecimal against integral bounds") {
                messagesOf(BigDecimal("1.5")) { should be inRange(1, 2) } shouldBe emptyList()
                messagesOf(BigDecimal("2.5")) { should be inRange(1, 2) }.size shouldBe 1
            }

            should("mix bound types with the value's") {
                messagesOf(5) { should be inRange(1.5, 10L) } shouldBe emptyList()
            }
        }

        should("stay silent on an absent value") {
            messagesOf<Int?>(null) { should be inRange(1, 10) } shouldBe emptyList()
        }
    })

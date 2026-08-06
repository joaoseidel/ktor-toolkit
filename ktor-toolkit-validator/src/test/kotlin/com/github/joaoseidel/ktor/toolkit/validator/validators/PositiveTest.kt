package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PositiveTest :
    ShouldSpec({
        context("be positive") {
            should("accept a value above zero") {
                messagesOf(1) { should be positive() } shouldBe emptyList()
            }

            should("reject zero") {
                messagesOf(0) { should be positive() } shouldBe listOf("should be positive")
            }

            should("reject a value below zero") {
                messagesOf(-1) { should be positive() } shouldBe listOf("should be positive")
            }
        }

        context("notBe positive") {
            should("accept zero and below") {
                messagesOf(0) { should notBe positive() } shouldBe emptyList()
                messagesOf(-1) { should notBe positive() } shouldBe emptyList()
            }

            should("reject a value above zero") {
                messagesOf(1) { should notBe positive() } shouldBe listOf("should not be positive")
            }
        }

        context("across numeric types") {
            should("judge a Long") {
                messagesOf(1L) { should be positive() } shouldBe emptyList()
                messagesOf(-1L) { should be positive() }.size shouldBe 1
            }

            should("judge a Double") {
                messagesOf(0.5) { should be positive() } shouldBe emptyList()
                messagesOf(-0.5) { should be positive() }.size shouldBe 1
            }

            should("judge a Float") {
                messagesOf(0.5f) { should be positive() } shouldBe emptyList()
            }

            should("judge a Short") {
                messagesOf(1.toShort()) { should be positive() } shouldBe emptyList()
                messagesOf((-1).toShort()) { should be positive() }.size shouldBe 1
            }

            should("judge a Byte") {
                messagesOf(1.toByte()) { should be positive() } shouldBe emptyList()
            }

            should("judge a BigDecimal") {
                messagesOf(BigDecimal("0.01")) { should be positive() } shouldBe emptyList()
                messagesOf(BigDecimal("-0.01")) { should be positive() }.size shouldBe 1
            }
        }

        should("stay silent on an absent value") {
            messagesOf<Int?>(null) { should be positive() } shouldBe emptyList()
        }
    })

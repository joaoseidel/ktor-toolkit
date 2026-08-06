package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class NegativeTest :
    ShouldSpec(
        {
            context("be negative") {
                should("accept a value below zero") {
                    messagesOf(-1) { should be negative() } shouldBe emptyList()
                }

                should("reject zero") {
                    messagesOf(0) { should be negative() } shouldBe listOf("should be negative")
                }

                should("reject a value above zero") {
                    messagesOf(1) { should be negative() } shouldBe listOf("should be negative")
                }
            }

            context("notBe negative") {
                should("accept zero and above") {
                    messagesOf(0) { should notBe negative() } shouldBe emptyList()
                    messagesOf(1) { should notBe negative() } shouldBe emptyList()
                }

                should("reject a value below zero") {
                    messagesOf(-1) { should notBe negative() } shouldBe listOf("should not be negative")
                }
            }

            context("across numeric types") {
                should("judge a Long") {
                    messagesOf(-1L) { should be negative() } shouldBe emptyList()
                    messagesOf(1L) { should be negative() }.size shouldBe 1
                }

                should("judge a Double") {
                    messagesOf(-0.5) { should be negative() } shouldBe emptyList()
                    messagesOf(0.5) { should be negative() }.size shouldBe 1
                }

                should("judge a Float") {
                    messagesOf(-0.5f) { should be negative() } shouldBe emptyList()
                }

                should("judge a Short") {
                    messagesOf((-1).toShort()) { should be negative() } shouldBe emptyList()
                    messagesOf(1.toShort()) { should be negative() }.size shouldBe 1
                }

                should("judge a Byte") {
                    messagesOf((-1).toByte()) { should be negative() } shouldBe emptyList()
                }

                should("judge a BigDecimal") {
                    messagesOf(BigDecimal("-0.01")) { should be negative() } shouldBe emptyList()
                    messagesOf(BigDecimal("0.01")) { should be negative() }.size shouldBe 1
                }
            }

            should("stay silent on an absent value") {
                messagesOf<Int?>(null) { should be negative() } shouldBe emptyList()
            }
        },
    )

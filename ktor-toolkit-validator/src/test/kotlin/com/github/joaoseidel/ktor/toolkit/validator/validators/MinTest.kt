package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger

class MinTest :
    ShouldSpec(
        {
            context("be min") {
                should("accept a value above the bound") {
                    messagesOf(11) { should be min(10) } shouldBe emptyList()
                }

                should("accept the bound itself") {
                    messagesOf(10) { should be min(10) } shouldBe emptyList()
                }

                should("reject a value below it") {
                    messagesOf(9) { should be min(10) } shouldBe listOf("should be greater than or equal to 10")
                }
            }

            context("notBe min") {
                should("accept a value below the bound") {
                    messagesOf(9) { should notBe min(10) } shouldBe emptyList()
                }

                should("reject a value at or above it") {
                    messagesOf(10) { should notBe min(10) } shouldBe listOf("should not be greater than or equal to 10")
                }
            }

            context("across numeric types") {
                should("compare a Long") {
                    messagesOf(10L) { should be min(10) } shouldBe emptyList()
                    messagesOf(9L) { should be min(10) }.size shouldBe 1
                }

                should("compare a Double") {
                    messagesOf(10.5) { should be min(10) } shouldBe emptyList()
                    messagesOf(9.5) { should be min(10) }.size shouldBe 1
                }

                should("compare a Float") {
                    messagesOf(10.5f) { should be min(10) } shouldBe emptyList()
                }

                should("compare a Short") {
                    messagesOf(10.toShort()) { should be min(10) } shouldBe emptyList()
                    messagesOf(9.toShort()) { should be min(10) }.size shouldBe 1
                }

                should("compare a Byte") {
                    messagesOf(10.toByte()) { should be min(10) } shouldBe emptyList()
                    messagesOf(9.toByte()) { should be min(10) }.size shouldBe 1
                }

                should("compare a BigDecimal") {
                    messagesOf(BigDecimal("10.01")) { should be min(10) } shouldBe emptyList()
                    messagesOf(BigDecimal("9.99")) { should be min(10) }.size shouldBe 1
                }

                should("compare a BigInteger") {
                    messagesOf(BigInteger("11")) { should be min(10) } shouldBe emptyList()
                    messagesOf(BigInteger("9")) { should be min(10) }.size shouldBe 1
                }

                should("compare against a bound of a different type") {
                    messagesOf(10) { should be min(9.5) } shouldBe emptyList()
                    messagesOf(10) { should be min(10.5) }.size shouldBe 1
                }

                should("keep the precision of an arbitrary-precision bound") {
                    messagesOf(10) { should be min(BigDecimal("9.99")) } shouldBe emptyList()
                    messagesOf(10) { should be min(BigDecimal("10.01")) }.size shouldBe 1
                    messagesOf(10) { should be min(BigInteger("11")) }.size shouldBe 1
                }

                should("widen a fractional value against an arbitrary-precision bound") {
                    messagesOf(10.5) { should be min(BigDecimal("10.4")) } shouldBe emptyList()
                    messagesOf(10.5) { should be min(BigDecimal("10.6")) }.size shouldBe 1
                    messagesOf(10.5f) { should be min(BigDecimal("10.4")) } shouldBe emptyList()
                    messagesOf(10.5f) { should be min(BigDecimal("10.6")) }.size shouldBe 1
                }
            }

            should("stay silent on an absent value") {
                messagesOf<Int?>(null) { should be min(10) } shouldBe emptyList()
            }
        },
    )

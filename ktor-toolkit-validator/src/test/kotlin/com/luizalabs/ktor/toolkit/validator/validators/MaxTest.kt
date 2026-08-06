package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.math.BigInteger

class MaxTest :
    ShouldSpec({
        context("be max") {
            should("accept a value below the bound") {
                messagesOf(9) { should be max(10) } shouldBe emptyList()
            }

            should("accept the bound itself") {
                messagesOf(10) { should be max(10) } shouldBe emptyList()
            }

            should("reject a value above it") {
                messagesOf(11) { should be max(10) } shouldBe listOf("should be less than or equal to 10")
            }
        }

        context("notBe max") {
            should("accept a value above the bound") {
                messagesOf(11) { should notBe max(10) } shouldBe emptyList()
            }

            should("reject a value at or below it") {
                messagesOf(10) { should notBe max(10) } shouldBe listOf("should not be less than or equal to 10")
            }
        }

        context("across numeric types") {
            should("compare a Long") {
                messagesOf(10L) { should be max(10) } shouldBe emptyList()
                messagesOf(11L) { should be max(10) }.size shouldBe 1
            }

            should("compare a Double") {
                messagesOf(9.5) { should be max(10) } shouldBe emptyList()
                messagesOf(10.5) { should be max(10) }.size shouldBe 1
            }

            should("compare a Float") {
                messagesOf(9.5f) { should be max(10) } shouldBe emptyList()
            }

            should("compare a Short") {
                messagesOf(10.toShort()) { should be max(10) } shouldBe emptyList()
                messagesOf(11.toShort()) { should be max(10) }.size shouldBe 1
            }

            should("compare a Byte") {
                messagesOf(10.toByte()) { should be max(10) } shouldBe emptyList()
                messagesOf(11.toByte()) { should be max(10) }.size shouldBe 1
            }

            should("compare a BigDecimal") {
                messagesOf(BigDecimal("9.99")) { should be max(10) } shouldBe emptyList()
                messagesOf(BigDecimal("10.01")) { should be max(10) }.size shouldBe 1
            }

            should("compare a BigInteger") {
                messagesOf(BigInteger("9")) { should be max(10) } shouldBe emptyList()
                messagesOf(BigInteger("11")) { should be max(10) }.size shouldBe 1
            }

            should("compare against a bound of a different type") {
                messagesOf(10) { should be max(10.5) } shouldBe emptyList()
                messagesOf(10) { should be max(9.5) }.size shouldBe 1
            }
        }

        should("stay silent on an absent value") {
            messagesOf<Int?>(null) { should be max(10) } shouldBe emptyList()
        }
    })

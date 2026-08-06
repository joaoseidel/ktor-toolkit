package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.and
import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class SatisfyingTest :
    ShouldSpec({
        context("be satisfying") {
            should("accept a value the predicate approves of") {
                messagesOf(4) { should be satisfying("should be even") { it % 2 == 0 } } shouldBe emptyList()
            }

            should("record the message otherwise") {
                messagesOf(3) { should be satisfying("should be even") { it % 2 == 0 } } shouldBe
                    listOf("should be even")
            }

            should("see the value at its declared type") {
                messagesOf("kotlin") { should be satisfying("should start with k") { it.startsWith("k") } } shouldBe
                    emptyList()
            }
        }

        context("notBe satisfying") {
            should("invert the predicate, keeping the same message") {
                messagesOf(4) { should notBe satisfying("should be even") { it % 2 == 0 } } shouldBe
                    listOf("should be even")
            }
        }

        context("composition") {
            should("combine with a named rule") {
                messagesOf(6) { should be (min(4) and satisfying("should be even") { it % 2 == 0 }) } shouldBe
                    emptyList()
            }

            should("report the composed message when it fails") {
                messagesOf(3) { should be (min(4) and satisfying("should be even") { it % 2 == 0 }) } shouldBe
                    listOf("should be greater than or equal to 4 and should be even")
            }
        }

        should("never see an absent value") {
            var called = false
            val rule = { value: Int ->
                called = true
                value % 2 == 0
            }

            messagesOf<Int?>(null) { should be satisfying("should be even", rule) } shouldBe emptyList()

            called shouldBe false
        }
    })

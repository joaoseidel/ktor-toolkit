package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class BlankTest :
    ShouldSpec({
        context("be blank") {
            listOf("", " ", "\t", "\n", "   ").forEach { value ->
                should("accept ${value.ifEmpty { "an empty string" }}") {
                    messagesOf(value) { should be blank() } shouldBe emptyList()
                }
            }

            should("reject a string with content") {
                messagesOf(" a ") { should be blank() } shouldBe listOf("should be blank")
            }
        }

        context("notBe blank") {
            should("accept a string with content") {
                messagesOf("a") { should notBe blank() } shouldBe emptyList()
            }

            should("reject a whitespace-only string") {
                messagesOf("  ") { should notBe blank() } shouldBe listOf("should not be blank")
            }
        }

        should("stay silent on an absent value") {
            messagesOf<String?>(null) { should notBe blank() } shouldBe emptyList()
        }
    })

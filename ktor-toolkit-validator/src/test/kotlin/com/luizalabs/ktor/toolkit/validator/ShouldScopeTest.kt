package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.support.errorsOf
import com.luizalabs.ktor.toolkit.validator.support.messagesOf
import com.luizalabs.ktor.toolkit.validator.validators.blank
import com.luizalabs.ktor.toolkit.validator.validators.email
import com.luizalabs.ktor.toolkit.validator.validators.nil
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class ShouldScopeTest :
    ShouldSpec({
        context("be") {
            should("stay quiet when the rule holds") {
                messagesOf("a@b.com") { should be email() } shouldBe emptyList()
            }

            should("record the positive message when the rule does not hold") {
                messagesOf("nope") { should be email() } shouldBe listOf("should be a valid email address")
            }
        }

        context("notBe") {
            should("stay quiet when the rule does not hold") {
                messagesOf("nope") { should notBe email() } shouldBe emptyList()
            }

            should("record the negative message when the rule holds") {
                messagesOf("a@b.com") { should notBe email() } shouldBe listOf("should not be a valid email address")
            }
        }

        context("an absent value") {
            should("be ignored by a rule with no opinion about absence") {
                messagesOf<String?>(null) { should be email() } shouldBe emptyList()
                messagesOf<String?>(null) { should notBe blank() } shouldBe emptyList()
            }

            should("still be seen by a rule that opts in") {
                messagesOf<String?>(null) { should notBe nil() } shouldBe listOf("should not be null")
                messagesOf<String?>(null) { should be nil() } shouldBe emptyList()
            }

            should("let presence and shape be required separately") {
                messagesOf<String?>(null) {
                    should notBe nil()
                    should be email()
                } shouldBe listOf("should not be null")
            }
        }

        context("errors") {
            should("accumulate in the order the rules were asserted") {
                messagesOf("nope") {
                    should be email()
                    should be blank()
                } shouldBe listOf("should be a valid email address", "should be blank")
            }

            should("be recorded under the property path") {
                errorsOf("nope") { should be email() }.map { it.propertyPath } shouldBe listOf("value")
            }
        }

        context("describedAs on an outcome") {
            should("reword the error the assertion recorded") {
                messagesOf("nope") { should be email() describedAs "should be a work address" } shouldBe
                    listOf("should be a work address")
            }

            should("do nothing when the assertion held") {
                messagesOf("a@b.com") { should be email() describedAs "should be a work address" } shouldBe emptyList()
            }

            should("reword only its own error") {
                messagesOf("nope") {
                    should be email() describedAs "should be a work address"
                    should be blank()
                } shouldBe listOf("should be a work address", "should be blank")
            }
        }
    })

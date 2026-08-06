package com.github.joaoseidel.ktor.toolkit.validator

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import com.github.joaoseidel.ktor.toolkit.validator.validators.blank
import com.github.joaoseidel.ktor.toolkit.validator.validators.email
import com.github.joaoseidel.ktor.toolkit.validator.validators.nil
import com.github.joaoseidel.ktor.toolkit.validator.validators.size
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class ValidationRuleTest :
    ShouldSpec({
        context("validationRule") {
            should("never hand a null value to the predicate") {
                var sawValue = false
                val rule =
                    validationRule<String>("should be seen", "should not be seen") {
                        sawValue = true
                        true
                    }

                messagesOf<String?>(null) { should be rule }

                sawValue shouldBe false
            }

            should("build a rule that stays silent on an absent value") {
                messagesOf<String?>(null) { should be email() } shouldBe emptyList()
            }
        }

        context("and") {
            should("hold only when both operands hold") {
                messagesOf("ab") { should be (blank() and size(max = 1)) }.size shouldBe 1
                messagesOf("") { should be (blank() and size(max = 1)) } shouldBe emptyList()
            }

            should("join both messages into one") {
                messagesOf("ab") { should be (blank() and size(max = 1)) } shouldBe
                    listOf("should be blank and size should be between 0 and 1")
            }
        }

        context("or") {
            should("hold when either operand holds") {
                messagesOf("") { should be (blank() or email()) } shouldBe emptyList()
                messagesOf("a@b.com") { should be (blank() or email()) } shouldBe emptyList()
            }

            should("fail when neither operand holds") {
                messagesOf("not an email") { should be (blank() or email()) } shouldBe
                    listOf("should be blank or should be a valid email address")
            }

            should("keep an operand's opinion about absence") {
                // `nil` applies to null, so the composition does too.
                messagesOf<String?>(null) { should be (nil() or blank()) } shouldBe emptyList()
            }
        }

        context("not") {
            should("invert the rule and swap its messages") {
                messagesOf("") { should be !blank() } shouldBe listOf("should not be blank")
                messagesOf("a") { should be !blank() } shouldBe emptyList()
            }

            should("be the same as asserting the rule with notBe") {
                messagesOf("") { should be !blank() } shouldBe messagesOf("") { should notBe blank() }
            }
        }

        context("describedAs on a rule") {
            should("replace both messages") {
                messagesOf("a") { should be (blank() describedAs "should be empty") } shouldBe
                    listOf("should be empty")
                messagesOf("") { should notBe (blank() describedAs "should be empty") } shouldBe
                    listOf("should be empty")
            }

            should("apply to a whole composition") {
                messagesOf("x") { should be (blank() or email() describedAs "should be empty or an address") } shouldBe
                    listOf("should be empty or an address")
            }
        }
    })

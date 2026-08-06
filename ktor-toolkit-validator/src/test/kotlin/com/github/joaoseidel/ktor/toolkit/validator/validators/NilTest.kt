package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class NilTest :
    ShouldSpec(
        {
            context("be nil") {
                should("accept an absent value") {
                    messagesOf<String?>(null) { should be nil() } shouldBe emptyList()
                }

                should("reject a present one") {
                    messagesOf<String?>("a") { should be nil() } shouldBe listOf("should be null")
                }
            }

            context("notBe nil") {
                should("accept a present value") {
                    messagesOf<String?>("a") { should notBe nil() } shouldBe emptyList()
                }

                should("reject an absent one") {
                    messagesOf<String?>(null) { should notBe nil() } shouldBe listOf("should not be null")
                }
            }

            context("across property types") {
                should("apply to any of them") {
                    messagesOf<Int?>(null) { should notBe nil() } shouldBe listOf("should not be null")
                    messagesOf<List<String>?>(null) { should notBe nil() } shouldBe listOf("should not be null")
                    messagesOf<Any?>(Unit) { should be nil() } shouldBe listOf("should be null")
                }

                should("hold trivially on a non-nullable property") {
                    messagesOf("a") { should notBe nil() } shouldBe emptyList()
                }
            }
        },
    )

package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class EmailTest :
    ShouldSpec({
        val valid =
            listOf(
                "user@example.com",
                "user.name@example.com",
                "user+tag@example.com",
                "user@subdomain.example.com",
                "user@example.co.uk",
                "12345@example.com",
                "user@example-domain.com",
                "user@example.technology",
            )

        val invalid =
            listOf(
                "user@",
                "@example.com",
                "user@.com",
                "user@example.",
                "user@exam@ple.com",
                "user example.com",
                "user@exam ple.com",
                "user@example..com",
            )

        context("be email") {
            valid.forEach { address ->
                should("accept $address") {
                    messagesOf(address) { should be email() } shouldBe emptyList()
                }
            }

            invalid.forEach { address ->
                should("reject $address") {
                    messagesOf(address) { should be email() } shouldBe listOf("should be a valid email address")
                }
            }
        }

        context("notBe email") {
            should("accept something that is not an address") {
                messagesOf("nope") { should notBe email() } shouldBe emptyList()
            }

            should("reject an address") {
                messagesOf("user@example.com") { should notBe email() } shouldBe
                    listOf("should not be a valid email address")
            }
        }

        should("stay silent on an absent value") {
            messagesOf<String?>(null) { should be email() } shouldBe emptyList()
        }
    })

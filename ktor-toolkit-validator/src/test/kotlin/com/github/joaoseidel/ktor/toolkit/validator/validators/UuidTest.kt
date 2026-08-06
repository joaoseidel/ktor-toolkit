@file:OptIn(ExperimentalUuidApi::class)

package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class UuidTest :
    ShouldSpec({
        context("be uuid, on a string") {
            listOf(
                "123e4567-e89b-12d3-a456-426614174000",
                "00000000-0000-0000-0000-000000000000",
                "123E4567-E89B-12D3-A456-426614174000",
            ).forEach { value ->
                should("accept $value") {
                    messagesOf(value) { should be uuid() } shouldBe emptyList()
                }
            }

            listOf(
                "not-a-uuid",
                "",
                "123e4567-e89b-12d3-a456",
                "123e4567e89b12d3a456426614174000",
                "123e4567-e89b-12d3-a456-42661417400g",
            ).forEach { value ->
                should("reject ${value.ifEmpty { "an empty string" }}") {
                    messagesOf(value) { should be uuid() } shouldBe listOf("should be a valid UUID")
                }
            }
        }

        context("notBe uuid, on a string") {
            should("accept something that is not a UUID") {
                messagesOf("nope") { should notBe uuid() } shouldBe emptyList()
            }

            should("reject a UUID") {
                messagesOf("123e4567-e89b-12d3-a456-426614174000") { should notBe uuid() } shouldBe
                    listOf("should not be a valid UUID")
            }
        }

        context("on a typed identifier") {
            should("hold for a java.util.UUID") {
                messagesOf(UUID.randomUUID()) { should be uuid() } shouldBe emptyList()
            }

            should("hold for a kotlin.uuid.Uuid") {
                messagesOf(Uuid.random()) { should be uuid() } shouldBe emptyList()
            }

            should("fail the negated assertion, since the type guarantees the shape") {
                messagesOf(UUID.randomUUID()) { should notBe uuid() } shouldBe listOf("should not be a valid UUID")
            }
        }

        should("stay silent on an absent value") {
            messagesOf<String?>(null) { should be uuid() } shouldBe emptyList()
        }
    })

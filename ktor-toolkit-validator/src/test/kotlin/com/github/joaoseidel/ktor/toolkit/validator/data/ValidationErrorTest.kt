package com.github.joaoseidel.ktor.toolkit.validator.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ValidationErrorTest :
    ShouldSpec({
        context("reading an error back as text") {
            should("quote the property the error belongs to") {
                ValidationError("user.email", "should be a valid email address").toString() shouldBe
                    "`user.email` should be a valid email address"
            }

            should("drop the quoting when the error belongs to the object itself") {
                ValidationError("", "should not end before it starts").toString() shouldBe
                    "should not end before it starts"
            }
        }

        context("comparing two errors") {
            val error = ValidationError("user.email", "should be a valid email address")

            should("consider an error equal to itself") {
                error.equals(error) shouldBe true
            }

            should("consider an error equal to a structurally identical one") {
                error shouldBe error.copy()
                error.hashCode() shouldBe error.copy().hashCode()
            }

            should("consider an error different from anything that is not one") {
                error.equals("`user.email` should be a valid email address") shouldBe false
            }

            should("consider errors different when any single detail differs") {
                val variants =
                    mapOf(
                        "the property path" to error.copy(propertyPath = "user.name"),
                        "the message" to error.copy(message = "should not be blank"),
                    )

                variants.forEach { (detail, variant) ->
                    withClue("differing in $detail") { error shouldNotBe variant }
                }
            }

            should("hand its parts back in declaration order") {
                val (propertyPath, message) = error

                propertyPath shouldBe "user.email"
                message shouldBe "should be a valid email address"
            }
        }

        context("serializing an error") {
            should("survive a round trip") {
                val error = ValidationError("user.email", "should be a valid email address")

                Json.decodeFromString<ValidationError>(Json.encodeToString(error)) shouldBe error
            }

            should("write out both the path and the message") {
                Json.encodeToString(ValidationError("title", "should not be blank")) shouldBe
                    """{"propertyPath":"title","message":"should not be blank"}"""
            }

            should("refuse a payload that leaves either of them out") {
                listOf("""{"propertyPath":"title"}""", """{"message":"nope"}""").forEach { payload ->
                    withClue(payload) {
                        shouldThrow<SerializationException> { Json.decodeFromString<ValidationError>(payload) }
                    }
                }
            }
        }
    })

package com.luizalabs.ktor.toolkit.validator.data

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class ValidationErrorTest :
    ShouldSpec({
        context("ValidationError") {
            context("string representation") {
                should("format message with backticks around property path") {
                    val propertyPath = "user.email"
                    val message = "should be valid"
                    val error = ValidationError(propertyPath, message)

                    val result = error.toString()

                    result shouldBe "`$propertyPath` $message"
                }

                should("drop the backticks when the error belongs to the object itself") {
                    ValidationError("", "should not end before it starts").toString() shouldBe
                        "should not end before it starts"
                }
            }
        }
    })

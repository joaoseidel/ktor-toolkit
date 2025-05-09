package com.luizalabs.ktor.toolkit.validator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.plugins.requestvalidation.ValidationResult

class KtorRequestValidationConfigExtensionTest :
    ShouldSpec({
        context("withValidationContext extension method") {
            data class TestRequest(
                val name: String,
            )

            class TestValidator : RequestValidator<TestRequest> {
                override fun ValidationContext<TestRequest>.validate() {
                    property(TestRequest::name) {
                        if (propertyValue.isBlank()) {
                            addError("should not be blank")
                        }
                    }
                }
            }

            context("functionality testing") {
                should("process block validation and create correct ValidationResult") {
                    val validRequest = TestRequest("valid name")
                    val validContext = ValidationContext(validRequest)
                    validContext.property(TestRequest::name) {
                        if (propertyValue.isBlank()) {
                            addError("test error")
                        }
                    }

                    val validResult =
                        if (validContext.getErrors().isEmpty()) {
                            ValidationResult.Valid
                        } else {
                            ValidationResult.Invalid(validContext.getErrors().map { it.toString() })
                        }

                    validResult shouldBe ValidationResult.Valid

                    val invalidRequest = TestRequest("")
                    val invalidContext = ValidationContext(invalidRequest)
                    invalidContext.property(TestRequest::name) {
                        if (propertyValue.isBlank()) {
                            addError("test error")
                        }
                    }

                    val invalidResult =
                        if (invalidContext.getErrors().isEmpty()) {
                            ValidationResult.Valid
                        } else {
                            ValidationResult.Invalid(invalidContext.getErrors().map { it.toString() })
                        }

                    invalidResult.shouldBeInstanceOf<ValidationResult.Invalid>()
                    (invalidResult as ValidationResult.Invalid).reasons shouldContain "`name` test error"
                }

                should("process RequestValidator and create correct ValidationResult") {
                    val validator = TestValidator()

                    val validRequest = TestRequest("valid name")
                    val validContext = ValidationContext(validRequest)
                    with(validator) {
                        validContext.validate()
                    }

                    val validResult =
                        if (validContext.getErrors().isEmpty()) {
                            ValidationResult.Valid
                        } else {
                            ValidationResult.Invalid(validContext.getErrors().map { it.toString() })
                        }

                    validResult shouldBe ValidationResult.Valid

                    val invalidRequest = TestRequest("")
                    val invalidContext = ValidationContext(invalidRequest)
                    with(validator) {
                        invalidContext.validate()
                    }

                    val invalidResult =
                        if (invalidContext.getErrors().isEmpty()) {
                            ValidationResult.Valid
                        } else {
                            ValidationResult.Invalid(invalidContext.getErrors().map { it.toString() })
                        }

                    invalidResult.shouldBeInstanceOf<ValidationResult.Invalid>()
                    (invalidResult as ValidationResult.Invalid).reasons shouldContain "`name` should not be blank"
                }
            }
        }
    })

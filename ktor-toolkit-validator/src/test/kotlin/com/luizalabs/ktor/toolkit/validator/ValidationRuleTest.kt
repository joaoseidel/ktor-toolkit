package com.luizalabs.ktor.toolkit.validator

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ValidationRuleTest :
    ShouldSpec({
        context("ValidationRule") {
            class TestRule(
                override val positiveMessage: String = "should be valid",
                override val negativeMessage: String = "should not be valid",
            ) : ValidationRule(positiveMessage, negativeMessage) {
                override fun supportedTypes() = listOf(Number::class.java)

                override fun validate(value: Any?) =
                    when (value) {
                        is Int -> value > 0
                        is Long -> value > 0
                        else -> false
                    }
            }

            class SingleTypeRule : ValidationRule() {
                override fun supportedTypes() = listOf(String::class.java)

                override fun validate(value: Any?) = value is String && value.isNotEmpty()
            }

            class AnyTypeRule : ValidationRule() {
                override fun supportedTypes() = emptyList<Class<*>>()

                override fun validate(value: Any?) = value != null
            }

            context("apply method") {
                context("type compatibility") {
                    should("add error message when type is incompatible (single type)") {
                        val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                        every { validator.propertyValue } returns 123

                        val rule = SingleTypeRule()
                        rule.apply(validator, false)

                        verify { validator.addError("should be of type String") }
                    }

                    should("add error message when type is incompatible (multiple types)") {
                        val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                        every { validator.propertyValue } returns "not a number"

                        val rule = TestRule()
                        rule.apply(validator, false)

                        verify { validator.addError("should be of type Number") }
                    }

                    should("not check type compatibility when supportedTypes is empty") {
                        val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                        every { validator.propertyValue } returns "any value"

                        val rule = AnyTypeRule()
                        rule.apply(validator, false)

                        verify(exactly = 0) { validator.addError(any()) }
                    }
                }

                context("validation result handling") {
                    should("add positive error message when value is invalid and negate is false") {
                        val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                        every { validator.propertyValue } returns -5

                        val rule = TestRule()
                        rule.apply(validator, false)

                        verify { validator.addError("should be valid") }
                    }

                    should("add negative error message when value is valid and negate is true") {
                        val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                        every { validator.propertyValue } returns 5

                        val rule = TestRule()
                        rule.apply(validator, true)

                        verify { validator.addError("should not be valid") }
                    }

                    should("not add error message when value is valid and negate is false") {
                        val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                        every { validator.propertyValue } returns 5

                        val rule = TestRule()
                        rule.apply(validator, false)

                        verify(exactly = 0) { validator.addError(any()) }
                    }

                    should("not add error message when value is invalid and negate is true") {
                        val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                        every { validator.propertyValue } returns -5

                        val rule = TestRule()
                        rule.apply(validator, true)

                        verify(exactly = 0) { validator.addError(any()) }
                    }
                }

                should("return the validator instance") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    every { validator.propertyValue } returns 5

                    val rule = TestRule()
                    val result = rule.apply(validator, false)

                    result shouldBe validator
                }
            }
        }
    })

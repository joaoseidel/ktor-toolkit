package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class BlankTest :
    ShouldSpec({
        context("blank validator") {
            context("type compatibility") {
                should("support String type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.blank()

                    rule.supportedTypes() shouldContainExactly listOf(String::class.java)
                }

                should("reject non-String types") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "number"
                    every { property.get(target) } returns 123

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.blank())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be of type String"
                }
            }

            context("validation") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "text"

                context("be context") {
                    should("accept empty string as valid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns ""

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.be(validator.blank())

                        errors shouldBe emptyList()
                    }

                    should("accept whitespace string as valid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "   "

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.be(validator.blank())

                        errors shouldBe emptyList()
                    }

                    should("reject non-blank string as invalid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "not blank"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.be(validator.blank())

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should be blank"
                    }
                }

                context("notBe context") {
                    should("accept non-blank string as valid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "not blank"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.blank())

                        errors shouldBe emptyList()
                    }

                    should("reject empty string as invalid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns ""

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.blank())

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be blank"
                    }

                    should("reject whitespace string as invalid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "   "

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.blank())

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be blank"
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "text"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "not blank"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.blank())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be blank"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns ""

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.blank())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be blank"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "text"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns "not blank"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.blank(
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns ""

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.blank(
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

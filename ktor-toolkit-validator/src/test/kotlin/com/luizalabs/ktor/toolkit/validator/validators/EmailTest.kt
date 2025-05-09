package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class EmailTest :
    ShouldSpec({
        context("email validator") {
            context("type compatibility") {
                should("support String type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.email()

                    rule.supportedTypes() shouldContainExactly listOf(String::class.java)
                }

                should("reject non-String types") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "number"
                    every { property.get(target) } returns 123

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.email())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be of type String"
                }
            }

            context("validation") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "email"

                context("be context") {
                    val validEmails =
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

                    validEmails.forEach { validEmail ->
                        should("accept $validEmail as valid") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns validEmail

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.email())

                            errors shouldBe emptyList()
                        }
                    }

                    val invalidEmails =
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

                    invalidEmails.forEach { invalidEmail ->
                        should("reject $invalidEmail as invalid") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns invalidEmail

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.email())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a valid email address"
                        }
                    }
                }

                context("notBe context") {
                    should("pass for invalid email") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "not-an-email"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.email())

                        errors shouldBe emptyList()
                    }

                    should("fail for valid email") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "user@example.com"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.email())

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be a valid email address"
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "email"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "not-an-email"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.email())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be a valid email address"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "user@example.com"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.email())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be a valid email address"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()

                every { property.name } returns "email"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns "not-an-email"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.email(
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns "user@example.com"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.email(
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class NilTest :
    ShouldSpec({
        context("nil validator") {
            context("type compatibility") {
                should("support any type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.nil()

                    rule.supportedTypes().shouldBeEmpty()
                }
            }

            context("validation") {
                val property = mockk<KProperty1<Any, String?>>()
                val target = mockk<Any>()
                every { property.name } returns "nullable"

                context("be context") {
                    should("accept null value as valid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns null

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.be(validator.nil())

                        errors shouldBe emptyList()
                    }

                    should("reject non-null value as invalid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "not null"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.be(validator.nil())

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should be null"
                    }
                }

                context("notBe context") {
                    should("accept non-null value as valid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "not null"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.nil())

                        errors shouldBe emptyList()
                    }

                    should("reject null value as invalid") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns null

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.nil())

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be null"
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, String?>>()
                val target = mockk<Any>()
                every { property.name } returns "nullable"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "not null"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.nil())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be null"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns null

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.nil())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be null"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, String?>>()
                val target = mockk<Any>()
                every { property.name } returns "nullable"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns "not null"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.nil(
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns null

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.nil(
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

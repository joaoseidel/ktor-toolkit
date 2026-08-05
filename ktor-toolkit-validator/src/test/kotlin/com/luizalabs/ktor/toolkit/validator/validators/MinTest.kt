package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class MinTest :
    ShouldSpec({
        context("min validator") {
            val minValue = 10

            context("type compatibility") {
                should("support Number type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.min(minValue)

                    rule.supportedTypes() shouldContainExactly listOf(Number::class.java)
                }

                should("reject non-Number types") {
                    val property = mockk<KProperty1<Any, String>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "text"
                    every { property.get(target) } returns "not a number"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.min(minValue))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be of type Number"
                }
            }

            context("validation") {
                context("be context") {
                    context("Integer type") {
                        val property = mockk<KProperty1<Any, Int>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value equal to minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors shouldBe emptyList()
                        }

                        should("accept value above minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue + 5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value below minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue - 1

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be greater than or equal to $minValue"
                        }
                    }

                    context("Float type") {
                        val property = mockk<KProperty1<Any, Float>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value above minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue + 0.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value below minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue - 0.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be greater than or equal to $minValue"
                        }
                    }

                    context("Double type") {
                        val property = mockk<KProperty1<Any, Double>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value above minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue + 0.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value below minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue - 0.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be greater than or equal to $minValue"
                        }
                    }

                    context("Long type") {
                        val property = mockk<KProperty1<Any, Long>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value above minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue + 5L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value below minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns minValue - 1L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.min(minValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be greater than or equal to $minValue"
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    every { property.name } returns "value"

                    should("reject value above minimum") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns minValue + 5

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.min(minValue))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be greater than or equal to $minValue"
                    }

                    should("reject value equal to minimum") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns minValue

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.min(minValue))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be greater than or equal to $minValue"
                    }

                    should("accept value below minimum") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns minValue - 1

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.min(minValue))

                        errors shouldBe emptyList()
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, Int>>()
                val target = mockk<Any>()
                every { property.name } returns "value"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns minValue - 1

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.min(minValue))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be greater than or equal to $minValue"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns minValue + 5

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.min(minValue))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be greater than or equal to $minValue"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, Int>>()
                val target = mockk<Any>()
                every { property.name } returns "value"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns minValue - 1

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.min(
                            minValue = minValue,
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns minValue + 5

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.min(
                            minValue = minValue,
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

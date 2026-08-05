package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class MaxTest :
    ShouldSpec({
        context("max validator") {
            val maxValue = 100

            context("type compatibility") {
                should("support Number type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.max(maxValue)

                    rule.supportedTypes() shouldContainExactly listOf(Number::class.java)
                }

                should("reject non-Number types") {
                    val property = mockk<KProperty1<Any, String>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "text"
                    every { property.get(target) } returns "not a number"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.max(maxValue))

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

                        should("accept value equal to maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors shouldBe emptyList()
                        }

                        should("accept value below maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue - 10

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value above maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue + 10

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be less than or equal to $maxValue"
                        }
                    }

                    context("Float type") {
                        val property = mockk<KProperty1<Any, Float>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value below maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue - 10.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value above maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue + 10.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be less than or equal to $maxValue"
                        }
                    }

                    context("Double type") {
                        val property = mockk<KProperty1<Any, Double>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value below maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue - 10.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value above maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue + 10.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be less than or equal to $maxValue"
                        }
                    }

                    context("Long type") {
                        val property = mockk<KProperty1<Any, Long>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value below maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue - 10L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors shouldBe emptyList()
                        }

                        should("reject value above maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns maxValue + 10L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.max(maxValue))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be less than or equal to $maxValue"
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    every { property.name } returns "value"

                    should("reject value below maximum") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns maxValue - 10

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.max(maxValue))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be less than or equal to $maxValue"
                    }

                    should("reject value equal to maximum") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns maxValue

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.max(maxValue))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be less than or equal to $maxValue"
                    }

                    should("accept value above maximum") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns maxValue + 10

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.max(maxValue))

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
                    every { property.get(target) } returns maxValue + 10

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.max(maxValue))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be less than or equal to $maxValue"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns maxValue - 10

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.max(maxValue))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be less than or equal to $maxValue"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, Int>>()
                val target = mockk<Any>()
                every { property.name } returns "value"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns maxValue + 10

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.max(
                            maxValue = maxValue,
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns maxValue - 10

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.max(
                            maxValue = maxValue,
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

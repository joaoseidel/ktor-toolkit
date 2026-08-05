package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class NegativeTest :
    ShouldSpec({
        context("negative validator") {
            context("type compatibility") {
                should("support Number type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.negative()

                    rule.supportedTypes() shouldContainExactly listOf(Number::class.java)
                }

                should("reject non-Number types") {
                    val property = mockk<KProperty1<Any, String>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "text"
                    every { property.get(target) } returns "not a number"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.negative())

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

                        should("accept negative value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns -5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors shouldBe emptyList()
                        }

                        should("reject zero") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 0

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be negative"
                        }

                        should("reject positive value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be negative"
                        }
                    }

                    context("Float type") {
                        val property = mockk<KProperty1<Any, Float>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept negative value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns -5.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors shouldBe emptyList()
                        }

                        should("reject positive value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 5.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be negative"
                        }
                    }

                    context("Double type") {
                        val property = mockk<KProperty1<Any, Double>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept negative value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns -5.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors shouldBe emptyList()
                        }

                        should("reject positive value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 5.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be negative"
                        }
                    }

                    context("Long type") {
                        val property = mockk<KProperty1<Any, Long>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept negative value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns -5L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors shouldBe emptyList()
                        }

                        should("reject positive value") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 5L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.negative())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be negative"
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    every { property.name } returns "value"

                    should("reject negative value") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns -5

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.negative())

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be negative"
                    }

                    should("accept zero") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns 0

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.negative())

                        errors shouldBe emptyList()
                    }

                    should("accept positive value") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns 5

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.negative())

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
                    every { property.get(target) } returns 5

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.negative())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be negative"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns -5

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.negative())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be negative"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, Int>>()
                val target = mockk<Any>()
                every { property.name } returns "value"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns 5

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.negative(
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns -5

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.negative(
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

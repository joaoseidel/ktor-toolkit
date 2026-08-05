package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class InRangeTest :
    ShouldSpec({
        context("inRange validator") {
            val min = 10
            val max = 20

            context("type compatibility") {
                should("support Number type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.inRange(min, max)

                    rule.supportedTypes() shouldContainExactly listOf(Number::class.java)
                }

                should("reject non-Number types") {
                    val property = mockk<KProperty1<Any, String>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "text"
                    every { property.get(target) } returns "not a number"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.inRange(min, max))

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

                        should("accept value at lower bound") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns min

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors shouldBe emptyList()
                        }

                        should("accept value at upper bound") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns max

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors shouldBe emptyList()
                        }

                        should("accept value within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 15

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject value below range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns min - 1

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be in range of $min..$max"
                        }

                        should("reject value above range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns max + 1

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be in range of $min..$max"
                        }
                    }

                    context("Float type") {
                        val property = mockk<KProperty1<Any, Float>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 15.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject value outside range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 25.5f

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be in range of $min..$max"
                        }
                    }

                    context("Double type") {
                        val property = mockk<KProperty1<Any, Double>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 15.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject value outside range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 25.5

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be in range of $min..$max"
                        }
                    }

                    context("Long type") {
                        val property = mockk<KProperty1<Any, Long>>()
                        val target = mockk<Any>()
                        every { property.name } returns "value"

                        should("accept value within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 15L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject value outside range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns 25L

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.inRange(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be in range of $min..$max"
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    every { property.name } returns "value"

                    should("reject value within range") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns 15

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.inRange(min, max))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be in range of $min..$max"
                    }

                    should("accept value outside range") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns 25

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.inRange(min, max))

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
                    validator.should.be(validator.inRange(min, max))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be in range of $min..$max"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns 15

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.inRange(min, max))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be in range of $min..$max"
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
                        validator.inRange(
                            min = min,
                            max = max,
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns 15

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.inRange(
                            min = min,
                            max = max,
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

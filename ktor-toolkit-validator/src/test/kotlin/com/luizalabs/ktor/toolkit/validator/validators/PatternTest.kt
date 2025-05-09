package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class PatternTest :
    ShouldSpec({
        context("pattern validator") {
            val regex = Regex("^[0-9]{5}-[0-9]{3}$")

            context("type compatibility") {
                should("support String type") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.pattern(regex)

                    rule.supportedTypes() shouldContainExactly listOf(String::class.java)
                }

                should("reject non-String types") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "number"
                    every { property.get(target) } returns 12345

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.pattern(regex))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be of type String"
                }
            }

            context("validation") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "postalCode"

                context("be context") {
                    should("accept string matching pattern") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "12345-678"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.be(validator.pattern(regex))

                        errors shouldBe emptyList()
                    }

                    should("reject string not matching pattern") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "invalid"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.be(validator.pattern(regex))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should match pattern \"$regex\""
                    }
                }

                context("notBe context") {
                    should("accept string not matching pattern") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "invalid"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.pattern(regex))

                        errors shouldBe emptyList()
                    }

                    should("reject string matching pattern") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns "12345-678"

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.pattern(regex))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not match pattern \"$regex\""
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "postalCode"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "invalid"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.pattern(regex))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should match pattern \"$regex\""
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "12345-678"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.pattern(regex))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not match pattern \"$regex\""
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "postalCode"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns "invalid"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.pattern(
                            regex = regex,
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns "12345-678"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.pattern(
                            regex = regex,
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

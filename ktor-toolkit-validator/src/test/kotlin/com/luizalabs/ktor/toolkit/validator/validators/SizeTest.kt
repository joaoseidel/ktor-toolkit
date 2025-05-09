package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.KProperty1

class SizeTest :
    ShouldSpec({
        context("size validator") {
            val min = 2
            val max = 5

            context("type compatibility") {
                should("support String, Collection, Array, and Map types") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.size(min, max)

                    rule.supportedTypes() shouldContainExactly
                        listOf(
                            String::class.java,
                            Collection::class.java,
                            Array::class.java,
                            Map::class.java,
                        )
                }

                should("reject unsupported types") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "number"
                    every { property.get(target) } returns 123

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.size(min, max))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be one of these types: String, Collection, Object[], Map"
                }
            }

            context("validation") {
                context("String type") {
                    val property = mockk<KProperty1<Any, String>>()
                    val target = mockk<Any>()
                    every { property.name } returns "text"

                    context("be context") {
                        should("accept string with length within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns "abc"

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors shouldBe emptyList()
                        }

                        should("accept string with length at minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns "ab"

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors shouldBe emptyList()
                        }

                        should("accept string with length at maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns "abcde"

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject string with length below minimum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns "a"

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "size should be between $min and $max"
                        }

                        should("reject string with length above maximum") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns "abcdef"

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "size should be between $min and $max"
                        }
                    }

                    context("notBe context") {
                        should("reject string with length within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns "abc"

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.notBe(validator.size(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "size should not be between $min and $max"
                        }

                        should("accept string with length outside range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns "abcdefgh"

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.notBe(validator.size(min, max))

                            errors shouldBe emptyList()
                        }
                    }
                }

                context("Collection type") {
                    val property = mockk<KProperty1<Any, List<String>>>()
                    val target = mockk<Any>()
                    every { property.name } returns "list"

                    context("be context") {
                        should("accept collection with size within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns listOf("a", "b", "c")

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject collection with size outside range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns listOf("a")

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "size should be between $min and $max"
                        }
                    }
                }

                context("Array type") {
                    val property = mockk<KProperty1<Any, Array<String>>>()
                    val target = mockk<Any>()
                    every { property.name } returns "array"

                    context("be context") {
                        should("accept array with size within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns arrayOf("a", "b", "c")

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject array with size outside range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns arrayOf("a")

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "size should be between $min and $max"
                        }
                    }
                }

                context("Map type") {
                    val property = mockk<KProperty1<Any, Map<String, String>>>()
                    val target = mockk<Any>()
                    every { property.name } returns "map"

                    context("be context") {
                        should("accept map with size within range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns mapOf("a" to "1", "b" to "2", "c" to "3")

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors shouldBe emptyList()
                        }

                        should("reject map with size outside range") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns mapOf("a" to "1")

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.size(min, max))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "size should be between $min and $max"
                        }
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "text"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "a"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.size(min, max))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "size should be between $min and $max"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "abc"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.size(min, max))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "size should not be between $min and $max"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "text"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns "a"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.size(
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
                    every { property.get(target) } returns "abc"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.size(
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

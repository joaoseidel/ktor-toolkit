@file:OptIn(ExperimentalUuidApi::class)

package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class UuidTest :
    ShouldSpec({
        context("uuid validator") {
            context("type compatibility") {
                should("support String and UUID types") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.uuid()

                    rule.supportedTypes() shouldContainExactly
                        listOf(
                            String::class.java,
                            Uuid::class.java,
                            UUID::class.java,
                        )
                }

                should("reject unsupported types") {
                    val property = mockk<KProperty1<Any, Int>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "number"
                    every { property.get(target) } returns 123

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.uuid())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be one of these types: String, Uuid, UUID"
                }
            }

            context("validation") {
                val validUuidString = "550e8400-e29b-41d4-a716-446655440000"
                val validUuid = UUID.fromString(validUuidString)
                val invalidUuidString = "not-a-uuid"

                context("String type") {
                    val property = mockk<KProperty1<Any, String>>()
                    val target = mockk<Any>()
                    every { property.name } returns "uuidString"

                    context("be context") {
                        should("accept valid UUID string") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns validUuidString

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.uuid())

                            errors shouldBe emptyList()
                        }

                        should("reject invalid UUID string") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns invalidUuidString

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.uuid())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a valid UUID"
                        }
                    }

                    context("notBe context") {
                        should("accept invalid UUID string") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns invalidUuidString

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.notBe(validator.uuid())

                            errors shouldBe emptyList()
                        }

                        should("reject valid UUID string") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns validUuidString

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.notBe(validator.uuid())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should not be a valid UUID"
                        }
                    }
                }

                context("UUID type") {
                    val property = mockk<KProperty1<Any, UUID>>()
                    val target = mockk<Any>()
                    every { property.name } returns "uuid"

                    context("be context") {
                        should("always accept UUID instance") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns validUuid

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.uuid())

                            errors shouldBe emptyList()
                        }
                    }

                    context("notBe context") {
                        should("always reject UUID instance") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns validUuid

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.notBe(validator.uuid())

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should not be a valid UUID"
                        }
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "uuid"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "not-a-uuid"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.uuid())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be a valid UUID"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns "550e8400-e29b-41d4-a716-446655440000"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.uuid())

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be a valid UUID"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, String>>()
                val target = mockk<Any>()
                every { property.name } returns "uuid"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns "not-a-uuid"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.uuid(
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns "550e8400-e29b-41d4-a716-446655440000"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.uuid(
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

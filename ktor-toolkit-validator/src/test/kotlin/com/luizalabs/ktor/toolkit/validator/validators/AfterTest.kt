package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.reflect.KProperty1
import kotlin.time.Instant

class AfterTest :
    ShouldSpec({
        context("after validator") {
            val earlierDate = LocalDate.parse("2023-01-01")
            val laterDate = LocalDate.parse("2023-01-02")

            val earlierDateTime = LocalDateTime.parse("2023-01-01T10:00:00")
            val laterDateTime = LocalDateTime.parse("2023-01-01T11:00:00")

            val earlierInstant = Instant.parse("2023-01-01T10:00:00Z")
            val laterInstant = Instant.parse("2023-01-01T11:00:00Z")

            context("type compatibility") {
                should("support date types") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.after(earlierDate)

                    rule.supportedTypes() shouldContainExactly
                        listOf(
                            LocalDateTime::class.java,
                            LocalDate::class.java,
                            Instant::class.java,
                        )
                }

                should("reject non-date types") {
                    val property = mockk<KProperty1<Any, String>>()
                    val target = mockk<Any>()
                    val errors = mutableListOf<ValidationError>()
                    every { property.name } returns "text"
                    every { property.get(target) } returns "not a date"

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.after(earlierDate))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be one of these types: LocalDateTime, LocalDate, Instant"
                }
            }

            context("validation") {
                context("be context") {
                    context("LocalDate type") {
                        val property = mockk<KProperty1<Any, LocalDate>>()
                        val target = mockk<Any>()
                        every { property.name } returns "date"

                        should("accept later date") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns laterDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.after(earlierDate))

                            errors shouldBe emptyList()
                        }

                        should("reject earlier date") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns earlierDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.after(laterDate))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be after $laterDate"
                        }
                    }

                    context("LocalDateTime type") {
                        val property = mockk<KProperty1<Any, LocalDateTime>>()
                        val target = mockk<Any>()
                        every { property.name } returns "dateTime"

                        should("accept later datetime") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns laterDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.after(earlierDateTime))

                            errors shouldBe emptyList()
                        }

                        should("reject earlier datetime") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns earlierDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.after(laterDateTime))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be after $laterDateTime"
                        }
                    }

                    context("Instant type") {
                        val property = mockk<KProperty1<Any, Instant>>()
                        val target = mockk<Any>()
                        every { property.name } returns "instant"

                        should("accept later instant") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns laterInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.after(earlierInstant))

                            errors shouldBe emptyList()
                        }

                        should("reject earlier instant") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns earlierInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.after(laterInstant))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be after $laterInstant"
                        }
                    }

                    context("mixed types") {
                        context("LocalDate and LocalDateTime") {
                            val property = mockk<KProperty1<Any, LocalDate>>()
                            val target = mockk<Any>()
                            every { property.name } returns "date"

                            should("accept later date compared to datetime") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns laterDate

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.after(earlierDateTime))

                                errors shouldBe emptyList()
                            }

                            should("reject earlier date compared to datetime") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns earlierDate

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.after(laterDateTime))

                                errors.size shouldBe 1
                                errors[0].message shouldBe "should be after $laterDateTime"
                            }
                        }

                        context("LocalDateTime and LocalDate") {
                            val property = mockk<KProperty1<Any, LocalDateTime>>()
                            val target = mockk<Any>()
                            every { property.name } returns "dateTime"

                            should("accept later datetime compared to date") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns laterDateTime

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.after(earlierDate))

                                errors shouldBe emptyList()
                            }

                            should("reject earlier datetime compared to date") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns earlierDateTime

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.after(laterDate))

                                errors.size shouldBe 1
                                errors[0].message shouldBe "should be after $laterDate"
                            }
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, LocalDate>>()
                    val target = mockk<Any>()
                    every { property.name } returns "date"

                    should("reject later date") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns laterDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.after(earlierDate))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be after $earlierDate"
                    }

                    should("accept earlier date") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns earlierDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.after(laterDate))

                        errors shouldBe emptyList()
                    }
                }
            }

            context("default error messages") {
                val property = mockk<KProperty1<Any, LocalDate>>()
                val target = mockk<Any>()
                every { property.name } returns "date"

                should("use default positive message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns earlierDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.after(laterDate))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be after $laterDate"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns laterDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.after(earlierDate))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be after $earlierDate"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, LocalDate>>()
                val target = mockk<Any>()
                every { property.name } returns "date"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns earlierDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.after(
                            date = laterDate,
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns laterDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.after(
                            date = earlierDate,
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

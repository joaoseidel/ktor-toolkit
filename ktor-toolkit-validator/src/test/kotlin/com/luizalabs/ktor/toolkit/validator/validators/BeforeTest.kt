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

class BeforeTest :
    ShouldSpec({
        context("before validator") {
            val earlierDate = LocalDate.parse("2023-01-01")
            val laterDate = LocalDate.parse("2023-01-02")

            val earlierDateTime = LocalDateTime.parse("2023-01-01T10:00:00")
            val laterDateTime = LocalDateTime.parse("2023-01-01T11:00:00")

            val earlierInstant = Instant.parse("2023-01-01T10:00:00Z")
            val laterInstant = Instant.parse("2023-01-01T11:00:00Z")

            context("type compatibility") {
                should("support date types") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.before(laterDate)

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
                    validator.should.be(validator.before(laterDate))

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

                        should("accept earlier date") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns earlierDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.before(laterDate))

                            errors shouldBe emptyList()
                        }

                        should("reject later date") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns laterDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.before(earlierDate))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be before $earlierDate"
                        }
                    }

                    context("LocalDateTime type") {
                        val property = mockk<KProperty1<Any, LocalDateTime>>()
                        val target = mockk<Any>()
                        every { property.name } returns "dateTime"

                        should("accept earlier datetime") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns earlierDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.before(laterDateTime))

                            errors shouldBe emptyList()
                        }

                        should("reject later datetime") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns laterDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.before(earlierDateTime))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be before $earlierDateTime"
                        }
                    }

                    context("Instant type") {
                        val property = mockk<KProperty1<Any, kotlin.time.Instant>>()
                        val target = mockk<Any>()
                        every { property.name } returns "instant"

                        should("accept earlier instant") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns earlierInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.before(laterInstant))

                            errors shouldBe emptyList()
                        }

                        should("reject later instant") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns laterInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.before(earlierInstant))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be before $earlierInstant"
                        }
                    }

                    context("mixed types") {
                        context("LocalDate and LocalDateTime") {
                            val property = mockk<KProperty1<Any, LocalDate>>()
                            val target = mockk<Any>()
                            every { property.name } returns "date"

                            should("accept earlier date compared to datetime") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns earlierDate

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.before(laterDateTime))

                                errors shouldBe emptyList()
                            }

                            should("reject later date compared to datetime") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns laterDate

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.before(earlierDateTime))

                                errors.size shouldBe 1
                                errors[0].message shouldBe "should be before $earlierDateTime"
                            }
                        }

                        context("LocalDateTime and LocalDate") {
                            val property = mockk<KProperty1<Any, LocalDateTime>>()
                            val target = mockk<Any>()
                            every { property.name } returns "dateTime"

                            should("accept earlier datetime compared to date") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns earlierDateTime

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.before(laterDate))

                                errors shouldBe emptyList()
                            }

                            should("reject later datetime compared to date") {
                                val errors = mutableListOf<ValidationError>()
                                every { property.get(target) } returns laterDateTime

                                val validator = PropertyValidator(target, property, errors)
                                validator.should.be(validator.before(earlierDate))

                                errors.size shouldBe 1
                                errors[0].message shouldBe "should be before $earlierDate"
                            }
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, LocalDate>>()
                    val target = mockk<Any>()
                    every { property.name } returns "date"

                    should("reject earlier date") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns earlierDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.before(laterDate))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be before $laterDate"
                    }

                    should("accept later date") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns laterDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.before(earlierDate))

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
                    every { property.get(target) } returns laterDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.before(earlierDate))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be before $earlierDate"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    every { property.get(target) } returns earlierDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.before(laterDate))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be before $laterDate"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, LocalDate>>()
                val target = mockk<Any>()
                every { property.name } returns "date"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    every { property.get(target) } returns laterDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.before(
                            date = earlierDate,
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    every { property.get(target) } returns earlierDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.before(
                            date = laterDate,
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.PropertyValidator
import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.reflect.KProperty1
import kotlin.time.Duration
import kotlin.time.Instant

class PastTest :
    ShouldSpec({
        context("past validator") {
            val now = Instant.parse("2023-05-10T12:00:00Z")
            val nowDate = LocalDate.parse("2023-05-10")
            val nowDateTime = LocalDateTime.parse("2023-05-10T12:00:00")

            context("type compatibility") {
                should("support date types") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.past()

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
                    validator.should.be(validator.past())

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

                        should("accept past date") {
                            val errors = mutableListOf<ValidationError>()
                            val pastDate = nowDate.minus(1, DateTimeUnit.DAY)
                            every { property.get(target) } returns pastDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past())

                            errors shouldBe emptyList()
                        }

                        should("reject current date") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns nowDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a past date"
                        }

                        should("reject future date") {
                            val errors = mutableListOf<ValidationError>()
                            val futureDate = nowDate.plus(1, DateTimeUnit.DAY)
                            every { property.get(target) } returns futureDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a past date"
                        }
                    }

                    context("LocalDateTime type") {
                        val property = mockk<KProperty1<Any, LocalDateTime>>()
                        val target = mockk<Any>()
                        every { property.name } returns "dateTime"

                        should("accept past datetime") {
                            val errors = mutableListOf<ValidationError>()
                            val pastDateTime =
                                LocalDateTime(
                                    nowDate.minus(1, DateTimeUnit.DAY),
                                    nowDateTime.time,
                                )
                            every { property.get(target) } returns pastDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject current datetime") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns nowDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a past date"
                        }
                    }

                    context("Instant type") {
                        val property = mockk<KProperty1<Any, Instant>>()
                        val target = mockk<Any>()
                        every { property.name } returns "instant"

                        should("accept past instant") {
                            val errors = mutableListOf<ValidationError>()
                            val pastInstant = now.minus(Duration.parse("1d"))
                            every { property.get(target) } returns pastInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject current instant") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns now

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a past date"
                        }
                    }

                    context("with duration") {
                        val property = mockk<KProperty1<Any, LocalDate>>()
                        val target = mockk<Any>()
                        val duration = Duration.parse("30d")
                        every { property.name } returns "date"

                        should("accept date within duration") {
                            val errors = mutableListOf<ValidationError>()
                            val recentPastDate = nowDate.minus(10, DateTimeUnit.DAY)
                            every { property.get(target) } returns recentPastDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(duration, now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject date beyond duration") {
                            val errors = mutableListOf<ValidationError>()
                            val distantPastDate = nowDate.minus(40, DateTimeUnit.DAY)
                            every { property.get(target) } returns distantPastDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.past(duration, now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a past date of at most $duration"
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, LocalDate>>()
                    val target = mockk<Any>()
                    every { property.name } returns "date"

                    should("reject past date") {
                        val errors = mutableListOf<ValidationError>()
                        val pastDate = nowDate.minus(1, DateTimeUnit.DAY)
                        every { property.get(target) } returns pastDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.past(now = now))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be a past date"
                    }

                    should("accept current or future date") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns nowDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.past(now = now))

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
                    val futureDate = nowDate.plus(1, DateTimeUnit.DAY)
                    every { property.get(target) } returns futureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.past(now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be a past date"
                }

                should("use default positive message with duration") {
                    val errors = mutableListOf<ValidationError>()
                    val duration = Duration.parse("30d")
                    val distantPastDate = nowDate.minus(40, DateTimeUnit.DAY)
                    every { property.get(target) } returns distantPastDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.past(duration, now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be a past date of at most $duration"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val pastDate = nowDate.minus(1, DateTimeUnit.DAY)
                    every { property.get(target) } returns pastDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.past(now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be a past date"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, LocalDate>>()
                val target = mockk<Any>()
                every { property.name } returns "date"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    val futureDate = nowDate.plus(1, DateTimeUnit.DAY)
                    every { property.get(target) } returns futureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.past(
                            positiveMessage = customMessage,
                            now = now,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    val pastDate = nowDate.minus(1, DateTimeUnit.DAY)
                    every { property.get(target) } returns pastDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.past(
                            negativeMessage = customMessage,
                            now = now,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

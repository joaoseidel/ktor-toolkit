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

class WithinTest :
    ShouldSpec({
        context("within validator") {
            val now = Instant.parse("2023-05-10T12:00:00Z")
            val nowDate = LocalDate.parse("2023-05-10")
            val nowDateTime = LocalDateTime.parse("2023-05-10T12:00:00")
            val duration = Duration.parse("7d")

            context("type compatibility") {
                should("support date types") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.within(duration)

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
                    validator.should.be(validator.within(duration, now = now))

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

                        should("accept date within duration in the future") {
                            val errors = mutableListOf<ValidationError>()
                            val nearFutureDate = nowDate.plus(5, DateTimeUnit.DAY)
                            every { property.get(target) } returns nearFutureDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors shouldBe emptyList()
                        }

                        should("accept date within duration in the past") {
                            val errors = mutableListOf<ValidationError>()
                            val nearPastDate = nowDate.minus(5, DateTimeUnit.DAY)
                            every { property.get(target) } returns nearPastDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors shouldBe emptyList()
                        }

                        should("accept current date") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns nowDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject date outside duration in the future") {
                            val errors = mutableListOf<ValidationError>()
                            val farFutureDate = nowDate.plus(10, DateTimeUnit.DAY)
                            every { property.get(target) } returns farFutureDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be within $duration from now"
                        }

                        should("reject date outside duration in the past") {
                            val errors = mutableListOf<ValidationError>()
                            val farPastDate = nowDate.minus(10, DateTimeUnit.DAY)
                            every { property.get(target) } returns farPastDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be within $duration from now"
                        }
                    }

                    context("LocalDateTime type") {
                        val property = mockk<KProperty1<Any, LocalDateTime>>()
                        val target = mockk<Any>()
                        every { property.name } returns "dateTime"

                        should("accept datetime within duration") {
                            val errors = mutableListOf<ValidationError>()
                            val nearFutureDateTime =
                                LocalDateTime(
                                    nowDate.plus(5, DateTimeUnit.DAY),
                                    nowDateTime.time,
                                )
                            every { property.get(target) } returns nearFutureDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject datetime outside duration") {
                            val errors = mutableListOf<ValidationError>()
                            val farFutureDateTime =
                                LocalDateTime(
                                    nowDate.plus(10, DateTimeUnit.DAY),
                                    nowDateTime.time,
                                )
                            every { property.get(target) } returns farFutureDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be within $duration from now"
                        }
                    }

                    context("Instant type") {
                        val property = mockk<KProperty1<Any, Instant>>()
                        val target = mockk<Any>()
                        every { property.name } returns "instant"

                        should("accept instant within duration") {
                            val errors = mutableListOf<ValidationError>()
                            val nearFutureInstant = now.plus(Duration.parse("5d"))
                            every { property.get(target) } returns nearFutureInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject instant outside duration") {
                            val errors = mutableListOf<ValidationError>()
                            val farFutureInstant = now.plus(Duration.parse("10d"))
                            every { property.get(target) } returns farFutureInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.within(duration, now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be within $duration from now"
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, LocalDate>>()
                    val target = mockk<Any>()
                    every { property.name } returns "date"

                    should("reject date within duration") {
                        val errors = mutableListOf<ValidationError>()
                        val nearFutureDate = nowDate.plus(5, DateTimeUnit.DAY)
                        every { property.get(target) } returns nearFutureDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.within(duration, now = now))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be within $duration from now"
                    }

                    should("accept date outside duration") {
                        val errors = mutableListOf<ValidationError>()
                        val farFutureDate = nowDate.plus(10, DateTimeUnit.DAY)
                        every { property.get(target) } returns farFutureDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.within(duration, now = now))

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
                    val farFutureDate = nowDate.plus(10, DateTimeUnit.DAY)
                    every { property.get(target) } returns farFutureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.within(duration, now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be within $duration from now"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val nearFutureDate = nowDate.plus(5, DateTimeUnit.DAY)
                    every { property.get(target) } returns nearFutureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.within(duration, now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be within $duration from now"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, LocalDate>>()
                val target = mockk<Any>()
                every { property.name } returns "date"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    val farFutureDate = nowDate.plus(10, DateTimeUnit.DAY)
                    every { property.get(target) } returns farFutureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.within(
                            duration = duration,
                            now = now,
                            positiveMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }

                should("use custom negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom negative message"
                    val nearFutureDate = nowDate.plus(5, DateTimeUnit.DAY)
                    every { property.get(target) } returns nearFutureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.within(
                            duration = duration,
                            now = now,
                            negativeMessage = customMessage,
                        ),
                    )

                    errors.size shouldBe 1
                    errors[0].message shouldBe customMessage
                }
            }
        }
    })

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
import kotlinx.datetime.plus
import kotlin.reflect.KProperty1
import kotlin.time.Duration
import kotlin.time.Instant

class FutureTest :
    ShouldSpec({
        context("future validator") {
            val now = Instant.parse("2023-05-10T12:00:00Z")
            val nowDate = LocalDate.parse("2023-05-10")
            val nowDateTime = LocalDateTime.parse("2023-05-10T12:00:00")

            context("type compatibility") {
                should("support date types") {
                    val validator = mockk<PropertyValidator<*, *>>(relaxed = true)
                    val rule = validator.future()

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
                    validator.should.be(validator.future())

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

                        should("accept future date") {
                            val errors = mutableListOf<ValidationError>()
                            val futureDate = nowDate.plus(1, DateTimeUnit.DAY)
                            every { property.get(target) } returns futureDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject current date") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns nowDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a future date"
                        }

                        should("reject past date") {
                            val errors = mutableListOf<ValidationError>()
                            val pastDate = nowDate.plus(-1, DateTimeUnit.DAY)
                            every { property.get(target) } returns pastDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a future date"
                        }
                    }

                    context("LocalDateTime type") {
                        val property = mockk<KProperty1<Any, LocalDateTime>>()
                        val target = mockk<Any>()
                        every { property.name } returns "dateTime"

                        should("accept future datetime") {
                            val errors = mutableListOf<ValidationError>()
                            val futureDateTime =
                                LocalDateTime(
                                    nowDate.plus(1, DateTimeUnit.DAY),
                                    nowDateTime.time,
                                )
                            every { property.get(target) } returns futureDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject current datetime") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns nowDateTime

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a future date"
                        }
                    }

                    context("Instant type") {
                        val property = mockk<KProperty1<Any, Instant>>()
                        val target = mockk<Any>()
                        every { property.name } returns "instant"

                        should("accept future instant") {
                            val errors = mutableListOf<ValidationError>()
                            val futureInstant = now.plus(Duration.parse("1d"))
                            every { property.get(target) } returns futureInstant

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject current instant") {
                            val errors = mutableListOf<ValidationError>()
                            every { property.get(target) } returns now

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a future date"
                        }
                    }

                    context("with duration") {
                        val property = mockk<KProperty1<Any, LocalDate>>()
                        val target = mockk<Any>()
                        val duration = Duration.parse("30d")
                        every { property.name } returns "date"

                        should("accept date within duration") {
                            val errors = mutableListOf<ValidationError>()
                            val nearFutureDate = nowDate.plus(10, DateTimeUnit.DAY)
                            every { property.get(target) } returns nearFutureDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(duration, now = now))

                            errors shouldBe emptyList()
                        }

                        should("reject date beyond duration") {
                            val errors = mutableListOf<ValidationError>()
                            val distantFutureDate = nowDate.plus(40, DateTimeUnit.DAY)
                            every { property.get(target) } returns distantFutureDate

                            val validator = PropertyValidator(target, property, errors)
                            validator.should.be(validator.future(duration, now = now))

                            errors.size shouldBe 1
                            errors[0].message shouldBe "should be a future date of at most $duration"
                        }
                    }
                }

                context("notBe context") {
                    val property = mockk<KProperty1<Any, LocalDate>>()
                    val target = mockk<Any>()
                    every { property.name } returns "date"

                    should("reject future date") {
                        val errors = mutableListOf<ValidationError>()
                        val futureDate = nowDate.plus(1, DateTimeUnit.DAY)
                        every { property.get(target) } returns futureDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.future(now = now))

                        errors.size shouldBe 1
                        errors[0].message shouldBe "should not be a future date"
                    }

                    should("accept current or past date") {
                        val errors = mutableListOf<ValidationError>()
                        every { property.get(target) } returns nowDate

                        val validator = PropertyValidator(target, property, errors)
                        validator.should.notBe(validator.future(now = now))

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
                    val pastDate = nowDate.plus(-1, DateTimeUnit.DAY)
                    every { property.get(target) } returns pastDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.future(now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be a future date"
                }

                should("use default positive message with duration") {
                    val errors = mutableListOf<ValidationError>()
                    val duration = Duration.parse("30d")
                    val distantFutureDate = nowDate.plus(40, DateTimeUnit.DAY)
                    every { property.get(target) } returns distantFutureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(validator.future(duration, now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should be a future date of at most $duration"
                }

                should("use default negative message") {
                    val errors = mutableListOf<ValidationError>()
                    val futureDate = nowDate.plus(1, DateTimeUnit.DAY)
                    every { property.get(target) } returns futureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(validator.future(now = now))

                    errors.size shouldBe 1
                    errors[0].message shouldBe "should not be a future date"
                }
            }

            context("custom error messages") {
                val property = mockk<KProperty1<Any, LocalDate>>()
                val target = mockk<Any>()
                every { property.name } returns "date"

                should("use custom positive message") {
                    val errors = mutableListOf<ValidationError>()
                    val customMessage = "custom positive message"
                    val pastDate = nowDate.plus(-1, DateTimeUnit.DAY)
                    every { property.get(target) } returns pastDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.be(
                        validator.future(
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
                    val futureDate = nowDate.plus(1, DateTimeUnit.DAY)
                    every { property.get(target) } returns futureDate

                    val validator = PropertyValidator(target, property, errors)
                    validator.should.notBe(
                        validator.future(
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

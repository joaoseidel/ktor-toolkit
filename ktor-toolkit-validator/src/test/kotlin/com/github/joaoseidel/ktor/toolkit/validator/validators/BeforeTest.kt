package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.appliedTo
import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import com.github.joaoseidel.ktor.toolkit.validator.support.validatorFor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

class BeforeTest :
    ShouldSpec({
        val reference = LocalDate(2026, 6, 15)
        val utc = TimeZone.UTC

        context("on a LocalDate") {
            should("accept an earlier date") {
                messagesOf(LocalDate(2026, 6, 14)) { should be before(reference, utc) } shouldBe emptyList()
            }

            should("reject the same date, the comparison being strict") {
                messagesOf(reference) { should be before(reference, utc) } shouldBe listOf("should be before 2026-06-15")
            }

            should("reject a later date") {
                messagesOf(LocalDate(2026, 6, 16)) { should be before(reference, utc) }.size shouldBe 1
            }
        }

        context("on a LocalDateTime") {
            should("accept a moment on the previous day") {
                messagesOf(LocalDateTime(2026, 6, 14, 23, 0)) { should be before(reference, utc) } shouldBe emptyList()
            }

            should("reject a moment after the start of the reference day") {
                messagesOf(LocalDateTime(2026, 6, 15, 1, 0)) { should be before(reference, utc) }.size shouldBe 1
            }
        }

        context("on an Instant") {
            should("accept an earlier instant") {
                messagesOf(Instant.parse("2026-06-14T00:00:00Z")) { should be before(reference, utc) } shouldBe
                    emptyList()
            }

            should("reject a later one") {
                messagesOf(Instant.parse("2026-06-16T00:00:00Z")) { should be before(reference, utc) }.size shouldBe 1
            }
        }

        context("mixing the reference type") {
            should("compare against a LocalDateTime") {
                messagesOf(LocalDate(2026, 6, 14)) {
                    should be before(LocalDateTime(2026, 6, 15, 12, 0), utc)
                } shouldBe emptyList()
            }

            should("compare against an Instant") {
                messagesOf(LocalDate(2026, 6, 14)) {
                    should be before(Instant.parse("2026-06-15T12:00:00Z"), utc)
                } shouldBe emptyList()
            }
        }

        context("notBe before") {
            should("accept a later date") {
                messagesOf(LocalDate(2026, 6, 16)) { should notBe before(reference, utc) } shouldBe emptyList()
            }

            should("reject an earlier one") {
                messagesOf(LocalDate(2026, 6, 14)) { should notBe before(reference, utc) } shouldBe
                    listOf("should not be before 2026-06-15")
            }
        }

        should("reject a reference that is not a temporal value, as the rule is built") {
            val failure =
                shouldThrow<IllegalArgumentException> {
                    messagesOf(LocalDate(2026, 6, 14)) { should be before(1234L, utc) }
                }

            failure.message shouldContain "LocalDate, LocalDateTime or Instant"
        }

        context("without a zone") {
            // Each value sits days away from the reference, so no zone can move it across.
            should("resolve a date against the system zone") {
                messagesOf(LocalDate(2026, 6, 1)) { should be before(reference) } shouldBe emptyList()
            }

            should("resolve a date and time against the system zone") {
                messagesOf(LocalDateTime(2026, 6, 1, 12, 0)) { should be before(reference) } shouldBe emptyList()
            }

            should("resolve an instant against the system zone") {
                messagesOf(Instant.parse("2026-06-01T00:00:00Z")) { should be before(reference) } shouldBe emptyList()
            }
        }

        should("answer false for a value of a type it cannot compare against a point in time, rather than throw") {
            val rule = validatorFor<LocalDate?>(null).before(reference, utc)

            listOf(42, "2026-06-15", true, Unit).forEach { value ->
                withClue("$value") { rule appliedTo value shouldBe false }
            }
        }

        should("stay silent on an absent value") {
            messagesOf<LocalDate?>(null) { should be before(reference, utc) } shouldBe emptyList()
        }
    })

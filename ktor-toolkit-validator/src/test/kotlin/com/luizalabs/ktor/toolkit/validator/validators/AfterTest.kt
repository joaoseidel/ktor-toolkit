package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.support.messagesOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

class AfterTest :
    ShouldSpec({
        val reference = LocalDate(2026, 6, 15)
        val utc = TimeZone.UTC

        context("on a LocalDate") {
            should("accept a later date") {
                messagesOf(LocalDate(2026, 6, 16)) { should be after(reference, utc) } shouldBe emptyList()
            }

            should("reject the same date, the comparison being strict") {
                messagesOf(reference) { should be after(reference, utc) } shouldBe listOf("should be after 2026-06-15")
            }

            should("reject an earlier date") {
                messagesOf(LocalDate(2026, 6, 14)) { should be after(reference, utc) }.size shouldBe 1
            }
        }

        context("on a LocalDateTime") {
            should("accept a later moment on the reference day") {
                messagesOf(LocalDateTime(2026, 6, 15, 12, 0)) { should be after(reference, utc) } shouldBe emptyList()
            }

            should("reject the start of the reference day") {
                messagesOf(LocalDateTime(2026, 6, 15, 0, 0)) { should be after(reference, utc) }.size shouldBe 1
            }
        }

        context("on an Instant") {
            should("accept a later instant") {
                messagesOf(Instant.parse("2026-06-16T00:00:00Z")) { should be after(reference, utc) } shouldBe
                    emptyList()
            }

            should("reject an earlier one") {
                messagesOf(Instant.parse("2026-06-14T00:00:00Z")) { should be after(reference, utc) }.size shouldBe 1
            }
        }

        context("mixing the reference type") {
            should("compare against a LocalDateTime") {
                messagesOf(LocalDate(2026, 6, 16)) {
                    should be after(LocalDateTime(2026, 6, 15, 12, 0), utc)
                } shouldBe emptyList()
            }

            should("compare against an Instant") {
                messagesOf(LocalDate(2026, 6, 16)) {
                    should be after(Instant.parse("2026-06-15T12:00:00Z"), utc)
                } shouldBe emptyList()
            }
        }

        context("notBe after") {
            should("accept an earlier date") {
                messagesOf(LocalDate(2026, 6, 14)) { should notBe after(reference, utc) } shouldBe emptyList()
            }

            should("reject a later one") {
                messagesOf(LocalDate(2026, 6, 16)) { should notBe after(reference, utc) } shouldBe
                    listOf("should not be after 2026-06-15")
            }
        }

        should("reject a reference that is not a temporal value, as the rule is built") {
            val failure =
                shouldThrow<IllegalArgumentException> {
                    messagesOf(LocalDate(2026, 6, 16)) { should be after("2026-06-15", utc) }
                }

            failure.message shouldContain "LocalDate, LocalDateTime or Instant"
        }

        should("stay silent on an absent value") {
            messagesOf<LocalDate?>(null) { should be after(reference, utc) } shouldBe emptyList()
        }
    })

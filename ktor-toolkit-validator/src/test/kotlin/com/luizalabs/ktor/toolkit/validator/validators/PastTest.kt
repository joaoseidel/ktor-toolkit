package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class PastTest :
    ShouldSpec({
        // A fixed "now" keeps every expectation below deterministic.
        val now = Instant.parse("2026-06-15T12:00:00Z")
        val utc = TimeZone.UTC

        context("on a LocalDate") {
            should("accept yesterday") {
                messagesOf(LocalDate(2026, 6, 14)) { should be past(now = now, timeZone = utc) } shouldBe emptyList()
            }

            should("reject today, the comparison being at day granularity") {
                messagesOf(LocalDate(2026, 6, 15)) { should be past(now = now, timeZone = utc) } shouldBe
                    listOf("should be a past date")
            }

            should("reject tomorrow") {
                messagesOf(LocalDate(2026, 6, 16)) { should be past(now = now, timeZone = utc) }.size shouldBe 1
            }
        }

        context("on a LocalDateTime") {
            should("accept an earlier moment today") {
                messagesOf(LocalDateTime(2026, 6, 15, 11, 0)) {
                    should be past(now = now, timeZone = utc)
                } shouldBe emptyList()
            }

            should("reject a later one") {
                messagesOf(LocalDateTime(2026, 6, 15, 13, 0)) {
                    should be past(now = now, timeZone = utc)
                }.size shouldBe 1
            }
        }

        context("on an Instant") {
            should("accept an earlier instant") {
                messagesOf(Instant.parse("2026-06-15T11:59:59Z")) {
                    should be past(now = now, timeZone = utc)
                } shouldBe emptyList()
            }

            should("reject now itself") {
                messagesOf(now) { should be past(now = now, timeZone = utc) }.size shouldBe 1
            }
        }

        context("with a duration") {
            should("accept a date inside the window") {
                messagesOf(LocalDate(2026, 6, 12)) {
                    should be past(7.days, now, utc)
                } shouldBe emptyList()
            }

            should("reject one further back than it") {
                messagesOf(LocalDate(2026, 5, 1)) {
                    should be past(7.days, now, utc)
                } shouldBe listOf("should be a past date of at most 7d")
            }

            should("bound an Instant, too") {
                messagesOf(Instant.parse("2026-06-01T12:00:00Z")) {
                    should be past(7.days, now, utc)
                }.size shouldBe 1
            }
        }

        context("notBe past") {
            should("accept a future date") {
                messagesOf(LocalDate(2026, 6, 16)) { should notBe past(now = now, timeZone = utc) } shouldBe emptyList()
            }

            should("reject a past one") {
                messagesOf(LocalDate(2026, 6, 14)) { should notBe past(now = now, timeZone = utc) } shouldBe
                    listOf("should not be a past date")
            }
        }

        should("stay silent on an absent value") {
            messagesOf<LocalDate?>(null) { should be past(now = now, timeZone = utc) } shouldBe emptyList()
        }
    })

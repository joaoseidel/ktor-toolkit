package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.appliedTo
import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import com.github.joaoseidel.ktor.toolkit.validator.support.validatorFor
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class WithinTest :
    ShouldSpec(
        {
            // A fixed "now" keeps every expectation below deterministic.
            val now = Instant.parse("2026-06-15T12:00:00Z")
            val utc = TimeZone.UTC

            context("the window is symmetric") {
                should("accept a date behind now") {
                    messagesOf(LocalDate(2026, 6, 13)) { should be within(7.days, now, utc) } shouldBe emptyList()
                }

                should("accept a date ahead of it") {
                    messagesOf(LocalDate(2026, 6, 17)) { should be within(7.days, now, utc) } shouldBe emptyList()
                }

                should("reject a date beyond it in either direction") {
                    messagesOf(LocalDate(2026, 5, 1)) { should be within(7.days, now, utc) } shouldBe
                        listOf("should be within 7d from now")
                    messagesOf(LocalDate(2026, 8, 1)) { should be within(7.days, now, utc) }.size shouldBe 1
                }
            }

            context("on a LocalDateTime") {
                should("accept a moment inside the window") {
                    messagesOf(LocalDateTime(2026, 6, 15, 15, 0)) { should be within(6.hours, now, utc) } shouldBe
                        emptyList()
                }

                should("reject one outside it") {
                    messagesOf(LocalDateTime(2026, 6, 16, 0, 0)) { should be within(6.hours, now, utc) }.size shouldBe 1
                }
            }

            context("on an Instant") {
                should("accept an instant inside the window") {
                    messagesOf(Instant.parse("2026-06-15T10:00:00Z")) { should be within(6.hours, now, utc) } shouldBe
                        emptyList()
                }

                should("accept the boundary itself") {
                    messagesOf(Instant.parse("2026-06-15T06:00:00Z")) { should be within(6.hours, now, utc) } shouldBe
                        emptyList()
                }

                should("reject one just outside it") {
                    messagesOf(Instant.parse("2026-06-15T05:59:59Z")) {
                        should be within(6.hours, now, utc)
                    }.size shouldBe 1
                }
            }

            context("notBe within") {
                should("accept a date outside the window") {
                    messagesOf(LocalDate(2026, 1, 1)) { should notBe within(7.days, now, utc) } shouldBe emptyList()
                }

                should("reject one inside it") {
                    messagesOf(LocalDate(2026, 6, 15)) { should notBe within(7.days, now, utc) } shouldBe
                        listOf("should not be within 7d from now")
                }
            }

            context("without a reference point") {
                // Anchored on the system clock itself, so the window holds whenever the suite runs.
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                should("measure a date against the system clock and zone") {
                    messagesOf(today.date) { should be within(1.days) } shouldBe emptyList()
                }

                should("measure a date and time against the system clock and zone") {
                    messagesOf(today) { should be within(1.days) } shouldBe emptyList()
                }

                should("measure an instant against the system clock") {
                    messagesOf(Clock.System.now()) { should be within(1.days) } shouldBe emptyList()
                }
            }

            should("answer false for a value of a type it cannot measure against a point in time, rather than throw") {
                val rule = validatorFor<LocalDate?>(null).within(7.days, now, utc)

                listOf(42, "2026-06-15", true, Unit).forEach { value ->
                    withClue("$value") { rule appliedTo value shouldBe false }
                }
            }

            should("stay silent on an absent value") {
                messagesOf<LocalDate?>(null) { should be within(7.days, now, utc) } shouldBe emptyList()
            }
        },
    )

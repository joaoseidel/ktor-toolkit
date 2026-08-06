package com.github.joaoseidel.ktor.toolkit.validator

import com.github.joaoseidel.ktor.toolkit.validator.validators.after
import com.github.joaoseidel.ktor.toolkit.validator.validators.blank
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

private data class Event(
    val name: String,
    val startsAt: LocalDate,
    val endsAt: LocalDate,
)

class PropertyValidatorTest :
    ShouldSpec({
        val event = Event("Release", LocalDate(2026, 1, 10), LocalDate(2026, 1, 20))

        context("the value under validation") {
            should("be readable, alongside the object that owns it") {
                ValidationContext(event).property(Event::name) {
                    value shouldBe "Release"
                    target shouldBe event
                    path shouldBe "name"
                }
            }
        }

        context("target") {
            should("let a rule compare against a sibling property") {
                val errors =
                    ValidationContext(event)
                        .apply { property(Event::endsAt) { should be after(target.startsAt) } }
                        .errors

                errors shouldBe emptyList()
            }

            should("report a violation of that comparison") {
                val backwards = event.copy(endsAt = LocalDate(2026, 1, 1))
                val errors =
                    ValidationContext(backwards)
                        .apply { property(Event::endsAt) { should be after(target.startsAt) } }
                        .errors

                errors.map { it.propertyPath to it.message } shouldBe
                    listOf("endsAt" to "should be after 2026-01-10")
            }
        }

        context("errors") {
            should("expose what the enclosing context has collected so far") {
                ValidationContext(event.copy(name = "")).property(Event::name) {
                    errors shouldBe emptyList()
                    should notBe blank()
                    errors.map { it.message } shouldBe listOf("should not be blank")
                }
            }
        }
    })

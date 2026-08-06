package com.github.joaoseidel.ktor.toolkit.validator

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import com.github.joaoseidel.ktor.toolkit.validator.validators.blank
import com.github.joaoseidel.ktor.toolkit.validator.validators.email
import com.github.joaoseidel.ktor.toolkit.validator.validators.future
import com.github.joaoseidel.ktor.toolkit.validator.validators.inRange
import com.github.joaoseidel.ktor.toolkit.validator.validators.max
import com.github.joaoseidel.ktor.toolkit.validator.validators.min
import com.github.joaoseidel.ktor.toolkit.validator.validators.negative
import com.github.joaoseidel.ktor.toolkit.validator.validators.nil
import com.github.joaoseidel.ktor.toolkit.validator.validators.past
import com.github.joaoseidel.ktor.toolkit.validator.validators.pattern
import com.github.joaoseidel.ktor.toolkit.validator.validators.positive
import com.github.joaoseidel.ktor.toolkit.validator.validators.satisfying
import com.github.joaoseidel.ktor.toolkit.validator.validators.size
import com.github.joaoseidel.ktor.toolkit.validator.validators.uuid
import com.github.joaoseidel.ktor.toolkit.validator.validators.within
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.days

/**
 * The contract every rule shares: an absent optional field is nothing to say, and only `nil` has an
 * opinion about it. Each rule's own spec covers its logic; this one pins the shared behaviour down
 * in one place, so a new rule that gets it wrong fails here.
 */
class NullPropertyTest :
    ShouldSpec(
        {
            context("a rule with no opinion about absence") {
                should("stay silent when asserted with be") {
                    messagesOf<String?>(null) { should be blank() } shouldBe emptyList()
                    messagesOf<String?>(null) { should be email() } shouldBe emptyList()
                    messagesOf<String?>(null) { should be pattern(Regex("a+")) } shouldBe emptyList()
                    messagesOf<String?>(null) { should be uuid() } shouldBe emptyList()
                    messagesOf<String?>(null) { should be size(min = 1) } shouldBe emptyList()
                    messagesOf<Int?>(null) { should be min(1) } shouldBe emptyList()
                    messagesOf<Int?>(null) { should be max(1) } shouldBe emptyList()
                    messagesOf<Int?>(null) { should be inRange(1, 2) } shouldBe emptyList()
                    messagesOf<Int?>(null) { should be positive() } shouldBe emptyList()
                    messagesOf<Int?>(null) { should be negative() } shouldBe emptyList()
                    messagesOf<LocalDate?>(null) { should be past() } shouldBe emptyList()
                    messagesOf<LocalDate?>(null) { should be future() } shouldBe emptyList()
                    messagesOf<LocalDate?>(null) { should be within(1.days) } shouldBe emptyList()
                    messagesOf<Int?>(null) { should be satisfying("should be even") { it % 2 == 0 } } shouldBe emptyList()
                }

                should("stay silent when asserted with notBe, too") {
                    messagesOf<String?>(null) { should notBe blank() } shouldBe emptyList()
                    messagesOf<String?>(null) { should notBe email() } shouldBe emptyList()
                    messagesOf<Int?>(null) { should notBe positive() } shouldBe emptyList()
                    messagesOf<LocalDate?>(null) { should notBe past() } shouldBe emptyList()
                }

                should("still apply to a value that is present") {
                    messagesOf<String?>("") { should notBe blank() } shouldBe listOf("should not be blank")
                }
            }

            context("nil") {
                should("be the only rule that sees an absent value") {
                    messagesOf<String?>(null) { should be nil() } shouldBe emptyList()
                    messagesOf<String?>(null) { should notBe nil() } shouldBe listOf("should not be null")
                }

                should("pair with another rule to require presence and shape") {
                    messagesOf<String?>("nope") {
                        should notBe nil()
                        should be email()
                    } shouldBe listOf("should be a valid email address")
                }
            }

            context("a composed rule") {
                should("inherit the opinion of whichever operand has one") {
                    messagesOf<String?>(null) { should be (blank() and email()) } shouldBe emptyList()
                    messagesOf<String?>(null) { should be (nil() and blank()) } shouldBe emptyList()
                    messagesOf<String?>(null) { should notBe (nil() or blank()) } shouldBe
                        listOf("should not be null or should not be blank")
                }
            }
        },
    )

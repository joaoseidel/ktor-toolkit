package com.github.joaoseidel.ktor.toolkit.validator.validators

import com.github.joaoseidel.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class PatternTest :
    ShouldSpec({
        val slug = Regex("[a-z0-9-]+")

        context("be pattern") {
            should("accept a matching string") {
                messagesOf("kotlin-in-action") { should be pattern(slug) } shouldBe emptyList()
            }

            should("reject a non-matching string") {
                messagesOf("Kotlin In Action") { should be pattern(slug) } shouldBe
                    listOf("should match pattern \"[a-z0-9-]+\"")
            }

            should("require the whole string to match, not just a part of it") {
                messagesOf("kotlin!") { should be pattern(slug) }.size shouldBe 1
            }
        }

        context("notBe pattern") {
            should("accept a non-matching string") {
                messagesOf("Kotlin") { should notBe pattern(slug) } shouldBe emptyList()
            }

            should("reject a matching string") {
                messagesOf("kotlin") { should notBe pattern(slug) } shouldBe
                    listOf("should not match pattern \"[a-z0-9-]+\"")
            }
        }

        should("stay silent on an absent value") {
            messagesOf<String?>(null) { should be pattern(slug) } shouldBe emptyList()
        }
    })

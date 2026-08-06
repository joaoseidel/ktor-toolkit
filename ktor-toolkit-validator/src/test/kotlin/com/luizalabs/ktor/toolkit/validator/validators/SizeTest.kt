package com.luizalabs.ktor.toolkit.validator.validators

import com.luizalabs.ktor.toolkit.validator.support.messagesOf
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class SizeTest :
    ShouldSpec({
        context("on a string") {
            should("accept a length within the bounds, which are inclusive") {
                messagesOf("abc") { should be size(min = 3, max = 5) } shouldBe emptyList()
                messagesOf("abcde") { should be size(min = 3, max = 5) } shouldBe emptyList()
            }

            should("reject a length outside them") {
                messagesOf("ab") { should be size(min = 3, max = 5) } shouldBe
                    listOf("size should be between 3 and 5")
                messagesOf("abcdef") { should be size(min = 3, max = 5) }.size shouldBe 1
            }

            should("default to an unbounded range") {
                messagesOf("") { should be size() } shouldBe emptyList()
            }
        }

        context("on a collection") {
            should("count its elements") {
                messagesOf(listOf(1, 2)) { should be size(min = 1, max = 3) } shouldBe emptyList()
                messagesOf(emptyList<Int>()) { should be size(min = 1) } shouldBe
                    listOf("size should be between 1 and ${Int.MAX_VALUE}")
            }

            should("work through a set, too") {
                messagesOf(setOf("a", "b")) { should be size(max = 1) }.size shouldBe 1
            }
        }

        context("on a map") {
            should("count its entries") {
                messagesOf(mapOf("a" to 1)) { should be size(min = 1, max = 1) } shouldBe emptyList()
                messagesOf(mapOf("a" to 1, "b" to 2)) { should be size(max = 1) }.size shouldBe 1
            }
        }

        context("on an array") {
            should("count its elements") {
                messagesOf(arrayOf("a", "b")) { should be size(min = 2, max = 2) } shouldBe emptyList()
                messagesOf(arrayOf("a")) { should be size(min = 2) }.size shouldBe 1
            }
        }

        context("notBe size") {
            should("reject a length inside the range") {
                messagesOf("abc") { should notBe size(min = 3, max = 5) } shouldBe
                    listOf("size should not be between 3 and 5")
            }

            should("accept a length outside it") {
                messagesOf("ab") { should notBe size(min = 3, max = 5) } shouldBe emptyList()
            }
        }

        should("stay silent on an absent value") {
            messagesOf<String?>(null) { should be size(min = 1) } shouldBe emptyList()
        }
    })

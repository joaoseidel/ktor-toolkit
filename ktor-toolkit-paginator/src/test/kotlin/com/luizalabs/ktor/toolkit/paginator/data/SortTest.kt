package com.luizalabs.ktor.toolkit.paginator.data

import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.DESC
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class SortTest :
    ShouldSpec({
        context("Sort.fromString") {
            should("read an ascending token") {
                Sort.fromString("createdAt") shouldBe Sort("createdAt", ASC)
            }

            should("read a descending token") {
                Sort.fromString("-createdAt") shouldBe Sort("createdAt", DESC)
            }

            should("strip only the leading marker") {
                Sort.fromString("-created-at") shouldBe Sort("created-at", DESC)
            }

            should("treat an internal hyphen as part of the property name") {
                Sort.fromString("created-at") shouldBe Sort("created-at", ASC)
            }
        }

        context("Sort.Direction.fromString") {
            should("map the leading marker to a direction") {
                listOf("name" to ASC, "-name" to DESC, "" to ASC).forEach { (token, expected) ->
                    withClue("token \"$token\"") {
                        Sort.Direction.fromString(token) shouldBe expected
                    }
                }
            }
        }
    })

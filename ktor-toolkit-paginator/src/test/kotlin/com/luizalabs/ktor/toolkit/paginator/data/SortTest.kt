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

        context("a property reference") {
            should("take the property's name as the sort key") {
                Sort(Book::title) shouldBe Sort("title", ASC)
            }

            should("carry the direction it was given") {
                Sort(Book::publishedAt, DESC) shouldBe Sort("publishedAt", DESC)
            }
        }

        context("sortBy") {
            should("keep the criteria in the order they were declared") {
                val ordering =
                    sortBy {
                        desc(Book::publishedAt)
                        asc(Book::title)
                    }

                ordering shouldBe listOf(Sort("publishedAt", DESC), Sort("title", ASC))
            }

            should("accept a name for a property that has no reference to hand") {
                sortBy {
                    asc("relevance")
                    desc("score")
                } shouldBe listOf(Sort("relevance", ASC), Sort("score", DESC))
            }

            should("produce nothing when it declares nothing") {
                sortBy { } shouldBe emptyList()
            }
        }
    })

private data class Book(
    val title: String,
    val publishedAt: String,
)

package com.github.joaoseidel.ktor.toolkit.paginator.data

import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.DESC
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class SortTest :
    ShouldSpec(
        {
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

            context("comparing two criteria") {
                val criterion = Sort("title", ASC)

                should("consider a criterion equal to itself") {
                    criterion.equals(criterion) shouldBe true
                }

                should("consider a criterion equal to a structurally identical one") {
                    criterion shouldBe Sort("title", ASC)
                    criterion.hashCode() shouldBe Sort("title", ASC).hashCode()
                }

                should("consider a criterion different from anything that is not one") {
                    criterion.equals("title asc") shouldBe false
                }

                should("consider criteria different when any single detail differs") {
                    val variants =
                        mapOf(
                            "the property" to criterion.copy(property = "createdAt"),
                            "the direction" to criterion.copy(direction = DESC),
                        )

                    variants.forEach { (detail, variant) ->
                        withClue("differing in $detail") { criterion shouldNotBe variant }
                    }
                }

                should("hand its parts back in declaration order") {
                    val (property, direction) = criterion

                    property shouldBe "title"
                    direction shouldBe ASC
                }

                should("name every part in its string form") {
                    criterion.toString() shouldContain "property=title"
                    criterion.toString() shouldContain "direction=ASC"
                }
            }

            context("serializing a criterion") {
                should("survive a round trip") {
                    val criterion = Sort("createdAt", DESC)

                    Json.decodeFromString<Sort>(Json.encodeToString(criterion)) shouldBe criterion
                }

                should("spell the direction out") {
                    Json.encodeToString(Sort("createdAt", DESC)) shouldBe """{"property":"createdAt","direction":"DESC"}"""
                }

                should("refuse a payload that leaves the property or the direction out") {
                    listOf("""{"property":"createdAt"}""", """{"direction":"DESC"}""").forEach { payload ->
                        withClue(payload) {
                            shouldThrow<SerializationException> { Json.decodeFromString<Sort>(payload) }
                        }
                    }
                }
            }
        },
    )

private data class Book(
    val title: String,
    val publishedAt: String,
)

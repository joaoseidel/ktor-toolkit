package com.github.joaoseidel.ktor.toolkit.paginator.data

import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.ASC
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val page = Paged(Page(1, 10), listOf(Sort("title", ASC)), listOf("a", "b"), totalElements = 12)

class PagedTest :
    ShouldSpec(
        {
            context("comparing two pages") {
                should("consider a page equal to itself") {
                    page.equals(page) shouldBe true
                }

                should("consider a page equal to a structurally identical one") {
                    page shouldBe page.copy()
                    page.hashCode() shouldBe page.copy().hashCode()
                }

                should("consider a page different from anything that is not a page") {
                    page.equals("a page, honest") shouldBe false
                }

                should("consider pages different when any single detail differs") {
                    val variants =
                        mapOf(
                            "the page being asked for" to page.copy(page = Page(2, 10)),
                            "the sort criteria" to page.copy(sortBy = emptyList()),
                            "the content" to page.copy(content = listOf("a")),
                            "the total element count" to page.copy(totalElements = 13),
                        )

                    variants.forEach { (detail, variant) ->
                        withClue("differing in $detail") { page shouldNotBe variant }
                    }
                }
            }

            context("reading a page apart") {
                should("hand back its parts in declaration order") {
                    val (pageSpec, sortBy, content, totalElements) = page

                    pageSpec shouldBe Page(1, 10)
                    sortBy shouldBe listOf(Sort("title", ASC))
                    content shouldBe listOf("a", "b")
                    totalElements shouldBe 12L
                }

                should("name every part in its string form") {
                    val described = page.toString()

                    listOf("page=", "sortBy=", "content=", "totalElements=12").forEach {
                        withClue(it) { described shouldContain it }
                    }
                }
            }

            context("serializing a page") {
                should("survive a round trip with every field populated") {
                    val json = Json.encodeToString(page)

                    Json.decodeFromString<Paged<String>>(json) shouldBe page
                }

                should("survive a round trip when only the total element count is populated") {
                    val empty = Paged<String>(totalElements = 0)

                    Json.decodeFromString<Paged<String>>(Json.encodeToString(empty)) shouldBe empty
                }

                should("fall back to the defaults for fields the payload leaves out") {
                    Json.decodeFromString<Paged<String>>("""{"totalElements":7}""") shouldBe
                        Paged(Page(0, 10), emptyList(), emptyList(), totalElements = 7)
                }

                should("omit the fields that still hold their default") {
                    Json.encodeToString(Paged<String>(totalElements = 0)) shouldBe """{"totalElements":0}"""
                }

                should("spell the defaults out when the format asks it to") {
                    val verbose = Json { encodeDefaults = true }

                    verbose.encodeToString(Paged<String>(totalElements = 0)) shouldBe
                        """{"page":{"page":0,"pageSize":10},"sortBy":[],"content":[],"totalElements":0}"""
                }

                should("refuse a payload that leaves the total element count out") {
                    shouldThrow<SerializationException> {
                        Json.decodeFromString<Paged<String>>("""{"content":["a"]}""")
                    }
                }
            }
        },
    )

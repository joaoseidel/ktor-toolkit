package com.github.joaoseidel.ktor.toolkit.paginator.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class PageTest :
    ShouldSpec(
        {
            val page = Page(page = 2, pageSize = 25)

            context("comparing two page specifications") {
                should("consider a specification equal to itself") {
                    page.equals(page) shouldBe true
                }

                should("consider a specification equal to a structurally identical one") {
                    page shouldBe Page(2, 25)
                    page.hashCode() shouldBe Page(2, 25).hashCode()
                }

                should("consider a specification different from anything that is not one") {
                    page.equals("page 2") shouldBe false
                }

                should("consider specifications different when any single detail differs") {
                    val variants =
                        mapOf(
                            "the page index" to page.copy(page = 3),
                            "the page size" to page.copy(pageSize = 26),
                        )

                    variants.forEach { (detail, variant) ->
                        withClue("differing in $detail") { page shouldNotBe variant }
                    }
                }
            }

            context("reading a page specification apart") {
                should("hand back its parts in declaration order") {
                    val (index, size) = page

                    index shouldBe 2
                    size shouldBe 25
                }

                should("name every part in its string form") {
                    page.toString() shouldContain "page=2"
                    page.toString() shouldContain "pageSize=25"
                }
            }

            context("serializing a page specification") {
                should("survive a round trip") {
                    Json.decodeFromString<Page>(Json.encodeToString(page)) shouldBe page
                }

                should("write out both the index and the size") {
                    Json.encodeToString(page) shouldBe """{"page":2,"pageSize":25}"""
                }

                should("refuse a payload that leaves either of them out") {
                    listOf("""{"page":2}""", """{"pageSize":25}""", "{}").forEach { payload ->
                        withClue(payload) {
                            shouldThrow<SerializationException> { Json.decodeFromString<Page>(payload) }
                        }
                    }
                }
            }
        },
    )

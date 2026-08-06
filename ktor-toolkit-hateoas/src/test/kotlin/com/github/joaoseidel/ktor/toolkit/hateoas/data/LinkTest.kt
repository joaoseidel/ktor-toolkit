package com.github.joaoseidel.ktor.toolkit.hateoas.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class LinkTest :
    ShouldSpec({
        context("building a link") {
            should("default to GET when no method is named") {
                Link("self", "/books/1").method shouldBe "GET"
            }

            should("take the verb of the method it was given") {
                Link("delete", "/books/1", HttpMethod.Delete).method shouldBe "DELETE"
            }

            should("refuse a blank rel, which no client could dispatch on") {
                listOf("", "   ").forEach { rel ->
                    withClue("rel \"$rel\"") {
                        shouldThrow<IllegalArgumentException> {
                            Link(rel, "/books/1")
                        }.message shouldContain "rel must not be blank"
                    }
                }
            }

            should("refuse a blank href, which no client could follow") {
                listOf("", "   ").forEach { href ->
                    withClue("href \"$href\"") {
                        shouldThrow<IllegalArgumentException> {
                            Link("self", href)
                        }.message shouldContain "href must not be blank"
                    }
                }
            }
        }

        context("comparing two links") {
            val link = Link("self", "/books/1", HttpMethod.Get)

            should("consider a link equal to itself") {
                link.equals(link) shouldBe true
            }

            should("consider a link equal to a structurally identical one") {
                link shouldBe Link("self", "/books/1")
                link.hashCode() shouldBe Link("self", "/books/1").hashCode()
            }

            should("consider a link different from anything that is not one") {
                link.equals("self -> /books/1") shouldBe false
            }

            should("consider links different when any single part differs") {
                val variants =
                    mapOf(
                        "the rel" to Link("next", "/books/1"),
                        "the href" to Link("self", "/books/2"),
                        "the method" to Link("self", "/books/1", HttpMethod.Delete),
                    )

                variants.forEach { (part, variant) ->
                    withClue("differing in $part") { link shouldNotBe variant }
                }
            }

            should("hand its parts back in declaration order") {
                val (rel, href, method) = link

                rel shouldBe "self"
                href shouldBe "/books/1"
                method shouldBe "GET"
            }

            should("name every part in its string form") {
                val described = link.toString()

                listOf("rel=self", "href=/books/1", "method=GET").forEach {
                    withClue(it) { described shouldContain it }
                }
            }
        }

        context("serializing a link") {
            should("survive a round trip") {
                val link = Link("delete", "/books/1", HttpMethod.Delete)

                Json.decodeFromString<Link>(Json.encodeToString(link)) shouldBe link
            }

            should("write out all three parts") {
                Json.encodeToString(Link("self", "/books/1")) shouldBe
                    """{"rel":"self","href":"/books/1","method":"GET"}"""
            }

            should("round-trip a verb it does not know, rather than normalise it away") {
                val json = """{"rel":"purge","href":"/books/1","method":"PURGE"}"""

                Json.decodeFromString<Link>(json).method shouldBe "PURGE"
            }

            should("refuse a payload that leaves a part out") {
                listOf(
                    """{"href":"/books/1","method":"GET"}""",
                    """{"rel":"self","method":"GET"}""",
                    """{"rel":"self","href":"/books/1"}""",
                ).forEach { payload ->
                    withClue(payload) {
                        shouldThrow<SerializationException> { Json.decodeFromString<Link>(payload) }
                    }
                }
            }

            should("apply the blank guards to a payload, too") {
                shouldThrow<IllegalArgumentException> {
                    Json.decodeFromString<Link>("""{"rel":"","href":"/books/1","method":"GET"}""")
                }
            }
        }
    })

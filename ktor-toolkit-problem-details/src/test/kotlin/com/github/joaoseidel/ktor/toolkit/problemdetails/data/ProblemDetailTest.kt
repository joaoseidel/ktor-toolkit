package com.github.joaoseidel.ktor.toolkit.problemdetails.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private fun problem(
    status: HttpStatusCode = HttpStatusCode.NotFound,
    detail: String? = "Book not found",
    properties: Map<String, String>? = mapOf("id" to "42"),
    type: String = "https://example.com/probs/no-such-book",
    instance: String? = "/books/42",
) = ProblemDetail.fromStatus(status, detail, properties, type, instance)

class ProblemDetailTest :
    ShouldSpec({
        context("building a problem from a status") {
            should("take the status description as its title") {
                val detail = ProblemDetail.fromStatus(HttpStatusCode.NotFound)

                detail.title shouldBe "Not Found"
                detail.status shouldBe 404
            }

            should("default to the RFC's blank type when the caller names none") {
                ProblemDetail.fromStatus(HttpStatusCode.Gone).type shouldBe ProblemDetail.BLANK_TYPE
            }

            should("leave the optional members absent when the caller supplies none") {
                val detail = ProblemDetail.fromStatus(HttpStatusCode.Gone)

                detail.detail shouldBe null
                detail.instance shouldBe null
                detail.properties shouldBe null
            }
        }

        context("filling in the instance") {
            should("adopt the request path when the problem names no instance") {
                ProblemDetail.fromStatus(HttpStatusCode.NotFound).orInstance("/books/42").instance shouldBe "/books/42"
            }

            should("keep an instance the problem named itself") {
                ProblemDetail
                    .fromStatus(HttpStatusCode.NotFound, instance = "urn:book:42")
                    .orInstance("/books/42")
                    .instance shouldBe "urn:book:42"
            }
        }

        context("comparing two problems") {
            val detail = problem()

            should("consider a problem equal to itself") {
                detail.equals(detail) shouldBe true
            }

            should("consider a problem equal to a structurally identical one") {
                detail shouldBe problem()
                detail.hashCode() shouldBe problem().hashCode()
            }

            should("consider a problem different from anything that is not one") {
                detail.equals("404 Not Found") shouldBe false
            }

            should("consider problems different when any single member differs") {
                val variants =
                    mapOf(
                        "the type" to problem(type = "about:blank"),
                        "the title and status" to problem(status = HttpStatusCode.Gone),
                        "the detail" to problem(detail = "No such book"),
                        "the instance" to problem(instance = "/books/43"),
                        "the properties" to problem(properties = null),
                    )

                variants.forEach { (member, variant) ->
                    withClue("differing in $member") { detail shouldNotBe variant }
                }
            }

            should("hand its members back in declaration order") {
                val (type, title, status, explanation, instance, properties) = detail

                type shouldBe "https://example.com/probs/no-such-book"
                title shouldBe "Not Found"
                status shouldBe 404
                explanation shouldBe "Book not found"
                instance shouldBe "/books/42"
                properties shouldBe mapOf("id" to "42")
            }

            should("name every member in its string form") {
                val described = detail.toString()

                listOf("type=", "title=Not Found", "status=404", "detail=", "instance=", "properties=")
                    .forEach { withClue(it) { described shouldContain it } }
            }
        }

        context("serializing a problem") {
            should("survive a round trip with every member populated") {
                val detail = problem()

                Json.decodeFromString<ProblemDetail>(Json.encodeToString(detail)) shouldBe detail
            }

            should("survive a round trip with only the required members populated") {
                val detail = ProblemDetail.fromStatus(HttpStatusCode.Gone)

                Json.decodeFromString<ProblemDetail>(Json.encodeToString(detail)) shouldBe detail
            }

            should("fall back to the blank type for a payload that names none") {
                Json.decodeFromString<ProblemDetail>("""{"title":"Gone","status":410}""") shouldBe
                    ProblemDetail.fromStatus(HttpStatusCode.Gone)
            }

            should("omit the members that still hold their default") {
                Json.encodeToString(ProblemDetail.fromStatus(HttpStatusCode.Gone)) shouldBe
                    """{"title":"Gone","status":410}"""
            }

            should("spell the defaults out when the format asks it to") {
                val verbose = Json { encodeDefaults = true }

                verbose.encodeToString(ProblemDetail.fromStatus(HttpStatusCode.Gone)) shouldBe
                    """{"type":"about:blank","title":"Gone","status":410,"detail":null,"instance":null,"properties":null}"""
            }

            should("refuse a payload that leaves the title or the status out") {
                listOf("""{"status":410}""", """{"title":"Gone"}""").forEach { payload ->
                    withClue(payload) {
                        shouldThrow<SerializationException> { Json.decodeFromString<ProblemDetail>(payload) }
                    }
                }
            }
        }
    })

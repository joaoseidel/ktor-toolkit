package com.github.joaoseidel.ktor.toolkit.hateoas.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class Book(
    val id: String,
    val title: String,
)

class ResourceSerializerTest :
    ShouldSpec(
        {
            val json = Json

            context("serialization") {
                should("inline the content's fields alongside _links") {
                    val resource = Resource(Book("1", "Dune")).withLink(Link("self", "/books/1"))

                    val encoded = json.encodeToString(resource).let(json::parseToJsonElement).jsonObject

                    encoded["id"]?.jsonPrimitive?.content shouldBe "1"
                    encoded["title"]?.jsonPrimitive?.content shouldBe "Dune"
                    encoded["_links"]?.jsonArray?.size shouldBe 1
                }

                should("emit an empty _links array when the resource has none") {
                    val encoded =
                        json
                            .encodeToString(Resource(Book("1", "Dune")))
                            .let(json::parseToJsonElement)
                            .jsonObject

                    encoded["_links"]?.jsonArray?.size shouldBe 0
                }
            }

            context("deserialization") {
                should("round-trip a resource with links") {
                    val resource =
                        Resource(Book("1", "Dune"))
                            .withLinks(listOf(Link("self", "/books/1"), Link("next", "/books/2")))

                    json.decodeFromString<Resource<Book>>(json.encodeToString(resource)) shouldBe resource
                }

                should("read a payload that carries no _links") {
                    val decoded = json.decodeFromString<Resource<Book>>("""{"id":"1","title":"Dune"}""")

                    decoded.content shouldBe Book("1", "Dune")
                    decoded.links shouldBe emptyList()
                }

                should("reject a link whose rel or href is blank") {
                    shouldThrow<IllegalArgumentException> {
                        json.decodeFromString<Link>("""{"rel":"","href":"/books","method":"GET"}""")
                    }
                    shouldThrow<IllegalArgumentException> {
                        json.decodeFromString<Link>("""{"rel":"self","href":"","method":"GET"}""")
                    }
                }
            }

            context("withLink / withLinks") {
                should("append without mutating the original") {
                    val original = Resource(Book("1", "Dune"))

                    val withOne = original.withLink(Link("self", "/books/1"))
                    val withMore = withOne.withLinks(listOf(Link("next", "/books/2")))

                    original.links shouldBe emptyList()
                    withOne.links.map { it.rel } shouldContainExactly listOf("self")
                    withMore.links.map { it.rel } shouldContainExactly listOf("self", "next")
                }
            }
        },
    )

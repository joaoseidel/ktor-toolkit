package com.luizalabs.ktor.toolkit.expander.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class SerializedAuthor(
    val id: String,
    val name: String,
)

@Serializable
private data class SerializedReview(
    val id: String,
    val author: Expandable<SerializedAuthor>,
)

class ExpandableSerializerTest :
    ShouldSpec({
        val json = Json

        context("serialization") {
            should("write an unresolved ref as a bare string") {
                val encoded = json.encodeToString(SerializedReview("r1", Expandable.Ref("a1")))

                json
                    .parseToJsonElement(encoded)
                    .jsonObject["author"]
                    ?.jsonPrimitive
                    ?.content shouldBe "a1"
            }

            should("write a resolved value as an object") {
                val encoded = json.encodeToString(SerializedReview("r1", Expandable.Resolved(SerializedAuthor("a1", "Herbert"))))

                val author = json.parseToJsonElement(encoded).jsonObject["author"]?.jsonObject
                author?.get("id")?.jsonPrimitive?.content shouldBe "a1"
                author?.get("name")?.jsonPrimitive?.content shouldBe "Herbert"
            }

            should("write a partial value with only the requested fields") {
                val partial = Expandable.Partial(SerializedAuthor("a1", "Herbert"), setOf("name"))

                val encoded = json.encodeToString(SerializedReview("r1", partial))

                val author = json.parseToJsonElement(encoded).jsonObject["author"]?.jsonObject
                author?.keys shouldBe setOf("name")
            }

            should("write an empty object when the projection matches nothing") {
                val partial = Expandable.Partial(SerializedAuthor("a1", "Herbert"), setOf("nope"))

                val encoded = json.encodeToString(SerializedReview("r1", partial))

                json
                    .parseToJsonElement(encoded)
                    .jsonObject["author"]
                    ?.jsonObject
                    ?.keys shouldBe emptySet()
            }
        }

        context("deserialization") {
            should("read a bare string back as a ref") {
                val decoded = json.decodeFromString<SerializedReview>("""{"id":"r1","author":"a1"}""")

                decoded.author.shouldBeInstanceOf<Expandable.Ref>().id shouldBe "a1"
            }

            should("read an object back as a resolved value") {
                val decoded =
                    json.decodeFromString<SerializedReview>("""{"id":"r1","author":{"id":"a1","name":"Herbert"}}""")

                decoded.author.shouldBeInstanceOf<Expandable.Resolved<SerializedAuthor>>().value shouldBe SerializedAuthor("a1", "Herbert")
            }

            should("round-trip a ref") {
                val review = SerializedReview("r1", Expandable.Ref("a1"))

                json.decodeFromString<SerializedReview>(json.encodeToString(review)) shouldBe review
            }

            should("round-trip a resolved value") {
                val review = SerializedReview("r1", Expandable.Resolved(SerializedAuthor("a1", "Herbert")))

                json.decodeFromString<SerializedReview>(json.encodeToString(review)) shouldBe review
            }

            should("reject a shape that is neither a string nor an object") {
                shouldThrow<IllegalStateException> {
                    json.decodeFromString<SerializedReview>("""{"id":"r1","author":[1,2]}""")
                }
            }
        }

        context("resolve") {
            should("resolve a ref through the block") {
                val resolved = Expandable.Ref("a1").resolve { SerializedAuthor(it, "Herbert") }

                resolved.shouldBeInstanceOf<Expandable.Resolved<SerializedAuthor>>().value shouldBe SerializedAuthor("a1", "Herbert")
            }

            should("keep the ref when the block finds nothing") {
                val resolved = Expandable.Ref("a1").resolve<SerializedAuthor> { null }

                resolved.shouldBeInstanceOf<Expandable.Ref>().id shouldBe "a1"
            }

            should("skip the block for an already-resolved value") {
                var called = false
                val original = Expandable.Resolved(SerializedAuthor("a1", "Herbert"))

                val resolved =
                    original.resolve {
                        called = true
                        SerializedAuthor("x", "y")
                    }

                called shouldBe false
                resolved shouldBe original
            }

            should("skip the block for a value already narrowed to a projection") {
                var called = false
                val original = Expandable.Partial(SerializedAuthor("a1", "Herbert"), setOf("name"))

                val resolved =
                    original.resolve {
                        called = true
                        SerializedAuthor("x", "y")
                    }

                called shouldBe false
                resolved shouldBe original
            }
        }

        context("resolveAll") {
            should("ask for every ref id in one call") {
                var requested: Set<String> = emptySet()
                val expandables =
                    listOf(
                        Expandable.Ref("a1"),
                        Expandable.Resolved(SerializedAuthor("a2", "Austen")),
                        Expandable.Ref("a3"),
                    )

                expandables.resolveAll { ids ->
                    requested = ids
                    ids.associateWith { SerializedAuthor(it, "name") }
                }

                requested shouldBe setOf("a1", "a3")
            }

            should("skip the block entirely when nothing is unresolved") {
                var called = false
                val expandables = listOf(Expandable.Resolved(SerializedAuthor("a1", "Herbert")))

                expandables.resolveAll {
                    called = true
                    emptyMap()
                }

                called shouldBe false
            }

            should("pass an already-resolved or already-projected entry through untouched") {
                val resolved = Expandable.Resolved(SerializedAuthor("a2", "Austen"))
                val partial = Expandable.Partial(SerializedAuthor("a3", "Eliot"), setOf("name"))

                val out =
                    listOf(Expandable.Ref("a1"), resolved, partial).resolveAll { ids ->
                        ids.associateWith { SerializedAuthor(it, "Herbert") }
                    }

                out[1] shouldBe resolved
                out[2] shouldBe partial
            }

            should("keep a ref the block returned nothing for") {
                val out = listOf(Expandable.Ref("a1")).resolveAll<SerializedAuthor> { emptyMap() }

                out.single() shouldBe Expandable.Ref("a1")
            }
        }

        context("the serializer itself") {
            should("describe the field it stands in for") {
                ExpandableSerializer(SerializedAuthor.serializer()).descriptor.serialName shouldBe "Expandable"
            }

            should("be reachable through the generated companion") {
                val serializer = Expandable.serializer(SerializedAuthor.serializer())

                json.encodeToString(serializer, Expandable.Ref("a1")) shouldBe "\"a1\""
            }
        }
    })

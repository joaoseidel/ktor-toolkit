package com.luizalabs.ktor.toolkit.expander.data

import com.luizalabs.ktor.toolkit.expander.web.ExpandRequest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.http.parametersOf
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger

@Serializable
private data class Author(
    val id: String,
    val name: String,
    val books: List<Expandable<Book>>? = null,
)

@Serializable
private data class Book(
    val id: String,
    val title: String,
)

@Serializable
private data class Review(
    val id: String,
    val author: Expandable<Author>,
    val editor: Expandable<Author>? = null,
    val mentions: List<Expandable<Book>>? = null,
)

private fun expand(value: String) = ExpandRequest.from(parametersOf("expand", value))

private val authors =
    mapOf(
        "a1" to Author("a1", "Herbert"),
        "a2" to Author("a2", "Austen"),
    )

private val books =
    mapOf(
        "b1" to Book("b1", "Dune"),
        "b2" to Book("b2", "Emma"),
    )

class ExpandSpecTest :
    ShouldSpec({
        context("single field") {
            should("leave the ref alone when expansion was not requested") {
                val calls = AtomicInteger()
                val spec = reviewSpec(calls)

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), ExpandRequest.NONE)

                result.author.shouldBeInstanceOf<Expandable.Ref>()
                calls.get() shouldBe 0
            }

            should("resolve the ref when expansion was requested") {
                val spec = reviewSpec()

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), expand("author"))

                result.author.shouldBeInstanceOf<Expandable.Resolved<Author>>().value shouldBe authors["a1"]
            }

            should("keep an unknown ref unresolved instead of dropping it") {
                val spec = reviewSpec()

                val result = spec.apply(Review("r1", Expandable.Ref("nope")), expand("author"))

                result.author.shouldBeInstanceOf<Expandable.Ref>().id shouldBe "nope"
            }

            should("batch a whole page into a single call") {
                val calls = AtomicInteger()
                val spec = reviewSpec(calls)
                val reviews =
                    listOf(
                        Review("r1", Expandable.Ref("a1")),
                        Review("r2", Expandable.Ref("a2")),
                        Review("r3", Expandable.Ref("a1")),
                    )

                val result = spec.apply(reviews, expand("author"))

                calls.get() shouldBe 1
                result.map { it.author }.forEach { it.shouldBeInstanceOf<Expandable.Resolved<Author>>() }
            }

            should("not call the batcher when there is nothing to resolve") {
                val calls = AtomicInteger()
                val spec = reviewSpec(calls)

                spec.apply(Review("r1", Expandable.Resolved(authors.getValue("a1"))), expand("author"))

                calls.get() shouldBe 0
            }

            should("treat a single item exactly like a one-element list") {
                val spec = reviewSpec()
                val review = Review("r1", Expandable.Ref("a1"))

                spec.apply(review, expand("author")) shouldBe spec.apply(listOf(review), expand("author")).single()
            }
        }

        context("optional field") {
            should("skip an item whose field is absent") {
                val spec = reviewSpec()

                val result = spec.apply(Review("r1", Expandable.Ref("a1"), editor = null), expand("editor"))

                result.editor shouldBe null
            }

            should("resolve the field when it is present") {
                val spec = reviewSpec()

                val result =
                    spec.apply(
                        Review("r1", Expandable.Ref("a1"), editor = Expandable.Ref("a2")),
                        expand("editor"),
                    )

                result.editor.shouldBeInstanceOf<Expandable.Resolved<Author>>().value shouldBe authors["a2"]
            }

            should("batch across items, skipping the null ones") {
                val calls = AtomicInteger()
                val spec = reviewSpec(editorCalls = calls)
                val reviews =
                    listOf(
                        Review("r1", Expandable.Ref("a1"), editor = Expandable.Ref("a1")),
                        Review("r2", Expandable.Ref("a1"), editor = null),
                        Review("r3", Expandable.Ref("a1"), editor = Expandable.Ref("a2")),
                    )

                val result = spec.apply(reviews, expand("editor"))

                calls.get() shouldBe 1
                result[0].editor.shouldBeInstanceOf<Expandable.Resolved<Author>>()
                result[1].editor shouldBe null
                result[2].editor.shouldBeInstanceOf<Expandable.Resolved<Author>>()
            }
        }

        context("list field") {
            should("resolve every entry") {
                val spec = reviewSpec()

                val result =
                    spec.apply(
                        Review("r1", Expandable.Ref("a1"), mentions = listOf(Expandable.Ref("b1"), Expandable.Ref("b2"))),
                        expand("mentions"),
                    )

                result.mentions!!.map { it.shouldBeInstanceOf<Expandable.Resolved<Book>>().value } shouldContainExactlyInAnyOrder
                    listOf(books.getValue("b1"), books.getValue("b2"))
            }

            should("cover every item's refs with one call") {
                val calls = AtomicInteger()
                val spec = reviewSpec(mentionCalls = calls)
                val reviews =
                    listOf(
                        Review("r1", Expandable.Ref("a1"), mentions = listOf(Expandable.Ref("b1"))),
                        Review("r2", Expandable.Ref("a1"), mentions = listOf(Expandable.Ref("b2"))),
                    )

                spec.apply(reviews, expand("mentions"))

                calls.get() shouldBe 1
            }

            should("leave an item with a null list untouched") {
                val spec = reviewSpec()

                spec.apply(Review("r1", Expandable.Ref("a1"), mentions = null), expand("mentions")).mentions shouldBe null
            }
        }

        context("nesting") {
            should("apply the nested spec to the resolved value") {
                val bookSpec =
                    ExpandSpec.build<Book> {
                    }
                val authorSpec =
                    ExpandSpec.build<Author> {
                        listField(
                            name = "books",
                            getter = { it.books },
                            setter = { copy(books = it) },
                            batch = { ids, _ -> books.filterKeys { it in ids } },
                        )
                    }
                val spec =
                    ExpandSpec.build<Review> {
                        field(
                            name = "author",
                            getter = { it.author },
                            setter = { copy(author = it) },
                            nested = authorSpec,
                            batch = { ids, _ ->
                                ids.associateWith { id ->
                                    authors.getValue(id).copy(books = listOf(Expandable.Ref("b1")))
                                }
                            },
                        )
                    }
                bookSpec.knownFields shouldBe emptySet()

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), expand("author.books"))

                val author = result.author.shouldBeInstanceOf<Expandable.Resolved<Author>>().value
                author.books!!
                    .single()
                    .shouldBeInstanceOf<Expandable.Resolved<Book>>()
                    .value shouldBe books["b1"]
            }
        }

        context("field projection") {
            should("mark the value partial and tell the batcher which fields were asked for") {
                var requestedFields: Set<String> = emptySet()
                val spec =
                    ExpandSpec.build<Review> {
                        field(
                            name = "author",
                            getter = { it.author },
                            setter = { copy(author = it) },
                            batch = { ids, fields ->
                                requestedFields = fields
                                authors.filterKeys { it in ids }
                            },
                        )
                    }

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), expand("author.name"))

                requestedFields shouldBe setOf("name")
                result.author.shouldBeInstanceOf<Expandable.Partial<Author>>().fields shouldBe setOf("name")
            }

            should("not treat a registered nested field as a projection") {
                var requestedFields: Set<String> = setOf("sentinel")
                val authorSpec =
                    ExpandSpec.build<Author> {
                        listField(
                            name = "books",
                            getter = { it.books },
                            setter = { copy(books = it) },
                            batch = { ids, _ -> books.filterKeys { it in ids } },
                        )
                    }
                val spec =
                    ExpandSpec.build<Review> {
                        field(
                            name = "author",
                            getter = { it.author },
                            setter = { copy(author = it) },
                            nested = authorSpec,
                            batch = { ids, fields ->
                                requestedFields = fields
                                authors.filterKeys { it in ids }
                            },
                        )
                    }

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), expand("author.books"))

                requestedFields shouldBe emptySet()
                result.author.shouldBeInstanceOf<Expandable.Resolved<Author>>()
            }
        }

        context("knownFields") {
            should("list every registered field") {
                reviewSpec().knownFields shouldContainExactlyInAnyOrder listOf("author", "editor", "mentions")
            }
        }
    })

private fun reviewSpec(
    authorCalls: AtomicInteger = AtomicInteger(),
    editorCalls: AtomicInteger = AtomicInteger(),
    mentionCalls: AtomicInteger = AtomicInteger(),
): ExpandSpec<Review> =
    ExpandSpec.build {
        field(
            name = "author",
            getter = { it.author },
            setter = { copy(author = it) },
            batch = { ids, _ ->
                authorCalls.incrementAndGet()
                authors.filterKeys { it in ids }
            },
        )
        optionalField(
            name = "editor",
            getter = { it.editor },
            setter = { copy(editor = it) },
            batch = { ids, _ ->
                editorCalls.incrementAndGet()
                authors.filterKeys { it in ids }
            },
        )
        listField(
            name = "mentions",
            getter = { it.mentions },
            setter = { copy(mentions = it) },
            batch = { ids, _ ->
                mentionCalls.incrementAndGet()
                books.filterKeys { it in ids }
            },
        )
    }

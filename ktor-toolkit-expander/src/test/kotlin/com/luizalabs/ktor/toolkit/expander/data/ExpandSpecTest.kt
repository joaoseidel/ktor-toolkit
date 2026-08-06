package com.luizalabs.ktor.toolkit.expander.data

import com.luizalabs.ktor.toolkit.expander.web.ExpandRequest
import io.kotest.assertions.throwables.shouldThrow
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

/** Its organiser is an author or a book, depending on [organiserType]. */
private data class Report(
    val id: String,
    val organiserType: String,
    val organiser: Expandable<Any>,
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

        context("a nullable single field") {
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
            should("apply a shared nested spec to the resolved value") {
                val authorSpec =
                    ExpandSpec.build<Author> {
                        listField("books", get = { it.books }, set = { copy(books = it) }) {
                            batch { ids, _ -> books.filterKeys { it in ids } }
                        }
                    }
                val spec =
                    ExpandSpec.build<Review> {
                        field("author", get = { it.author }, set = { copy(author = it) }) {
                            nested(authorSpec)
                            batch { ids, _ ->
                                ids.associateWith { id ->
                                    authors.getValue(id).copy(books = listOf(Expandable.Ref("b1")))
                                }
                            }
                        }
                    }

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), expand("author.books"))

                val author = result.author.shouldBeInstanceOf<Expandable.Resolved<Author>>().value
                author.books!!
                    .single()
                    .shouldBeInstanceOf<Expandable.Resolved<Book>>()
                    .value shouldBe books["b1"]
            }

            should("accept a nested spec declared inline") {
                val spec =
                    ExpandSpec.build<Review> {
                        field("author", get = { it.author }, set = { copy(author = it) }) {
                            nested {
                                listField("books", get = { it.books }, set = { copy(books = it) }) {
                                    batch { ids, _ -> books.filterKeys { it in ids } }
                                }
                            }
                            batch { ids, _ ->
                                ids.associateWith { id ->
                                    authors.getValue(id).copy(books = listOf(Expandable.Ref("b2")))
                                }
                            }
                        }
                    }

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), expand("author.books"))

                result.author
                    .shouldBeInstanceOf<Expandable.Resolved<Author>>()
                    .value.books!!
                    .single()
                    .shouldBeInstanceOf<Expandable.Resolved<Book>>()
                    .value shouldBe books["b2"]
            }
        }

        context("polymorphic field") {
            val organiserSpec =
                ExpandSpec.build<Report> {
                    polymorphicField(
                        name = "organiser",
                        get = { it.organiser },
                        set = { copy(organiser = it) },
                        type = { it.organiserType },
                    ) {
                        case("author") { batch { ids, _ -> authors.filterKeys { it in ids } } }
                        case("book") { batch { ids, _ -> books.filterKeys { it in ids }.mapValues { (_, b) -> b } } }
                    }
                }

            should("resolve each item against the source its discriminator names") {
                val result =
                    organiserSpec.apply(
                        listOf(
                            Report("p1", "author", Expandable.Ref("a1")),
                            Report("p2", "book", Expandable.Ref("b1")),
                        ),
                        expand("organiser"),
                    )

                result[0].organiser.shouldBeInstanceOf<Expandable.Resolved<Any>>().value shouldBe authors["a1"]
                result[1].organiser.shouldBeInstanceOf<Expandable.Resolved<Any>>().value shouldBe books["b1"]
            }

            should("leave an item whose discriminator has no case untouched") {
                val result = organiserSpec.apply(Report("p3", "publisher", Expandable.Ref("a1")), expand("organiser"))

                result.organiser.shouldBeInstanceOf<Expandable.Ref>()
            }

            should("reject a duplicate case") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        ExpandSpec.build<Report> {
                            polymorphicField(
                                name = "organiser",
                                get = { it.organiser },
                                set = { copy(organiser = it) },
                                type = { it.organiserType },
                            ) {
                                case("author") { batch { _, _ -> emptyMap() } }
                                case("author") { batch { _, _ -> emptyMap() } }
                            }
                        }
                    }

                failure.message shouldBe "Duplicate case \"author\""
            }

            should("reject a field with no case at all") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        ExpandSpec.build<Report> {
                            polymorphicField(
                                name = "organiser",
                                get = { it.organiser },
                                set = { copy(organiser = it) },
                                type = { it.organiserType },
                            ) { }
                        }
                    }

                failure.message shouldBe "Polymorphic field \"organiser\" needs at least one case { } block"
            }
        }

        context("a malformed spec") {
            should("be rejected when a field has no batch") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        ExpandSpec.build<Review> {
                            field("author", get = { it.author }, set = { copy(author = it) }) { }
                        }
                    }

                failure.message shouldBe "Expandable field \"author\" needs a batch { } block to resolve its refs"
            }

            should("be rejected when a field has no name") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        ExpandSpec.build<Review> {
                            field(" ", get = { it.author }, set = { copy(author = it) }) {
                                batch { _, _ -> emptyMap() }
                            }
                        }
                    }

                failure.message shouldBe "An expandable field needs a name"
            }

            should("be rejected when two fields share a name") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        ExpandSpec.build<Review> {
                            field("author", get = { it.author }, set = { copy(author = it) }) {
                                batch { _, _ -> emptyMap() }
                            }
                            field("author", get = { it.editor }, set = { copy(editor = it) }) {
                                batch { _, _ -> emptyMap() }
                            }
                        }
                    }

                failure.message shouldBe "Duplicate expandable fields: author"
            }
        }

        context("field projection") {
            should("mark the value partial and tell the batcher which fields were asked for") {
                var requestedFields: Set<String> = emptySet()
                val spec =
                    ExpandSpec.build<Review> {
                        field("author", get = { it.author }, set = { copy(author = it) }) {
                            batch { ids, fields ->
                                requestedFields = fields
                                authors.filterKeys { it in ids }
                            }
                        }
                    }

                val result = spec.apply(Review("r1", Expandable.Ref("a1")), expand("author.name"))

                requestedFields shouldBe setOf("name")
                result.author.shouldBeInstanceOf<Expandable.Partial<Author>>().fields shouldBe setOf("name")
            }

            should("not treat a registered nested field as a projection") {
                var requestedFields: Set<String> = setOf("sentinel")
                val authorSpec =
                    ExpandSpec.build<Author> {
                        listField("books", get = { it.books }, set = { copy(books = it) }) {
                            batch { ids, _ -> books.filterKeys { it in ids } }
                        }
                    }
                val spec =
                    ExpandSpec.build<Review> {
                        field("author", get = { it.author }, set = { copy(author = it) }) {
                            nested(authorSpec)
                            batch { ids, fields ->
                                requestedFields = fields
                                authors.filterKeys { it in ids }
                            }
                        }
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
        field("author", get = { it.author }, set = { copy(author = it) }) {
            batch { ids, _ ->
                authorCalls.incrementAndGet()
                authors.filterKeys { it in ids }
            }
        }
        field("editor", get = { it.editor }, set = { copy(editor = it) }) {
            batch { ids, _ ->
                editorCalls.incrementAndGet()
                authors.filterKeys { it in ids }
            }
        }
        listField("mentions", get = { it.mentions }, set = { copy(mentions = it) }) {
            batch { ids, _ ->
                mentionCalls.incrementAndGet()
                books.filterKeys { it in ids }
            }
        }
    }

package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.validators.blank
import com.luizalabs.ktor.toolkit.validator.validators.email
import com.luizalabs.ktor.toolkit.validator.validators.nil
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.server.plugins.requestvalidation.ValidationResult

private data class Author(
    val name: String,
    val email: String,
)

private data class Publisher(
    val name: String,
    val country: String?,
)

private data class Book(
    val title: String,
    val tags: List<String>?,
    val authors: List<Author>?,
    val publisher: Publisher?,
    val draft: Boolean = false,
    val publishedYear: Int? = null,
)

private fun book(
    title: String = "Kotlin in Action",
    tags: List<String>? = emptyList(),
    authors: List<Author>? = emptyList(),
    publisher: Publisher? = Publisher("Manning", "US"),
    draft: Boolean = false,
    publishedYear: Int? = 2017,
) = Book(title, tags, authors, publisher, draft, publishedYear)

private fun validate(
    target: Book,
    block: ValidationContext<Book>.() -> Unit,
): List<Pair<String, String>> =
    ValidationContext(target)
        .apply(block)
        .errors
        .map { it.propertyPath to it.message }

class ValidationContextTest :
    ShouldSpec({
        context("property") {
            should("record errors under the property name") {
                validate(book(title = "")) {
                    property(Book::title) { should notBe blank() }
                } shouldBe listOf("title" to "should not be blank")
            }
        }

        context("nested") {
            should("prefix errors with the nested property path") {
                validate(book(publisher = Publisher("", "US"))) {
                    nested(Book::publisher) {
                        property(Publisher::name) { should notBe blank() }
                    }
                } shouldBe listOf("publisher.name" to "should not be blank")
            }

            should("report an absent nested object and skip its rules") {
                validate(book(publisher = null)) {
                    nested(Book::publisher) {
                        property(Publisher::name) { should notBe blank() }
                    }
                } shouldBe listOf("publisher" to "should not be null")
            }

            should("accept a custom message for the absent case") {
                validate(book(publisher = null)) {
                    nested(Book::publisher, nullMessage = "is required") { }
                } shouldBe listOf("publisher" to "is required")
            }

            should("compose paths through several levels") {
                validate(book(publisher = Publisher("Manning", null))) {
                    nested(Book::publisher) {
                        property(Publisher::country) { should notBe nil() }
                    }
                } shouldBe listOf("publisher.country" to "should not be null")
            }
        }

        context("each") {
            should("index the errors it records") {
                validate(book(tags = listOf("kotlin", "", "jvm", ""))) {
                    each(Book::tags) { should notBe blank() }
                } shouldBe
                    listOf(
                        "tags[1]" to "should not be blank",
                        "tags[3]" to "should not be blank",
                    )
            }

            should("skip an absent collection") {
                validate(book(tags = null)) {
                    each(Book::tags) { should notBe blank() }
                } shouldBe emptyList()
            }

            should("do nothing for an empty collection") {
                validate(book(tags = emptyList())) {
                    each(Book::tags) { should notBe blank() }
                } shouldBe emptyList()
            }
        }

        context("eachNested") {
            should("index the elements and name the property within each") {
                validate(book(authors = listOf(Author("Dmitry", "d@example.com"), Author("", "nope")))) {
                    eachNested(Book::authors) {
                        property(Author::name) { should notBe blank() }
                        property(Author::email) { should be email() }
                    }
                } shouldBe
                    listOf(
                        "authors[1].name" to "should not be blank",
                        "authors[1].email" to "should be a valid email address",
                    )
            }

            should("skip an absent collection") {
                validate(book(authors = null)) {
                    eachNested(Book::authors) { property(Author::name) { should notBe blank() } }
                } shouldBe emptyList()
            }
        }

        context("whenever") {
            should("apply the rules when the condition holds") {
                validate(book(draft = false, publishedYear = null)) {
                    whenever(!target.draft) {
                        property(Book::publishedYear) { should notBe nil() }
                    }
                } shouldBe listOf("publishedYear" to "should not be null")
            }

            should("skip them when it does not") {
                validate(book(draft = true, publishedYear = null)) {
                    whenever(!target.draft) {
                        property(Book::publishedYear) { should notBe nil() }
                    }
                } shouldBe emptyList()
            }

            should("keep the enclosing path when nested") {
                validate(book(publisher = Publisher("", "US"))) {
                    nested(Book::publisher) {
                        whenever(target.country == "US") {
                            property(Publisher::name) { should notBe blank() }
                        }
                    }
                } shouldBe listOf("publisher.name" to "should not be blank")
            }
        }

        context("invariant") {
            should("record an error against the object itself") {
                validate(book(title = "a", tags = listOf("b"))) {
                    invariant("title should be longer than its first tag") { it.title.length > it.tags!!.first().length }
                } shouldBe listOf("" to "title should be longer than its first tag")
            }

            should("stay quiet when the condition holds") {
                validate(book()) {
                    invariant("should have a title") { it.title.isNotEmpty() }
                } shouldBe emptyList()
            }

            should("accept an explicit path") {
                validate(book(publishedYear = 1200)) {
                    invariant("should not predate printing", path = "publishedYear") { it.publishedYear!! > 1450 }
                } shouldBe listOf("publishedYear" to "should not predate printing")
            }

            should("qualify that path with the enclosing context") {
                validate(book(publisher = Publisher("Manning", "US"))) {
                    nested(Book::publisher) {
                        invariant("should not be American", path = "country") { it.country != "US" }
                    }
                } shouldBe listOf("publisher.country" to "should not be American")
            }
        }

        context("hasErrors") {
            should("track whether anything was found") {
                val context = ValidationContext(book(title = ""))
                context.hasErrors shouldBe false

                context.property(Book::title) { should notBe blank() }
                context.hasErrors shouldBe true
            }
        }

        context("toValidationResult") {
            should("be valid when nothing was found") {
                ValidationContext(book()).toValidationResult() shouldBe ValidationResult.Valid
            }

            should("list every error, quoting its path") {
                val result =
                    ValidationContext(book(title = "", publisher = null))
                        .apply {
                            property(Book::title) { should notBe blank() }
                            nested(Book::publisher) { }
                        }.toValidationResult()

                result.shouldBeInstanceOf<ValidationResult.Invalid>().reasons shouldBe
                    listOf("`title` should not be blank", "`publisher` should not be null")
            }

            should("leave an object-level error unquoted") {
                val result =
                    ValidationContext(book())
                        .apply { invariant("should be published") { it.publishedYear == null } }
                        .toValidationResult()

                result.shouldBeInstanceOf<ValidationResult.Invalid>().reasons shouldBe listOf("should be published")
            }
        }
    })

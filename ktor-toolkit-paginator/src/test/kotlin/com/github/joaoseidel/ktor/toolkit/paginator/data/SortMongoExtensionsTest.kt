package com.github.joaoseidel.ktor.toolkit.paginator.data

import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.DESC
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bson.conversions.Bson

private data class BookDocument(
    val id: String,
    val title: String,
    val createdAt: String,
)

private val sortableFields = listOf("id", "title", "created_at")

/** `Sorts` returns opaque wrappers, so compare a sort document by what it renders to. */
private infix fun Bson.shouldSortBy(expected: String) = toBsonDocument().toJson() shouldBe expected

class SortMongoExtensionsTest :
    ShouldSpec({
        context("a single sort criterion") {
            should("sort by the matching field, ascending") {
                Sort("title", ASC).toMongoSortExpression(sortableFields) shouldSortBy """{"title": 1}"""
            }

            should("sort by the matching field, descending") {
                Sort("title", DESC).toMongoSortExpression(sortableFields) shouldSortBy """{"title": -1}"""
            }

            should("resolve against an allow-list passed as varargs") {
                Sort("created_at", DESC).toMongoSortExpression("title", "created_at") shouldSortBy """{"created_at": -1}"""
            }

            should("resolve against an allow-list of property references") {
                Sort("title", ASC).toMongoSortExpression(BookDocument::title, BookDocument::createdAt) shouldSortBy """{"title": 1}"""
            }

            should("name the offending property when it is outside the allow-list") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        Sort("created_at", ASC).toMongoSortExpression(listOf("title"))
                    }

                failure.message shouldContain "created_at"
            }

            should("reject a property no known field matches") {
                shouldThrow<IllegalArgumentException> {
                    Sort("nope", ASC).toMongoSortExpression(sortableFields)
                }
            }

            should("reject a property outside a property-reference allow-list") {
                shouldThrow<IllegalArgumentException> {
                    Sort("createdAt", ASC).toMongoSortExpression(BookDocument::id, BookDocument::title)
                }
            }
        }

        context("several sort criteria") {
            should("keep them in the order of precedence they were given in") {
                listOf(Sort("title", ASC), Sort("id", DESC))
                    .toMongoSortExpression(sortableFields) shouldSortBy """{"title": 1, "id": -1}"""
            }

            should("resolve against an allow-list passed as varargs") {
                listOf(Sort("id", ASC)).toMongoSortExpression("id", "title") shouldSortBy """{"id": 1}"""
            }

            should("resolve against an allow-list of property references") {
                listOf(Sort("createdAt", DESC), Sort("title", ASC))
                    .toMongoSortExpression(BookDocument::title, BookDocument::createdAt) shouldSortBy """{"createdAt": -1, "title": 1}"""
            }

            should("sort by nothing when no criterion was given") {
                emptyList<Sort>().toMongoSortExpression(sortableFields) shouldSortBy "{}"
            }

            should("fail as a whole when any one criterion is unknown") {
                shouldThrow<IllegalArgumentException> {
                    listOf(Sort("title", ASC), Sort("nope", ASC)).toMongoSortExpression(sortableFields)
                }
            }
        }
    })

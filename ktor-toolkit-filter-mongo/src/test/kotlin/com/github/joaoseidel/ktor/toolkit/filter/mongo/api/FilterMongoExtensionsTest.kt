package com.github.joaoseidel.ktor.toolkit.filter.mongo.api

import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.EQ
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.EXISTS
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.GT
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.GTE
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.LIKE
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.LT
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.LTE
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.NOT_EQ
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.NOT_IN
import com.github.joaoseidel.ktor.toolkit.filter.api.data.Filter.Operator.NOT_LIKE
import com.github.joaoseidel.ktor.toolkit.filter.api.error.InvalidFilterException
import com.mongodb.client.model.Filters
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.bson.conversions.Bson
import org.bson.types.ObjectId
import java.math.BigDecimal
import java.time.LocalDate
import java.util.regex.Pattern
import kotlin.time.Instant

private enum class DocumentStatus { DRAFT, PUBLISHED }

private data class BookDocument(
    val title: String,
    val subtitle: String?,
    val pages: Int,
    val copies: Long,
    val rating: Double,
    val price: BigDecimal,
    val inPrint: Boolean,
    val status: DocumentStatus,
    val publishedAt: Instant,
    val releasedOn: LocalDate,
    val authorId: ObjectId,
    val tags: List<String>,
)

private val fields =
    filterableFields {
        field(BookDocument::title)
        field(BookDocument::subtitle)
        field(BookDocument::pages)
        field(BookDocument::copies)
        field(BookDocument::rating)
        field(BookDocument::price)
        field(BookDocument::inPrint)
        field(BookDocument::status)
        field(BookDocument::publishedAt)
        field(BookDocument::releasedOn)
        field(BookDocument::authorId)
        field<java.time.Instant>("updated_at")
    }

private fun filter(
    property: String,
    operator: Filter.Operator,
    vararg values: String,
) = Filter(property, operator, values.asList())

private infix fun Bson.shouldQuery(expected: Bson) = toBsonDocument() shouldBe expected.toBsonDocument()

private fun containing(text: String) = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)

class FilterMongoExtensionsTest :
    ShouldSpec(
        {
            context("operators") {
                should("compare with the operand") {
                    filter("pages", EQ, "120").toMongoFilter(fields) shouldQuery Filters.eq("pages", 120)
                    filter("pages", NOT_EQ, "120").toMongoFilter(fields) shouldQuery Filters.ne("pages", 120)
                    filter("pages", GT, "120").toMongoFilter(fields) shouldQuery Filters.gt("pages", 120)
                    filter("pages", GTE, "120").toMongoFilter(fields) shouldQuery Filters.gte("pages", 120)
                    filter("pages", LT, "120").toMongoFilter(fields) shouldQuery Filters.lt("pages", 120)
                    filter("pages", LTE, "120").toMongoFilter(fields) shouldQuery Filters.lte("pages", 120)
                }

                should("search text literally, ignoring case") {
                    filter("title", LIKE, "c++ (2nd)").toMongoFilter(fields) shouldQuery Filters.regex("title", containing("c++ (2nd)"))
                    filter("title", NOT_LIKE, "draft").toMongoFilter(fields) shouldQuery Filters.not(Filters.regex("title", containing("draft")))
                }

                should("match any or none of a list") {
                    filter("pages", IN, "80", "120").toMongoFilter(fields) shouldQuery Filters.`in`("pages", listOf(80, 120))
                    filter("pages", NOT_IN, "80").toMongoFilter(fields) shouldQuery Filters.nin("pages", listOf(80))
                }

                should("ask for a value that is present and not null, or for none") {
                    filter("subtitle", EXISTS, "true").toMongoFilter(fields) shouldQuery Filters.ne("subtitle", null)
                    filter("subtitle", EXISTS, "false").toMongoFilter(fields) shouldQuery Filters.eq("subtitle", null)
                }
            }

            context("operand types") {
                should("read each declared type") {
                    filter("title", EQ, "Ktor").toMongoFilter(fields) shouldQuery Filters.eq("title", "Ktor")
                    filter("copies", EQ, "5").toMongoFilter(fields) shouldQuery Filters.eq("copies", 5L)
                    filter("rating", EQ, "4.5").toMongoFilter(fields) shouldQuery Filters.eq("rating", 4.5)
                    filter("price", EQ, "9.99").toMongoFilter(fields) shouldQuery Filters.eq("price", BigDecimal("9.99"))
                    filter("inPrint", EQ, "true").toMongoFilter(fields) shouldQuery Filters.eq("inPrint", true)
                    filter("status", EQ, "DRAFT").toMongoFilter(fields) shouldQuery Filters.eq("status", "DRAFT")
                    filter("releasedOn", EQ, "2026-01-31").toMongoFilter(fields) shouldQuery Filters.eq("releasedOn", LocalDate.of(2026, 1, 31))
                }

                should("query both kinds of instant as a BSON date") {
                    val instant = java.time.Instant.parse("2026-01-31T10:00:00Z")

                    filter("publishedAt", GTE, "2026-01-31T10:00:00Z").toMongoFilter(fields) shouldQuery Filters.gte("publishedAt", instant)
                    filter("updated_at", LT, "2026-01-31T10:00:00Z").toMongoFilter(fields) shouldQuery Filters.lt("updated_at", instant)
                }

                should("read an ObjectId") {
                    val id = ObjectId()

                    filter("authorId", EQ, id.toHexString()).toMongoFilter(fields) shouldQuery Filters.eq("authorId", id)
                }

                should("reject an operand that does not parse, naming it") {
                    val failure = shouldThrow<InvalidFilterException> { filter("pages", EQ, "many").toMongoFilter(fields) }

                    failure.message shouldContain "many"
                    failure.property shouldBe "pages"
                }

                should("reject an operand of any declared type that does not parse") {
                    shouldThrow<InvalidFilterException> { filter("status", EQ, "LOST").toMongoFilter(fields) }
                    shouldThrow<InvalidFilterException> { filter("inPrint", EQ, "yes").toMongoFilter(fields) }
                    shouldThrow<InvalidFilterException> { filter("authorId", IN, "nope").toMongoFilter(fields) }
                }

                should("reject a text search on a field that is not text") {
                    val failure = shouldThrow<InvalidFilterException> { filter("pages", LIKE, "1").toMongoFilter(fields) }

                    failure.message shouldContain "not text"
                }
            }

            context("the allow-list") {
                should("reject a field that is not declared") {
                    val failure = shouldThrow<InvalidFilterException> { filter("secret", EQ, "x").toMongoFilter(fields) }

                    failure.property shouldBe "secret"
                }

                should("refuse to declare a field of an unsupported type") {
                    val failure = shouldThrow<IllegalArgumentException> { filterableFields { field(BookDocument::tags) } }

                    failure.message shouldContain "tags"
                }
            }

            context("several filters") {
                should("require all of them") {
                    listOf(filter("pages", GT, "100"), filter("status", EQ, "DRAFT")).toMongoFilter(fields) shouldQuery
                        Filters.and(Filters.gt("pages", 100), Filters.eq("status", "DRAFT"))
                }

                should("match every document when there are none") {
                    emptyList<Filter>().toMongoFilter(fields) shouldQuery Filters.empty()
                }
            }
        },
    )

package com.github.joaoseidel.ktor.toolkit.filter.data

import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EQ
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EXISTS
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.GT
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.GTE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LIKE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LT
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LTE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_EQ
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_LIKE
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal

private enum class Status { DRAFT, PUBLISHED, ARCHIVED }

private object Books : Table("books") {
    val id = integer("id")
    val title = varchar("title", 255)
    val pages = integer("pages")
    val price = decimal("price", 10, 2)
    val inPrint = bool("in_print")
    val status = enumerationByName<Status>("status", 16)
    val shelf = enumeration<Status>("shelf")
    val subtitle = varchar("subtitle", 255).nullable()
    val cover = binary("cover", 8).nullable()

    override val primaryKey = PrimaryKey(id)
}

private val database =
    Database.connect("jdbc:h2:mem:filters;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver").also {
        transaction(it) {
            SchemaUtils.create(Books)
            book(1, "Kotlin in Action", 360, "39.90", inPrint = true, Status.PUBLISHED, subtitle = "Second Edition")
            book(2, "Ktor Recipes", 120, "19.50", inPrint = false, Status.DRAFT, subtitle = null)
            book(3, "100% Kotlin_Tips", 80, "9.99", inPrint = true, Status.ARCHIVED, subtitle = "Tips & Tricks")
            book(4, "Exposed Basics", 200, "29.00", inPrint = true, Status.PUBLISHED, subtitle = null)
        }
    }

private fun book(
    id: Int,
    title: String,
    pages: Int,
    price: String,
    inPrint: Boolean,
    status: Status,
    subtitle: String?,
) {
    Books.insert {
        it[Books.id] = id
        it[Books.title] = title
        it[Books.pages] = pages
        it[Books.price] = BigDecimal(price)
        it[Books.inPrint] = inPrint
        it[Books.status] = status
        it[Books.shelf] = status
        it[Books.subtitle] = subtitle
    }
}

/** The ids of the books a condition selects. */
private fun matching(condition: Op<Boolean>): List<Int> =
    transaction(database) {
        Books
            .selectAll()
            .where(condition)
            .map { it[Books.id] }
            .sorted()
    }

private fun matching(vararg filters: Filter): List<Int> = matching(filters.toList().toExposedCondition(Books))

private fun filter(
    property: String,
    operator: Filter.Operator,
    vararg values: String,
) = Filter(property, operator, values.asList())

class FilterExposedExtensionsTest :
    ShouldSpec(
        {
            context("comparisons") {
                should("match text and numbers exactly") {
                    matching(filter("title", EQ, "Ktor Recipes")) shouldBe listOf(2)
                    matching(filter("pages", EQ, "80")) shouldBe listOf(3)
                }

                should("order numbers and decimals") {
                    matching(filter("pages", GT, "200")) shouldBe listOf(1)
                    matching(filter("pages", GTE, "200")) shouldBe listOf(1, 4)
                    matching(filter("pages", LT, "120")) shouldBe listOf(3)
                    matching(filter("price", LTE, "19.50")) shouldBe listOf(2, 3)
                }

                should("read booleans and enums by name or by ordinal column") {
                    matching(filter("in_print", EQ, "false")) shouldBe listOf(2)
                    matching(filter("status", EQ, "PUBLISHED")) shouldBe listOf(1, 4)
                    matching(filter("shelf", EQ, "ARCHIVED")) shouldBe listOf(3)
                }

                should("exclude a value on a column that cannot be null") {
                    matching(filter("pages", NOT_EQ, "120")) shouldBe listOf(1, 3, 4)
                }

                should("keep rows without a value when excluding one on a nullable column") {
                    matching(filter("subtitle", NOT_EQ, "Second Edition")) shouldBe listOf(2, 3, 4)
                }
            }

            context("text search") {
                should("find the text anywhere, ignoring case") {
                    matching(filter("title", LIKE, "KOTLIN")) shouldBe listOf(1, 3)
                }

                should("take % and _ literally") {
                    matching(filter("title", LIKE, "100%")) shouldBe listOf(3)
                    matching(filter("title", LIKE, "n_t")) shouldBe listOf(3)
                }

                should("exclude a match, keeping rows without a value") {
                    matching(filter("subtitle", NOT_LIKE, "edition")) shouldBe listOf(2, 3, 4)
                    matching(filter("title", NOT_LIKE, "kotlin")) shouldBe listOf(2, 4)
                }
            }

            context("lists") {
                should("match any of the values") {
                    matching(filter("status", IN, "DRAFT", "ARCHIVED")) shouldBe listOf(2, 3)
                }

                should("exclude every value, keeping rows without one on a nullable column") {
                    matching(filter("pages", NOT_IN, "80", "120")) shouldBe listOf(1, 4)
                    matching(filter("subtitle", NOT_IN, "Second Edition")) shouldBe listOf(2, 3, 4)
                }
            }

            context("presence") {
                should("find rows with and without a value") {
                    matching(filter("subtitle", EXISTS, "true")) shouldBe listOf(1, 3)
                    matching(filter("subtitle", EXISTS, "false")) shouldBe listOf(2, 4)
                }
            }

            context("several filters") {
                should("require all of them") {
                    matching(filter("in_print", EQ, "true"), filter("pages", GTE, "100")) shouldBe listOf(1, 4)
                }

                should("match every row when there are none") {
                    matching() shouldBe listOf(1, 2, 3, 4)
                }
            }

            context("the allow-list") {
                should("resolve against explicit columns") {
                    matching(listOf(filter("pages", EQ, "80")).toExposedCondition(Books.pages, Books.title)) shouldBe listOf(3)
                    matching(filter("pages", EQ, "80").toExposedCondition(Books.pages)) shouldBe listOf(3)
                    matching(filter("pages", EQ, "80").toExposedCondition(Books)) shouldBe listOf(3)
                }

                should("reject a column the table has but the list leaves out") {
                    val failure =
                        shouldThrow<InvalidFilterException> {
                            filter("pages", EQ, "80").toExposedCondition(Books.title)
                        }

                    failure.message shouldContain "pages"
                    failure.property shouldBe "pages"
                }
            }

            context("operands of the wrong type") {
                should("be rejected, naming the value and the property") {
                    val failure = shouldThrow<InvalidFilterException> { matching(filter("pages", EQ, "many")) }

                    failure.message shouldContain "many"
                    failure.property shouldBe "pages"
                }

                should("be rejected for every type parsed here") {
                    shouldThrow<InvalidFilterException> { matching(filter("price", GT, "cheap")) }
                    shouldThrow<InvalidFilterException> { matching(filter("in_print", EQ, "yes")) }
                    shouldThrow<InvalidFilterException> { matching(filter("status", EQ, "LOST")) }
                    shouldThrow<InvalidFilterException> { matching(filter("shelf", IN, "DRAFT", "LOST")) }
                }

                should("reject a text search on a column that is not text") {
                    val failure = shouldThrow<InvalidFilterException> { matching(filter("pages", LIKE, "1")) }

                    failure.message shouldContain "not text"
                }

                should("reject an ordering on a column whose values do not compare") {
                    val failure = shouldThrow<InvalidFilterException> { matching(filter("cover", GT, "x")) }

                    failure.property shouldBe "cover"
                }
            }
        },
    )

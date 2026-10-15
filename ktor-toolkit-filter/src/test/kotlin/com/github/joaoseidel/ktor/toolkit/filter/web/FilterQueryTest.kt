package com.github.joaoseidel.ktor.toolkit.filter.web

import com.github.joaoseidel.ktor.toolkit.filter.data.Filter
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EQ
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EXISTS
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.GTE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LIKE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.LTE
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_IN
import com.github.joaoseidel.ktor.toolkit.filter.data.InvalidFilterException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.Parameters
import io.ktor.http.parametersOf

private fun parse(vararg filterBy: String): List<Filter> = FilterQuery.parse(parametersOf("filterBy", filterBy.asList()))

class FilterQueryTest :
    ShouldSpec(
        {
            context("well-formed conditions") {
                should("read property, operator and value") {
                    parse("price:gte:1000") shouldBe listOf(Filter("price", GTE, listOf("1000")))
                }

                should("read every comma-separated condition, in order") {
                    parse("price:gte:1000,title:like:kotlin") shouldBe
                        listOf(
                            Filter("price", GTE, listOf("1000")),
                            Filter("title", LIKE, listOf("kotlin")),
                        )
                }

                should("read repeated parameters as more conditions") {
                    parse("price:gte:1000", "price:lte:5000") shouldBe
                        listOf(
                            Filter("price", GTE, listOf("1000")),
                            Filter("price", LTE, listOf("5000")),
                        )
                }

                should("keep every colon after the operator in the value") {
                    parse("startsAt:eq:12:30:00") shouldBe listOf(Filter("startsAt", EQ, listOf("12:30:00")))
                }

                should("split in values on the pipe") {
                    parse("status:in:draft|published") shouldBe listOf(Filter("status", IN, listOf("draft", "published")))
                }

                should("split notIn values on the pipe too") {
                    parse("status:notIn:draft|archived") shouldBe listOf(Filter("status", NOT_IN, listOf("draft", "archived")))
                }

                should("leave a pipe alone outside in and notIn") {
                    parse("title:eq:a|b") shouldBe listOf(Filter("title", EQ, listOf("a|b")))
                }

                should("take an escaped comma, pipe or backslash literally") {
                    parse("""title:eq:Hello\, world""") shouldBe listOf(Filter("title", EQ, listOf("Hello, world")))
                    parse("""tag:in:a\|b|c""") shouldBe listOf(Filter("tag", IN, listOf("a|b", "c")))
                    parse("""path:eq:C:\\temp""") shouldBe listOf(Filter("path", EQ, listOf("""C:\temp""")))
                }

                should("keep a trailing lone backslash") {
                    parse("""title:eq:ends\""") shouldBe listOf(Filter("title", EQ, listOf("""ends\""")))
                }

                should("accept an empty value") {
                    parse("subtitle:eq:") shouldBe listOf(Filter("subtitle", EQ, listOf("")))
                }

                should("parse exists") {
                    parse("subtitle:exists:false") shouldBe listOf(Filter("subtitle", EXISTS, listOf("false")))
                }

                should("skip blank conditions and surrounding whitespace") {
                    parse(" price:gte:1 , ,") shouldBe listOf(Filter("price", GTE, listOf("1")))
                }
            }

            context("no conditions") {
                should("come from an absent parameter") {
                    FilterQuery.parse(Parameters.Empty).shouldBeEmpty()
                }

                should("come from a blank one") {
                    parse("").shouldBeEmpty()
                }
            }

            context("malformed conditions") {
                should("reject a condition without an operator and a value") {
                    val failure = shouldThrow<InvalidFilterException> { parse("price") }

                    failure.message shouldContain "property:operator:value"
                    failure.property.shouldBeNull()
                }

                should("reject a condition without a value") {
                    shouldThrow<InvalidFilterException> { parse("price:gte") }
                }

                should("reject an unknown operator, listing the known ones") {
                    val failure = shouldThrow<InvalidFilterException> { parse("price:between:1") }

                    failure.message shouldContain "between"
                    failure.message shouldContain "notIn"
                    failure.property shouldBe "price"
                }

                should("reject exists with anything but true or false") {
                    shouldThrow<InvalidFilterException> { parse("subtitle:exists:yes") }
                }
            }

            context("the condition limit") {
                should("accept up to the default of ten") {
                    parse(List(10) { "a:eq:$it" }.joinToString(",")).size shouldBe 10
                }

                should("reject one more than the default") {
                    val failure = shouldThrow<InvalidFilterException> { parse(List(11) { "a:eq:$it" }.joinToString(",")) }

                    failure.message shouldContain "10"
                }

                should("honour an endpoint's own limit") {
                    shouldThrow<InvalidFilterException> {
                        FilterQuery.parse(parametersOf("filterBy", "a:eq:1,b:eq:2"), maxFilters = 1)
                    }
                }
            }
        },
    )

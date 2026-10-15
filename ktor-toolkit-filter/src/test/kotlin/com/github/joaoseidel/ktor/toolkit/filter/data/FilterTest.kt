package com.github.joaoseidel.ktor.toolkit.filter.data

import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EQ
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.EXISTS
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.IN
import com.github.joaoseidel.ktor.toolkit.filter.data.Filter.Operator.NOT_IN
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private data class Book(
    val title: String,
)

class FilterTest :
    ShouldSpec(
        {
            context("a valid filter") {
                should("take its property name from a property reference") {
                    Filter(Book::title, EQ, "Kotlin") shouldBe Filter("title", EQ, listOf("Kotlin"))
                }

                should("accept several values for in and notIn") {
                    Filter("status", IN, listOf("a", "b")).values shouldBe listOf("a", "b")
                    Filter("status", NOT_IN, listOf("a")).values shouldBe listOf("a")
                }

                should("accept true or false for exists") {
                    Filter("subtitle", EXISTS, listOf("true")).exists shouldBe true
                    Filter("subtitle", EXISTS, listOf("false")).exists shouldBe false
                }

                should("survive a serialization round trip") {
                    val filter = Filter("price", Filter.Operator.GTE, listOf("10"))

                    Json.decodeFromString<Filter>(Json.encodeToString(filter)) shouldBe filter
                }
            }

            context("an invalid filter") {
                should("reject a blank property") {
                    val failure = shouldThrow<InvalidFilterException> { Filter(" ", EQ, listOf("x")) }

                    failure.property.shouldBeNull()
                }

                should("reject in without values") {
                    val failure = shouldThrow<InvalidFilterException> { Filter("status", IN, emptyList()) }

                    failure.message shouldContain "status:in"
                    failure.property shouldBe "status"
                }

                should("reject exists with anything but true or false") {
                    shouldThrow<InvalidFilterException> { Filter("subtitle", EXISTS, listOf("yes")) }
                    shouldThrow<InvalidFilterException> { Filter("subtitle", EXISTS, listOf("true", "false")) }
                }

                should("reject a single-value operator given none, or several") {
                    shouldThrow<InvalidFilterException> { Filter("title", EQ, emptyList()) }
                    shouldThrow<InvalidFilterException> { Filter("title", EQ, listOf("a", "b")) }
                }

                // The serialization plugin copies these checks into the constructor it decodes with, so each one is
                // exercised through decoding as well.
                should("be rejected on deserialization too") {
                    listOf(
                        """{"property":" ","operator":"EQ","values":["x"]}""",
                        """{"property":"status","operator":"IN","values":[]}""",
                        """{"property":"status","operator":"NOT_IN","values":[]}""",
                        """{"property":"subtitle","operator":"EXISTS","values":["yes"]}""",
                        """{"property":"title","operator":"EQ","values":[]}""",
                    ).forEach { json ->
                        shouldThrow<InvalidFilterException> { Json.decodeFromString<Filter>(json) }
                    }
                }

                should("be decoded when it fits its operator") {
                    Json.decodeFromString<Filter>("""{"property":"status","operator":"NOT_IN","values":["a"]}""") shouldBe
                        Filter("status", NOT_IN, listOf("a"))
                    Json.decodeFromString<Filter>("""{"property":"subtitle","operator":"EXISTS","values":["true"]}""") shouldBe
                        Filter("subtitle", EXISTS, listOf("true"))
                }

                should("be rejected when a field is missing") {
                    shouldThrow<SerializationException> { Json.decodeFromString<Filter>("""{"property":"title"}""") }
                }

                should("be an IllegalArgumentException, so the usual 400 mapping covers it") {
                    shouldThrow<IllegalArgumentException> { Filter("title", EQ, emptyList()) }
                }
            }

            context("Operator.fromToken") {
                should("find every operator by its token") {
                    Filter.Operator.entries.forEach { Filter.Operator.fromToken(it.token) shouldBe it }
                }

                should("answer null for an unknown or differently cased token") {
                    Filter.Operator.fromToken("between").shouldBeNull()
                    Filter.Operator.fromToken("EQ").shouldBeNull()
                }
            }
        },
    )

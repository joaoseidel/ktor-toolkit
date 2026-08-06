package com.github.joaoseidel.ktor.toolkit.expander.web

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.parametersOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private fun expand(value: String) = ExpandRequest.from(parametersOf("expand", value))

class ExpandRequestTest :
    ShouldSpec({
        context("parsing") {
            should("read a flat list of fields") {
                val request = expand("author,publisher")

                request.wants("author") shouldBe true
                request.wants("publisher") shouldBe true
                request.wants("chapters") shouldBe false
            }

            should("read dotted paths as a tree") {
                val request = expand("author.books")

                request.wants("author") shouldBe true
                request.child("author").wants("books") shouldBe true
            }

            should("merge sibling paths under one parent") {
                val request = expand("author.books,author.posts")

                val author = request.child("author")
                author.wants("books") shouldBe true
                author.wants("posts") shouldBe true
            }

            should("read arbitrarily deep paths") {
                val request = expand("a.b.c")

                request.child("a").child("b").wants("c") shouldBe true
            }

            should("ignore case and surrounding whitespace") {
                val request = expand(" Author , PUBLISHER ")

                request.wants("author") shouldBe true
                request.wants("AUTHOR") shouldBe true
                request.wants("publisher") shouldBe true
            }

            should("be empty when the parameter is absent") {
                ExpandRequest.from(parametersOf()).isEmpty shouldBe true
            }

            should("be empty when the parameter carries nothing usable") {
                expand("").isEmpty shouldBe true
                expand(" , , ").isEmpty shouldBe true
            }

            should("return an empty sub-tree for an unrequested child") {
                expand("author").child("publisher").isEmpty shouldBe true
                expand("author").child("author").isEmpty shouldBe true
            }
        }

        context("toFetchPaths") {
            should("include every intermediate path") {
                expand("author.books,author.posts").toFetchPaths() shouldContainExactlyInAnyOrder
                    listOf("author", "author.books", "author.posts")
            }

            should("list a flat request as-is") {
                expand("author,publisher").toFetchPaths() shouldContainExactlyInAnyOrder listOf("author", "publisher")
            }

            should("produce nothing for an empty request") {
                ExpandRequest.NONE.toFetchPaths() shouldBe emptyList()
            }
        }

        context("comparing two requests") {
            val request = expand("author.books")

            should("consider a request equal to itself") {
                request.equals(request) shouldBe true
            }

            should("consider two requests for the same fields equal") {
                request shouldBe expand("author.books")
                request.hashCode() shouldBe expand("author.books").hashCode()
            }

            should("consider a request different from anything that is not one") {
                request.equals("author.books") shouldBe false
            }

            should("consider requests for different fields different") {
                request shouldNotBe expand("author.posts")
                request shouldNotBe ExpandRequest.NONE
            }

            should("hand its tree back") {
                val (fields) = request

                fields.keys shouldBe setOf("author")
            }

            should("name the fields it carries in its string form") {
                request.toString() shouldContain "author"
            }

            should("report whether it asks for anything at all") {
                request.isEmpty shouldBe false
                ExpandRequest.NONE.isEmpty shouldBe true
            }
        }

        context("serializing a request") {
            should("survive a round trip") {
                Json.decodeFromString<ExpandRequest>(Json.encodeToString(request)) shouldBe request
            }

            should("refuse a payload that carries no tree") {
                shouldThrow<SerializationException> { Json.decodeFromString<ExpandRequest>("{}") }
            }
        }
    })

private val request = ExpandRequest.from(parametersOf("expand", "author.books"))

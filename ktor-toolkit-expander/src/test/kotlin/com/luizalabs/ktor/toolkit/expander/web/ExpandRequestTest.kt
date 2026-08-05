package com.luizalabs.ktor.toolkit.expander.web

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.ktor.http.parametersOf

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
    })

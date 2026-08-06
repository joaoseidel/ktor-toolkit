package com.luizalabs.ktor.toolkit.hateoas.data

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpMethod

private data class Volume(
    val id: String,
    val title: String,
)

class ResourceTest :
    ShouldSpec({
        val book = Volume("b1", "Dune")

        context("the resource builder") {
            should("wrap content with no links at all") {
                resource(book) shouldBe Resource(book)
            }

            should("publish links in the order they were declared") {
                val wrapped =
                    resource(book) {
                        link("self", "/books/b1")
                        link("delete", "/books/b1", HttpMethod.Delete)
                    }

                wrapped.content shouldBe book
                wrapped.links.map { it.rel to it.method } shouldBe listOf("self" to "GET", "delete" to "DELETE")
            }

            should("default a link to GET") {
                resource(book) { link("self", "/books/b1") }.links.single().method shouldBe "GET"
            }

            should("accept an already built link") {
                val link = Link("self", "/books/b1")

                resource(book) { link(link) }.links shouldBe listOf(link)
            }

            should("accept a whole list of them") {
                val existing = listOf(Link("self", "/books"), Link("next", "/books?page=1"))

                val wrapped =
                    resource(book) {
                        links(existing)
                        link("create", "/books", HttpMethod.Post)
                    }

                wrapped.links.map { it.rel } shouldBe listOf("self", "next", "create")
            }
        }

        context("withLink") {
            should("append a link built from its parts") {
                val wrapped = Resource(book).withLink("self", "/books/b1", HttpMethod.Head)

                wrapped.links shouldBe listOf(Link("self", "/books/b1", HttpMethod.Head))
            }

            should("keep the links already published") {
                val wrapped =
                    Resource(book)
                        .withLink("self", "/books/b1")
                        .withLink("delete", "/books/b1", HttpMethod.Delete)

                wrapped.links.map { it.rel } shouldBe listOf("self", "delete")
            }

            should("leave the receiver untouched") {
                val original = Resource(book)
                original.withLink("self", "/books/b1")

                original.links shouldBe emptyList()
            }
        }

        context("withLinks") {
            should("append every link, keeping the existing ones") {
                val wrapped =
                    Resource(book)
                        .withLink("self", "/books/b1")
                        .withLinks(listOf(Link("next", "/books/b2"), Link("prev", "/books/b0")))

                wrapped.links.map { it.rel } shouldBe listOf("self", "next", "prev")
            }
        }
    })

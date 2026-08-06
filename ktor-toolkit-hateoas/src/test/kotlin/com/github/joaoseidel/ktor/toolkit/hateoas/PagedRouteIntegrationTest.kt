package com.github.joaoseidel.ktor.toolkit.hateoas

import com.github.joaoseidel.ktor.toolkit.paginator.data.Paged
import com.github.joaoseidel.ktor.toolkit.paginator.pagination
import com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class BookResponse(
    val id: Int,
    val title: String,
)

/** The whole catalogue the fake route pages over. */
private val catalogue = (1..25).map { BookResponse(it, "Book $it") }

/** A route wired the way the README documents: parse, page, wrap, link. */
private fun pagedApp(block: suspend ApplicationTestBuilder.() -> Unit) =
    testApplication {
        install(ContentNegotiation) { json() }
        routing {
            get("/books") {
                val pagination = call.pagination
                val (page, pageSize) = pagination.page

                val content = catalogue.drop(page * pageSize).take(pageSize)
                val paged = Paged(pagination.page, pagination.sortBy, content, catalogue.size.toLong())

                call.respond(PagedResponse.from(paged) { it }.toResource(call))
            }
        }
        block()
    }

private fun JsonObject.links(): Map<String, String> =
    this["_links"]!!
        .jsonArray
        .associate { it.jsonObject["rel"]!!.jsonPrimitive.content to it.jsonObject["href"]!!.jsonPrimitive.content }

class PagedRouteIntegrationTest :
    ShouldSpec(
        {
            context("a paged route") {
                should("serve the first page with working navigation") {
                    pagedApp {
                        val response = client.get("/books?page=0&pageSize=10")
                        response.status shouldBe HttpStatusCode.OK

                        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                        val metadata = body["metadata"]!!.jsonObject

                        metadata["page"]!!.jsonPrimitive.content shouldBe "0"
                        metadata["totalPages"]!!.jsonPrimitive.content shouldBe "3"
                        metadata["totalElements"]!!.jsonPrimitive.content shouldBe "25"
                        metadata["hasNext"]!!.jsonPrimitive.content shouldBe "true"
                        metadata["hasPrevious"]!!.jsonPrimitive.content shouldBe "false"

                        body["content"]!!.jsonArray.size shouldBe 10
                        body.links().keys shouldContainExactly setOf("self", "next", "last")
                    }
                }

                should("let a client walk to the last page by following next") {
                    pagedApp {
                        var href = "/books?page=0&pageSize=10"
                        val visited = mutableListOf<String>()

                        repeat(3) {
                            val body = Json.parseToJsonElement(client.get(href).bodyAsText()).jsonObject
                            visited += body["metadata"]!!.jsonObject["page"]!!.jsonPrimitive.content
                            href = body.links()["next"] ?: return@repeat
                        }

                        // The last page must be reachable — the old totalPages made `next` stop one short.
                        visited shouldContainExactly listOf("0", "1", "2")
                    }
                }

                should("serve a short final page") {
                    pagedApp {
                        val body = Json.parseToJsonElement(client.get("/books?page=2&pageSize=10").bodyAsText()).jsonObject

                        body["content"]!!.jsonArray.size shouldBe 5
                        body["metadata"]!!.jsonObject["hasNext"]!!.jsonPrimitive.content shouldBe "false"
                        body.links().keys shouldContainExactly setOf("self", "prev", "first")
                    }
                }

                should("survive a malformed request by falling back to the defaults") {
                    pagedApp {
                        val response = client.get("/books?page=abc&pageSize=999999")

                        response.status shouldBe HttpStatusCode.OK

                        val metadata =
                            Json
                                .parseToJsonElement(response.bodyAsText())
                                .jsonObject["metadata"]!!
                                .jsonObject

                        metadata["page"]!!.jsonPrimitive.content shouldBe "0"
                        metadata["pageSize"]!!.jsonPrimitive.content shouldBe "100"
                    }
                }

                should("carry an unrelated filter through the navigation links") {
                    pagedApp {
                        val body =
                            Json
                                .parseToJsonElement(client.get("/books?page=0&pageSize=10&filter=a%26b").bodyAsText())
                                .jsonObject

                        val next = Url(body.links()["next"]!!)
                        next.parameters["filter"] shouldBe "a&b"
                        next.parameters["page"] shouldBe "1"
                    }
                }
            }
        },
    )

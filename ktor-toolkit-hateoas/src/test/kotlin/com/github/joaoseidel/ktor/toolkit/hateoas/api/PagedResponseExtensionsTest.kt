package com.github.joaoseidel.ktor.toolkit.hateoas.api

import com.github.joaoseidel.ktor.toolkit.hateoas.api.data.Link
import com.github.joaoseidel.ktor.toolkit.paginator.api.data.Page
import com.github.joaoseidel.ktor.toolkit.paginator.api.data.Paged
import com.github.joaoseidel.ktor.toolkit.paginator.api.web.PagedResponse
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val page = PagedResponse.from(Paged(Page(0, 10), emptyList(), (1..10).toList(), 25L)) { it }

private val custom = listOf(Link("create", "/numbers", HttpMethod.Post))

private suspend fun relsOf(route: suspend (io.ktor.server.routing.RoutingCall) -> Unit): List<String> {
    var rels: List<String> = emptyList()

    testApplication {
        install(ContentNegotiation) { json() }
        routing { get("/numbers") { route(call) } }

        rels =
            Json
                .parseToJsonElement(client.get("/numbers?page=0&pageSize=10").bodyAsText())
                .jsonObject["_links"]!!
                .jsonArray
                .map { it.jsonObject["rel"]!!.jsonPrimitive.content }
    }

    return rels
}

class PagedResponseExtensionsTest :
    ShouldSpec(
        {
            context("toResource") {
                should("publish the pagination links for the call") {
                    relsOf { call -> call.respond(page.toResource(call)) } shouldContainExactly listOf("self", "next", "last")
                }

                should("publish the same links from the request") {
                    relsOf { call -> call.respond(page.toResource(call.request)) } shouldContainExactly
                        listOf("self", "next", "last")
                }

                should("append custom links after the pagination ones") {
                    relsOf { call -> call.respond(page.toResource(call, custom)) } shouldContainExactly
                        listOf("self", "next", "last", "create")
                }

                should("publish a custom link exactly once") {
                    val rels = relsOf { call -> call.respond(page.toResource(call, custom)) }

                    rels.count { it == "create" } shouldBe 1
                }
            }
        },
    )

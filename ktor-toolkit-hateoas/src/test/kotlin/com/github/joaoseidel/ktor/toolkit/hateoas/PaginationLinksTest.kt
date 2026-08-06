package com.github.joaoseidel.ktor.toolkit.hateoas

import com.github.joaoseidel.ktor.toolkit.hateoas.data.Link
import com.github.joaoseidel.ktor.toolkit.paginator.data.Page
import com.github.joaoseidel.ktor.toolkit.paginator.data.Paged
import com.github.joaoseidel.ktor.toolkit.paginator.web.PagedResponse
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/** Drives the link builder through a real routing call, since it reads the request URL. */
private suspend fun linksFor(
    query: String,
    page: Int,
    pageSize: Int,
    totalElements: Long,
): List<Link> {
    var links = emptyList<Link>()

    testApplication {
        routing {
            get("/books") {
                val response =
                    PagedResponse.from<String, String>(
                        Paged(Page(page, pageSize), emptyList(), emptyList(), totalElements),
                    )
                links = call.createPaginationLinks(response)
                call.respondText("ok")
            }
        }

        client.get("/books$query").bodyAsText()
    }

    return links
}

private fun List<Link>.rels(): List<String> = map { it.rel }

private fun List<Link>.href(rel: String): String = single { it.rel == rel }.href

class PaginationLinksTest :
    ShouldSpec(
        {
            context("createPaginationLinks") {
                context("which links are emitted") {
                    should("emit only self when everything fits on one page") {
                        linksFor("?page=0&pageSize=10", page = 0, pageSize = 10, totalElements = 5)
                            .rels() shouldContainExactly listOf("self")
                    }

                    should("omit prev and first on the opening page") {
                        // 25 elements over a page size of 10 spans 3 pages.
                        linksFor("?page=0&pageSize=10", page = 0, pageSize = 10, totalElements = 25)
                            .rels() shouldContainExactly listOf("self", "next", "last")
                    }

                    should("emit every link in the middle of the range") {
                        linksFor("?page=1&pageSize=10", page = 1, pageSize = 10, totalElements = 25)
                            .rels() shouldContainExactly listOf("self", "next", "prev", "first", "last")
                    }

                    should("omit next and last on the closing page") {
                        linksFor("?page=2&pageSize=10", page = 2, pageSize = 10, totalElements = 25)
                            .rels() shouldContainExactly listOf("self", "prev", "first")
                    }

                    should("emit only self when there is no data at all") {
                        linksFor("?page=0&pageSize=10", page = 0, pageSize = 10, totalElements = 0)
                            .rels() shouldContainExactly listOf("self")
                    }
                }

                context("targets") {
                    should("point last at the final page index, not the page count") {
                        val links = linksFor("?page=0&pageSize=10", page = 0, pageSize = 10, totalElements = 25)

                        Url(links.href("last")).parameters["page"] shouldBe "2"
                    }

                    should("step next and prev by one page") {
                        val links = linksFor("?page=1&pageSize=10", page = 1, pageSize = 10, totalElements = 25)

                        Url(links.href("next")).parameters["page"] shouldBe "2"
                        Url(links.href("prev")).parameters["page"] shouldBe "0"
                        Url(links.href("first")).parameters["page"] shouldBe "0"
                    }

                    should("keep the request path") {
                        val links = linksFor("?page=0&pageSize=10", page = 0, pageSize = 10, totalElements = 5)

                        Url(links.href("self")).encodedPath shouldBe "/books"
                    }
                }

                context("carried-over query parameters") {
                    should("percent-encode values that would otherwise break the URL") {
                        // Every one of these produced a corrupt link when the query string was concatenated by hand.
                        val hostile =
                            mapOf(
                                "amp" to "a&b",
                                "eq" to "a=b",
                                "space" to "a b",
                                "accent" to "ação",
                                "hash" to "a#b",
                            )
                        val query = hostile.entries.joinToString("&") { (k, v) -> "$k=${v.encodeForQuery()}" }

                        val links = linksFor("?page=0&pageSize=10&$query", page = 0, pageSize = 10, totalElements = 5)

                        val roundTripped = Url(links.href("self")).parameters
                        hostile.forEach { (key, value) ->
                            withClue("parameter $key") { roundTripped[key] shouldBe value }
                        }
                    }

                    should("preserve repeated parameters") {
                        val links = linksFor("?page=0&pageSize=10&tag=a&tag=b", page = 0, pageSize = 10, totalElements = 5)

                        Url(links.href("self")).parameters.getAll("tag") shouldContainExactly listOf("a", "b")
                    }

                    should("replace the incoming pagination parameters rather than duplicate them") {
                        val links = linksFor("?page=1&pageSize=10", page = 1, pageSize = 10, totalElements = 25)

                        val parameters = Url(links.href("next")).parameters
                        parameters.getAll("page") shouldContainExactly listOf("2")
                        parameters.getAll("pageSize") shouldContainExactly listOf("10")
                    }
                }

                context("serialization") {
                    should("default the method to GET") {
                        val links = linksFor("?page=0&pageSize=10", page = 0, pageSize = 10, totalElements = 5)

                        links.single().method shouldBe "GET"
                    }

                    should("round-trip through JSON") {
                        val link = Link("self", "/books?page=0")

                        Json.decodeFromString<Link>(Json.encodeToString(link)) shouldBe link
                    }
                }
            }
        },
    )

private fun String.encodeForQuery(): String =
    java.net.URLEncoder
        .encode(this, Charsets.UTF_8)

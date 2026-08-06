package com.github.joaoseidel.ktor.toolkit.paginator.web

import com.github.joaoseidel.ktor.toolkit.paginator.data.Page
import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.parametersOf
import kotlinx.serialization.json.Json

private data class ClampCase(
    val page: String,
    val pageSize: String,
    val expected: Page,
)

class PaginationRequestTest :
    ShouldSpec(
        {
            context("PaginationRequest.from(Parameters)") {
                should("read a well-formed request") {
                    val request =
                        PaginationRequest.from(
                            parametersOf(
                                "page" to listOf("2"),
                                "pageSize" to listOf("25"),
                                "sortBy" to listOf("name,-createdAt"),
                            ),
                        )

                    request.page shouldBe Page(2, 25)
                    request.sortBy shouldBe
                        listOf(
                            Sort("name", Sort.Direction.ASC),
                            Sort("createdAt", Sort.Direction.DESC),
                        )
                }

                should("fall back to the defaults when nothing is supplied") {
                    val request = PaginationRequest.from(parametersOf())

                    request.page shouldBe Page(PaginationRequest.DEFAULT_PAGE, PaginationRequest.DEFAULT_PAGE_SIZE)
                    request.sortBy shouldBe emptyList()
                }

                should("fall back to the defaults rather than throw on unparseable numbers") {
                    val request =
                        PaginationRequest.from(
                            parametersOf("page" to listOf("abc"), "pageSize" to listOf("")),
                        )

                    request.page shouldBe Page(PaginationRequest.DEFAULT_PAGE, PaginationRequest.DEFAULT_PAGE_SIZE)
                }

                should("clamp values that are out of range") {
                    val cases =
                        listOf(
                            ClampCase(page = "-1", pageSize = "10", expected = Page(0, 10)),
                            ClampCase(page = "-999", pageSize = "10", expected = Page(0, 10)),
                            // A page size of zero would divide by zero downstream.
                            ClampCase(page = "0", pageSize = "0", expected = Page(0, 1)),
                            ClampCase(page = "0", pageSize = "-5", expected = Page(0, 1)),
                            // Without an upper bound this is a way to ask for the whole table.
                            ClampCase(
                                page = "0",
                                pageSize = "10000000",
                                expected = Page(0, PaginationRequest.DEFAULT_MAX_PAGE_SIZE),
                            ),
                        )

                    cases.forEach { case ->
                        withClue("?page=${case.page}&pageSize=${case.pageSize}") {
                            val request =
                                PaginationRequest.from(
                                    parametersOf(
                                        "page" to listOf(case.page),
                                        "pageSize" to listOf(case.pageSize),
                                    ),
                                )

                            request.page shouldBe case.expected
                        }
                    }
                }

                should("honour a caller-supplied maximum page size") {
                    val request =
                        PaginationRequest.from(
                            parametersOf("pageSize" to listOf("500")),
                            maxPageSize = 200,
                        )

                    request.page.pageSize shouldBe 200
                }

                should("honour a caller-supplied default page size") {
                    val request = PaginationRequest.from(parametersOf(), defaultPageSize = 50)

                    request.page.pageSize shouldBe 50
                }

                should("ignore blank sort tokens") {
                    val request = PaginationRequest.from(parametersOf("sortBy" to listOf("name,, ,-age")))

                    request.sortBy shouldBe
                        listOf(
                            Sort("name", Sort.Direction.ASC),
                            Sort("age", Sort.Direction.DESC),
                        )
                }
            }

            context("PaginationRequest.from(page, pageSize, sortBy)") {
                should("apply the standard maximum page size when the caller names none") {
                    val request = PaginationRequest.from(page = 0, pageSize = 5_000, sortBy = emptyList())

                    request.page.pageSize shouldBe PaginationRequest.DEFAULT_MAX_PAGE_SIZE
                }

                should("read the sort tokens it was handed directly") {
                    val request = PaginationRequest.from(page = 1, pageSize = 20, sortBy = listOf("name", "-age"))

                    request.page shouldBe Page(1, 20)
                    request.sortBy shouldBe
                        listOf(
                            Sort("name", Sort.Direction.ASC),
                            Sort("age", Sort.Direction.DESC),
                        )
                }
            }

            context("comparing two requests") {
                val request = PaginationRequest(Page(1, 20), listOf(Sort("name", Sort.Direction.ASC)))

                should("consider a request equal to itself") {
                    request.equals(request) shouldBe true
                }

                should("consider a request equal to a structurally identical one") {
                    request shouldBe request.copy()
                    request.hashCode() shouldBe request.copy().hashCode()
                }

                should("consider a request different from anything that is not one") {
                    request.equals("?page=1&pageSize=20") shouldBe false
                }

                should("consider requests different when any single detail differs") {
                    val variants =
                        mapOf(
                            "the page being asked for" to request.copy(page = Page(2, 20)),
                            "the sort criteria" to request.copy(sortBy = emptyList()),
                        )

                    variants.forEach { (detail, variant) ->
                        withClue("differing in $detail") { request shouldNotBe variant }
                    }
                }

                should("hand its parts back in declaration order") {
                    val (page, sortBy) = request

                    page shouldBe Page(1, 20)
                    sortBy shouldBe listOf(Sort("name", Sort.Direction.ASC))
                }

                should("name every part in its string form") {
                    request.toString() shouldContain "page="
                    request.toString() shouldContain "sortBy="
                }
            }

            context("serializing a request") {
                should("survive a round trip") {
                    val request = PaginationRequest(Page(3, 15), listOf(Sort("name", Sort.Direction.DESC)))

                    Json.decodeFromString<PaginationRequest>(Json.encodeToString(request)) shouldBe request
                }

                should("fall back to the defaults for fields the payload leaves out") {
                    Json.decodeFromString<PaginationRequest>("{}") shouldBe PaginationRequest()
                }

                should("omit the fields that still hold their default") {
                    Json.encodeToString(PaginationRequest()) shouldBe "{}"
                }

                should("spell the defaults out when the format asks it to") {
                    val verbose = Json { encodeDefaults = true }

                    verbose.encodeToString(PaginationRequest()) shouldBe """{"page":{"page":0,"pageSize":10},"sortBy":[]}"""
                }
            }
        },
    )

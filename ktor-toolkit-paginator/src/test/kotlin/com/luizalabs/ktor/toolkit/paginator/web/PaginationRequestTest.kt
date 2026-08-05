package com.luizalabs.ktor.toolkit.paginator.web

import com.luizalabs.ktor.toolkit.paginator.data.Page
import com.luizalabs.ktor.toolkit.paginator.data.Sort
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.parametersOf

private data class ClampCase(
    val page: String,
    val pageSize: String,
    val expected: Page,
)

class PaginationRequestTest :
    ShouldSpec({
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
    })

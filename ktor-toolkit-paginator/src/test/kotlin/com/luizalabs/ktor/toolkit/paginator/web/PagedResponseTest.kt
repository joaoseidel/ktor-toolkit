package com.luizalabs.ktor.toolkit.paginator.web

import com.luizalabs.ktor.toolkit.paginator.data.Page
import com.luizalabs.ktor.toolkit.paginator.data.Paged
import com.luizalabs.ktor.toolkit.paginator.data.Sort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

private fun paged(
    page: Int,
    pageSize: Int,
    totalElements: Long,
    content: List<String> = emptyList(),
    sortBy: List<Sort> = emptyList(),
) = Paged(Page(page, pageSize), sortBy, content, totalElements)

class PagedResponseTest :
    ShouldSpec({
        context("PagedResponse.from") {
            context("totalPages") {
                should("count pages rather than report the last page index") {
                    val cases =
                        listOf(
                            Triple(0L, 10, 0),
                            Triple(1L, 10, 1),
                            Triple(9L, 10, 1),
                            // The boundary the old implementation got wrong: an exact multiple.
                            Triple(10L, 10, 1),
                            Triple(11L, 10, 2),
                            Triple(20L, 10, 2),
                            // 25 elements over a page size of 10 spans 3 pages, not 2.
                            Triple(25L, 10, 3),
                            Triple(1L, 1, 1),
                            Triple(7L, 3, 3),
                        )

                    cases.forEach { (totalElements, pageSize, expectedTotalPages) ->
                        withClue("$totalElements elements over a page size of $pageSize") {
                            PagedResponse
                                .from<String, String>(paged(0, pageSize, totalElements))
                                .metadata.totalPages shouldBe expectedTotalPages
                        }
                    }
                }
            }

            context("navigation flags") {
                should("offer a next page on every page but the last") {
                    // 25 elements over a page size of 10 means page 2 is the last one.
                    val cases =
                        listOf(
                            Triple(0, true, false),
                            Triple(1, true, true),
                            Triple(2, false, true),
                        )

                    cases.forEach { (page, expectedHasNext, expectedHasPrevious) ->
                        withClue("page $page of 25 elements over a page size of 10") {
                            val metadata = PagedResponse.from<String, String>(paged(page, 10, 25)).metadata

                            metadata.hasNext shouldBe expectedHasNext
                            metadata.hasPrevious shouldBe expectedHasPrevious
                        }
                    }
                }

                should("offer no navigation at all when there is no data") {
                    val metadata = PagedResponse.from<String, String>(paged(0, 10, 0)).metadata

                    metadata.totalPages shouldBe 0
                    metadata.hasNext shouldBe false
                    metadata.hasPrevious shouldBe false
                }
            }

            context("page size") {
                should("reject a non-positive page size instead of dividing by zero") {
                    listOf(0, -1).forEach { pageSize ->
                        withClue("page size $pageSize") {
                            shouldThrow<IllegalArgumentException> {
                                PagedResponse.from<String, String>(paged(0, pageSize, 10))
                            }
                        }
                    }
                }
            }

            context("sorting") {
                should("report unsorted data") {
                    val metadata = PagedResponse.from<String, String>(paged(0, 10, 5)).metadata

                    metadata.isSorted shouldBe false
                    metadata.sortCriteria shouldBe emptyList()
                }

                should("carry the sort criteria through") {
                    val sortBy = listOf(Sort("name", Sort.Direction.ASC), Sort("createdAt", Sort.Direction.DESC))

                    val metadata = PagedResponse.from<String, String>(paged(0, 10, 5, sortBy = sortBy)).metadata

                    metadata.isSorted shouldBe true
                    metadata.sortCriteria shouldBe sortBy
                }
            }

            context("content") {
                should("pass the content through untouched without a transformer") {
                    val content = listOf("a", "b")

                    PagedResponse.from<String, String>(paged(0, 10, 2, content)).content shouldBe content
                }

                should("apply the transformer to every element") {
                    val response = PagedResponse.from(paged(0, 10, 2, listOf("a", "b"))) { it.uppercase() }

                    response.content shouldBe listOf("A", "B")
                }
            }
        }
    })

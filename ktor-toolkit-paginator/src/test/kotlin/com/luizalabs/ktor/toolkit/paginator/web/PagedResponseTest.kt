package com.luizalabs.ktor.toolkit.paginator.web

import com.luizalabs.ktor.toolkit.paginator.data.Page
import com.luizalabs.ktor.toolkit.paginator.data.Paged
import com.luizalabs.ktor.toolkit.paginator.data.Sort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

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

        context("comparing two responses") {
            val response = PagedResponse.from<String, String>(paged(0, 10, 2, listOf("a", "b")))

            should("consider a response equal to itself") {
                response.equals(response) shouldBe true
            }

            should("consider a response equal to one built from the same page") {
                val same = PagedResponse.from<String, String>(paged(0, 10, 2, listOf("a", "b")))

                response shouldBe same
                response.hashCode() shouldBe same.hashCode()
            }

            should("consider a response different from anything that is not one") {
                response.equals("two of two") shouldBe false
            }

            should("consider responses different when the metadata differs") {
                response shouldNotBe PagedResponse.from<String, String>(paged(0, 10, 20, listOf("a", "b")))
            }

            should("consider responses different when the content differs") {
                response shouldNotBe PagedResponse.from<String, String>(paged(0, 10, 2, listOf("a", "c")))
            }

            should("hand its parts back in declaration order") {
                val (metadata, content) = response

                metadata.totalElements shouldBe 2L
                content shouldBe listOf("a", "b")
            }

            should("name every part in its string form") {
                response.toString() shouldContain "metadata="
                response.toString() shouldContain "content="
            }
        }

        context("comparing two sets of metadata") {
            val metadata =
                PagedResponse
                    .from<String, String>(paged(1, 10, 25, sortBy = listOf(Sort("name", Sort.Direction.ASC))))
                    .metadata

            should("consider metadata equal to itself") {
                metadata.equals(metadata) shouldBe true
            }

            should("consider metadata equal to a structurally identical set") {
                metadata shouldBe metadata.copy()
                metadata.hashCode() shouldBe metadata.copy().hashCode()
            }

            should("consider metadata different from anything that is not metadata") {
                metadata.equals("page 1 of 3") shouldBe false
            }

            should("consider metadata different when any single detail differs") {
                val variants =
                    mapOf(
                        "the page index" to metadata.copy(page = 2),
                        "the page size" to metadata.copy(pageSize = 20),
                        "the page count" to metadata.copy(totalPages = 4),
                        "the total element count" to metadata.copy(totalElements = 26),
                        "whether a next page exists" to metadata.copy(hasNext = false),
                        "whether a previous page exists" to metadata.copy(hasPrevious = false),
                        "whether the data is sorted" to metadata.copy(isSorted = false),
                        "the sort criteria" to metadata.copy(sortCriteria = emptyList()),
                    )

                variants.forEach { (detail, variant) ->
                    withClue("differing in $detail") { metadata shouldNotBe variant }
                }
            }

            should("hand its parts back in declaration order") {
                val (page, pageSize, totalPages, totalElements, hasNext, hasPrevious, isSorted) = metadata

                page shouldBe 1
                pageSize shouldBe 10
                totalPages shouldBe 3
                totalElements shouldBe 25L
                hasNext shouldBe true
                hasPrevious shouldBe true
                isSorted shouldBe true
                metadata.component8() shouldBe listOf(Sort("name", Sort.Direction.ASC))
            }

            should("name every part in its string form") {
                val described = metadata.toString()

                listOf(
                    "page=1",
                    "pageSize=10",
                    "totalPages=3",
                    "totalElements=25",
                    "hasNext=true",
                    "hasPrevious=true",
                    "isSorted=true",
                    "sortCriteria=",
                ).forEach { withClue(it) { described shouldContain it } }
            }
        }

        context("serializing a response") {
            should("survive a round trip") {
                val response =
                    PagedResponse.from<String, String>(
                        paged(1, 10, 25, listOf("a"), listOf(Sort("name", Sort.Direction.DESC))),
                    )

                Json.decodeFromString<PagedResponse<String>>(Json.encodeToString(response)) shouldBe response
            }

            should("fall back to the defaults for fields the payload leaves out") {
                val json =
                    """
                    {"metadata":{"page":0,"pageSize":10,"totalPages":0,"totalElements":0,
                    "hasNext":false,"hasPrevious":false,"isSorted":false}}
                    """.trimIndent().replace("\n", "")

                val response = Json.decodeFromString<PagedResponse<String>>(json)

                response.content shouldBe emptyList()
                response.metadata.sortCriteria shouldBe emptyList()
            }

            should("omit the fields that still hold their default") {
                val response = PagedResponse.from<String, String>(paged(0, 10, 0))

                Json.encodeToString(response) shouldBe
                    """{"metadata":{"page":0,"pageSize":10,"totalPages":0,"totalElements":0,""" +
                    """"hasNext":false,"hasPrevious":false,"isSorted":false}}"""
            }

            should("spell the defaults out when the format asks it to") {
                val verbose = Json { encodeDefaults = true }
                val response = PagedResponse.from<String, String>(paged(0, 10, 0))

                verbose.encodeToString(response) shouldBe
                    """{"metadata":{"page":0,"pageSize":10,"totalPages":0,"totalElements":0,""" +
                    """"hasNext":false,"hasPrevious":false,"isSorted":false,"sortCriteria":[]},"content":[]}"""
            }

            should("refuse a payload that leaves the metadata out") {
                shouldThrow<SerializationException> {
                    Json.decodeFromString<PagedResponse<String>>("""{"content":["a"]}""")
                }
            }

            should("refuse metadata that leaves a required field out") {
                shouldThrow<SerializationException> {
                    Json.decodeFromString<PagedResponse<String>>("""{"metadata":{"page":0}}""")
                }
            }
        }
    })

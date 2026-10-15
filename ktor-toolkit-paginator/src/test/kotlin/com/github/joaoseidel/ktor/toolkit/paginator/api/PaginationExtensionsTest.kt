package com.github.joaoseidel.ktor.toolkit.paginator.api

import com.github.joaoseidel.ktor.toolkit.paginator.api.data.Page
import com.github.joaoseidel.ktor.toolkit.paginator.api.data.Sort
import com.github.joaoseidel.ktor.toolkit.paginator.api.web.PaginationRequest
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class PaginationExtensionsTest :
    ShouldSpec(
        {
            context("PaginationRequest.toPagination") {
                should("carry the page and the sort criteria over") {
                    val sortBy = listOf(Sort("name", Sort.Direction.ASC))
                    val request = PaginationRequest(Page(2, 25), sortBy)

                    val pagination = request.toPagination()

                    pagination.page shouldBe Page(2, 25)
                    pagination.sortBy shouldBe sortBy
                }

                should("produce an empty sort when none was requested") {
                    PaginationRequest().toPagination().sortBy shouldBe emptyList()
                }
            }
        },
    )

package com.github.joaoseidel.ktor.toolkit.paginator.data

import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.github.joaoseidel.ktor.toolkit.paginator.data.Sort.Direction.DESC
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table

private object Books : Table("books") {
    val id = integer("id")
    val title = varchar("title", 255)
    val createdAt = varchar("created_at", 32)
}

class SortExposedExtensionsTest :
    ShouldSpec({
        context("Sort.toExposedQueryExpression") {
            should("map a direction onto the matching column") {
                Sort("title", ASC).toExposedQueryExpression(Books) shouldBe (Books.title to SortOrder.ASC)
                Sort("title", DESC).toExposedQueryExpression(Books) shouldBe (Books.title to SortOrder.DESC)
            }

            should("resolve against an explicit column allow-list") {
                val expression = Sort("title", ASC).toExposedQueryExpression(Books.title, Books.id)

                expression shouldBe (Books.title to SortOrder.ASC)
            }

            should("reject a property outside the allow-list, even when the table has it") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        Sort("created_at", ASC).toExposedQueryExpression(listOf(Books.title))
                    }

                failure.message shouldContain "created_at"
            }

            should("reject a property no column matches") {
                shouldThrow<IllegalArgumentException> {
                    Sort("nope", ASC).toExposedQueryExpression(Books)
                }
            }
        }

        context("List<Sort>.toExposedQueryExpression") {
            should("preserve the order of precedence") {
                val sortBy = listOf(Sort("title", ASC), Sort("id", DESC))

                sortBy.toExposedQueryExpression(Books) shouldBe
                    listOf(
                        Books.title to SortOrder.ASC,
                        Books.id to SortOrder.DESC,
                    )
            }

            should("resolve against an explicit column allow-list") {
                val expressions = listOf(Sort("id", DESC)).toExposedQueryExpression(Books.id, Books.title)

                expressions shouldBe listOf(Books.id to SortOrder.DESC)
            }

            should("produce nothing for an empty sort") {
                emptyList<Sort>().toExposedQueryExpression(Books) shouldBe emptyList()
            }

            should("fail as a whole when any entry is unknown") {
                shouldThrow<IllegalArgumentException> {
                    listOf(Sort("title", ASC), Sort("nope", ASC)).toExposedQueryExpression(Books)
                }
            }
        }
    })

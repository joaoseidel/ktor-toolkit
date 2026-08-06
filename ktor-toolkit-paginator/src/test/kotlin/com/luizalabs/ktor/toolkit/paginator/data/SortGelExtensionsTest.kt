package com.luizalabs.ktor.toolkit.paginator.data

import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.ASC
import com.luizalabs.ktor.toolkit.paginator.data.Sort.Direction.DESC
import io.github.joaoseidel.geldsl.core.GelOrderingExpression
import io.github.joaoseidel.geldsl.core.entity.properties.GelPropertyRef
import io.github.joaoseidel.geldsl.core.entity.properties.GelPropertyTypes
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private object BookProps {
    val id = GelPropertyRef("id", GelPropertyTypes.Uuid)
    val title = GelPropertyRef("title", GelPropertyTypes.Str)
    val createdAt = GelPropertyRef("created_at", GelPropertyTypes.Datetime)

    val all = listOf(id, title, createdAt)
}

/** `GelOrderingExpression` has no structural equality, so compare it by what it renders to. */
private infix fun GelOrderingExpression.shouldOrderBy(expected: String) = expression shouldBe expected

class SortGelExtensionsTest :
    ShouldSpec({
        context("a single sort criterion") {
            should("order by the matching property, ascending") {
                Sort("title", ASC).toGelOrderingExpression(BookProps.all) shouldOrderBy ".title ASC"
            }

            should("order by the matching property, descending") {
                Sort("title", DESC).toGelOrderingExpression(BookProps.all) shouldOrderBy ".title DESC"
            }

            should("resolve against an allow-list passed as varargs") {
                Sort("created_at", DESC).toGelOrderingExpression(BookProps.title, BookProps.createdAt) shouldOrderBy ".created_at DESC"
            }

            should("name the offending property when it is outside the allow-list") {
                val failure =
                    shouldThrow<IllegalArgumentException> {
                        Sort("created_at", ASC).toGelOrderingExpression(listOf(BookProps.title))
                    }

                failure.message shouldContain "created_at"
            }

            should("reject a property no known one matches") {
                shouldThrow<IllegalArgumentException> {
                    Sort("nope", ASC).toGelOrderingExpression(BookProps.all)
                }
            }
        }

        context("several sort criteria") {
            should("keep them in the order of precedence they were given in") {
                val ordering = listOf(Sort("title", ASC), Sort("id", DESC)).toGelOrderingExpression(BookProps.all)

                ordering.map { it.expression } shouldBe listOf(".title ASC", ".id DESC")
            }

            should("resolve against an allow-list passed as varargs") {
                val ordering = listOf(Sort("id", ASC)).toGelOrderingExpression(BookProps.id, BookProps.title)

                ordering.map { it.expression } shouldBe listOf(".id ASC")
            }

            should("order by nothing when no criterion was given") {
                emptyList<Sort>().toGelOrderingExpression(BookProps.all) shouldBe emptyList()
            }

            should("fail as a whole when any one criterion is unknown") {
                shouldThrow<IllegalArgumentException> {
                    listOf(Sort("title", ASC), Sort("nope", ASC)).toGelOrderingExpression(BookProps.all)
                }
            }
        }
    })

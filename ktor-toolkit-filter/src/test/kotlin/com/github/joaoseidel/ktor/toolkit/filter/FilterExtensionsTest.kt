package com.github.joaoseidel.ktor.toolkit.filter

import com.github.joaoseidel.ktor.toolkit.filter.data.InvalidFilterException
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

class FilterExtensionsTest :
    ShouldSpec(
        {
            should("parse the filters of the current call") {
                testApplication {
                    routing {
                        get("/books") {
                            call.respondText(call.filters.joinToString { "${it.property} ${it.operator} ${it.values}" })
                        }
                    }

                    client.get("/books?filterBy=price:gte:10,status:in:a%7Cb").bodyAsText() shouldBe
                        "price GTE [10], status IN [a, b]"
                }
            }

            should("fail the call past the endpoint's own limit") {
                testApplication {
                    routing {
                        get("/books") {
                            val failure = runCatching { call.filters(maxFilters = 1) }.exceptionOrNull()
                            call.respondText((failure is InvalidFilterException).toString())
                        }
                    }

                    client.get("/books?filterBy=a:eq:1,b:eq:2").bodyAsText() shouldBe "true"
                    client.get("/books?filterBy=a:eq:1").bodyAsText() shouldBe "false"
                }
            }
        },
    )

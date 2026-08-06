package com.github.joaoseidel.ktor.toolkit.expander

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/** Reports what the route saw in `call.expand`, so the property is exercised through real routing. */
private fun expandOf(query: String): String {
    var seen = ""

    testApplication {
        routing {
            get("/books") {
                seen =
                    call.expand
                        .toFetchPaths()
                        .sorted()
                        .joinToString(",")
                call.respondText("ok")
            }
        }
        client.get("/books$query").bodyAsText()
    }

    return seen
}

class ExpandExtensionsTest :
    ShouldSpec({
        context("ApplicationCall.expand") {
            should("parse the expand parameter off the current call") {
                expandOf("?expand=author") shouldBe "author"
            }

            should("parse nested paths") {
                expandOf("?expand=author.books") shouldBe "author,author.books"
            }

            should("be empty when the call carries no expand parameter") {
                expandOf("") shouldBe ""
            }
        }
    })

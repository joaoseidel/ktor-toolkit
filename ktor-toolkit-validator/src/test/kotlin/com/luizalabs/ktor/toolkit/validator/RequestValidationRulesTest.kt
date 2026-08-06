package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.validators.min
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/** Echoes the body back, or the validation reasons when the plugin rejected it. */
private fun Application.reportValidationFailures() {
    routing {
        post("/titles") {
            try {
                call.respondText(call.receive<String>())
            } catch (failure: RequestValidationException) {
                call.respondText(failure.reasons.joinToString("; "), status = HttpStatusCode.BadRequest)
            }
        }
    }
}

class RequestValidationRulesTest :
    ShouldSpec({
        context("rules, given a block") {
            should("accept a request that satisfies the rules") {
                testApplication {
                    application {
                        install(RequestValidation) {
                            rules<String> {
                                property(String::length) { should be min(3) }
                            }
                        }
                        reportValidationFailures()
                    }

                    val response = client.post("/titles") { setBody("Kotlin") }

                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldBe "Kotlin"
                }
            }

            should("reject one that does not, quoting the property path") {
                testApplication {
                    application {
                        install(RequestValidation) {
                            rules<String> {
                                property(String::length) { should be min(3) }
                            }
                        }
                        reportValidationFailures()
                    }

                    val response = client.post("/titles") { setBody("ab") }

                    response.status shouldBe HttpStatusCode.BadRequest
                    response.bodyAsText() shouldBe "`length` should be greater than or equal to 3"
                }
            }
        }

        context("rules, given a RequestValidator") {
            val validator =
                object : RequestValidator<String> {
                    override fun ValidationContext<String>.validate() {
                        property(String::length) { should be min(3) }
                        invariant("should not be blank") { it.isNotBlank() }
                    }
                }

            should("run the validator's rules") {
                testApplication {
                    application {
                        install(RequestValidation) { rules(validator) }
                        reportValidationFailures()
                    }

                    client.post("/titles") { setBody("Kotlin") }.status shouldBe HttpStatusCode.OK

                    val response = client.post("/titles") { setBody("  ") }

                    response.status shouldBe HttpStatusCode.BadRequest
                    response.bodyAsText() shouldBe
                        "`length` should be greater than or equal to 3; should not be blank"
                }
            }
        }

        context("the context behind the bridge") {
            should("collect what a RequestValidator declares") {
                val validator =
                    object : RequestValidator<String> {
                        override fun ValidationContext<String>.validate() {
                            property(String::length) { should notBe min(3) }
                        }
                    }

                val context = ValidationContext("Kotlin")
                with(validator) { context.validate() }

                context.errors.map { it.toString() } shouldBe
                    listOf("`length` should not be greater than or equal to 3")
            }

            should("stay valid when nothing is found") {
                val context = ValidationContext("Kotlin")
                context.property(String::length) { should be min(3) }

                context.hasErrors shouldBe false
            }
        }
    })

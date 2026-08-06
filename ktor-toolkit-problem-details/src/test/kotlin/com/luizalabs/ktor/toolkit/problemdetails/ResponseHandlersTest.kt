package com.luizalabs.ktor.toolkit.problemdetails

import com.luizalabs.ktor.toolkit.problemdetails.exception.HttpStatusException
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drives the handlers directly, rather than through [problemDetails], so each one is exercised the
 * way an application that wants only some of them would call it — on its own, and on its defaults.
 */
private fun handlerApp(
    boom: () -> Nothing,
    handle: suspend (ApplicationCall, Throwable) -> Unit,
    assert: suspend (HttpResponse) -> Unit,
) = testApplication {
    install(StatusPages) {
        exception<Throwable> { call, cause -> handle(call, cause) }
    }
    routing { get("/boom") { boom() } }

    assert(client.get("/boom"))
}

private suspend fun HttpResponse.problem(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

private fun JsonObject.properties(): Map<String, String> =
    this["properties"]?.jsonObject.orEmpty().mapValues { (_, value) -> value.jsonPrimitive.content }

class ResponseHandlersTest :
    ShouldSpec({
        context("handling an explicitly thrown HTTP status") {
            should("answer with that status, on the default serializer") {
                handlerApp(
                    boom = { throw HttpStatusException(HttpStatusCode.Conflict, "Already checked out") },
                    handle = { call, cause ->
                        ResponseHandlers.handleHttpStatusException(call, cause as HttpStatusException)
                    },
                ) { response ->
                    response.status shouldBe HttpStatusCode.Conflict
                    response.problem().string("detail") shouldBe "Already checked out"
                    response.problem().string("instance") shouldBe "/boom"
                }
            }
        }

        context("handling a validation failure") {
            should("quote a top-level field at the root of the document") {
                handlerApp(
                    boom = { throw RequestValidationException(Unit, listOf("`title` should not be blank")) },
                    handle = { call, cause ->
                        ResponseHandlers.handleValidationException(call, cause as RequestValidationException)
                    },
                ) { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.problem().properties() shouldContainExactly
                        mapOf("$.title" to "Property `title` at `\$.root` should not be blank")
                }
            }

            should("quote a nested field at the path it sits on") {
                handlerApp(
                    boom = {
                        throw RequestValidationException(Unit, listOf("`author.address.city` should not be blank"))
                    },
                    handle = { call, cause ->
                        ResponseHandlers.handleValidationException(call, cause as RequestValidationException)
                    },
                ) { response ->
                    response.problem().properties() shouldContainExactly
                        mapOf(
                            "\$.author.address.city" to
                                "Property `city` at `\$.author.address` should not be blank",
                        )
                }
            }

            should("still answer when a reason quotes no field at all") {
                handlerApp(
                    boom = { throw RequestValidationException(Unit, listOf("the whole thing is wrong")) },
                    handle = { call, cause ->
                        ResponseHandlers.handleValidationException(call, cause as RequestValidationException)
                    },
                ) { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.problem().properties() shouldContainExactly mapOf("$." to "Property `` at `\$.root` ")
                }
            }
        }

        context("handling a malformed request body") {
            should("fall back to the exception's own message when no field is named") {
                handlerApp(
                    boom = { throw BadRequestException("Body could not be read") },
                    handle = { call, cause ->
                        ResponseHandlers.handleBadRequestException(call, cause as BadRequestException)
                    },
                ) { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.problem().string("detail") shouldBe "Body could not be read"
                    response.problem()["properties"].shouldBeNull()
                }
            }

            should("name no field when the missing-field report is not phrased the way it parses") {
                handlerApp(
                    boom = {
                        throw BadRequestException("Bad body", MissingFieldException("title", "CreateBook"))
                    },
                    handle = { call, cause ->
                        ResponseHandlers.handleBadRequestException(call, cause as BadRequestException)
                    },
                ) { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.problem().string("detail") shouldBe "Missing required fields"
                    response.problem().properties() shouldContainExactly emptyMap()
                }
            }

            should("name no field when the missing-field report carries no message at all") {
                handlerApp(
                    boom = {
                        // The only way to build one with a null message; the guard it exercises is
                        // there because `Throwable.message` is nullable at all.
                        @Suppress("DEPRECATION_ERROR")
                        throw BadRequestException("Bad body", MissingFieldException(listOf("title"), null, null))
                    },
                    handle = { call, cause ->
                        ResponseHandlers.handleBadRequestException(call, cause as BadRequestException)
                    },
                ) { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.problem().properties() shouldContainExactly emptyMap()
                }
            }
        }

        context("handling anything else") {
            should("keep the message out of the response by default") {
                handlerApp(
                    boom = { error("jdbc://user:hunter2@db.internal") },
                    handle = { call, cause -> ResponseHandlers.handleGenericException(call, cause) },
                ) { response ->
                    response.status shouldBe HttpStatusCode.InternalServerError
                    response.problem().string("detail") shouldBe "An unexpected error occurred."
                }
            }
        }
    })

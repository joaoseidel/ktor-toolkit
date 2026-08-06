package com.luizalabs.ktor.toolkit.problemdetails

import com.luizalabs.ktor.toolkit.problemdetails.data.ProblemDetail
import com.luizalabs.ktor.toolkit.problemdetails.exception.HttpStatusException
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
private data class CreateBook(
    val title: String,
    val authorName: String,
)

@Serializable
private data class Address(
    val street: String,
    val city: String,
)

@Serializable
private data class CreateOrder(
    val reference: String,
    val shipTo: Address,
)

/** An application's own exception, of the kind `on<E>` exists to map. */
private class BookNotFoundException(
    val id: String,
) : IllegalStateException("Book $id not found")

private suspend fun HttpResponse.problemBody(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

/** Boots an app whose only error handling is the toolkit's installer. */
private fun problemApp(
    configure: ProblemDetailsConfig.() -> Unit = {},
    routes: ApplicationTestBuilder.() -> Unit,
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    install(ContentNegotiation) { json() }
    install(RequestValidation) {
        validate<CreateBook> { body ->
            if (body.title.isBlank()) ValidationResult.Invalid("`title` should not be blank") else ValidationResult.Valid
        }
    }
    install(StatusPages) { problemDetails(configure) }
    routes()
    block()
}

class ProblemDetailsTest :
    ShouldSpec({
        context("HttpStatusException") {
            should("answer with the exception's status and detail as problem+json") {
                problemApp(
                    routes = {
                        routing {
                            get("/books/{id}") {
                                throw HttpStatusException(
                                    HttpStatusCode.NotFound,
                                    "Book not found",
                                    mapOf("id" to "42"),
                                )
                            }
                        }
                    },
                ) {
                    val response = client.get("/books/42")

                    response.status shouldBe HttpStatusCode.NotFound
                    response.contentType()?.withoutParameters() shouldBe ProblemJsonContentType

                    val problem = response.problemBody()
                    problem.string("title") shouldBe "Not Found"
                    problem.string("status") shouldBe "404"
                    problem.string("detail") shouldBe "Book not found"
                    problem.string("type") shouldBe "about:blank"
                    problem.string("instance") shouldBe "/books/42"
                    problem["properties"]!!.jsonObject shouldContainKey "id"
                }
            }
        }

        context("RequestValidationException") {
            should("answer 400 with one property per failed rule") {
                problemApp(
                    routes = {
                        routing {
                            post("/books") {
                                call.receive<CreateBook>()
                                call.respondText("ok")
                            }
                        }
                    },
                ) {
                    val response =
                        client.post("/books") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"title":"","authorName":"Herbert"}""")
                        }

                    response.status shouldBe HttpStatusCode.BadRequest

                    val problem = response.problemBody()
                    problem.string("detail") shouldBe "Validation failed"
                    problem["properties"]!!.jsonObject shouldContainKey "$.title"
                }
            }

            should("rename fields with the configured naming strategy") {
                problemApp(
                    configure = { namingStrategy = JsonNamingStrategy.SnakeCase },
                    routes = {
                        routing {
                            post("/books") {
                                call.receive<CreateBook>()
                                call.respondText("ok")
                            }
                        }
                    },
                ) {
                    val response =
                        client.post("/books") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"title":"Dune"}""")
                        }

                    response.status shouldBe HttpStatusCode.BadRequest

                    val problem = response.problemBody()
                    problem.string("detail") shouldBe "Missing required fields"
                    problem["properties"]!!.jsonObject shouldContainKey "$.author_name"
                }
            }
        }

        context("BadRequestException") {
            should("name a single missing field") {
                problemApp(
                    routes = {
                        routing {
                            post("/books") {
                                call.receive<CreateBook>()
                                call.respondText("ok")
                            }
                        }
                    },
                ) {
                    val response =
                        client.post("/books") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"title":"Dune"}""")
                        }

                    response.status shouldBe HttpStatusCode.BadRequest

                    val problem = response.problemBody()
                    problem.string("detail") shouldBe "Missing required fields"
                    problem["properties"]!!.jsonObject shouldContainKey "$.authorName"
                }
            }

            should("name every missing field when several are absent") {
                problemApp(
                    routes = {
                        routing {
                            post("/books") {
                                call.receive<CreateBook>()
                                call.respondText("ok")
                            }
                        }
                    },
                ) {
                    val response =
                        client.post("/books") {
                            contentType(ContentType.Application.Json)
                            setBody("{}")
                        }

                    response.status shouldBe HttpStatusCode.BadRequest

                    val properties = response.problemBody()["properties"]!!.jsonObject
                    properties shouldContainKey "$.title"
                    properties shouldContainKey "$.authorName"
                }
            }
        }

        context("unhandled exceptions") {
            should("keep the exception message out of the response by default") {
                problemApp(
                    routes = {
                        routing {
                            get("/boom") { error("jdbc://user:hunter2@db.internal timed out") }
                        }
                    },
                ) {
                    val response = client.get("/boom")

                    response.status shouldBe HttpStatusCode.InternalServerError

                    val body = response.bodyAsText()
                    body shouldNotContain "hunter2"
                    body shouldNotContain "db.internal"
                    body shouldContain "An unexpected error occurred."
                }
            }

            should("echo the message when explicitly opted in") {
                problemApp(
                    configure = { includeExceptionMessage = true },
                    routes = {
                        routing {
                            get("/boom") { error("something specific") }
                        }
                    },
                ) {
                    client.get("/boom").bodyAsText() shouldContain "something specific"
                }
            }
        }

        context("an application's own exception") {
            should("be mapped to the problem its handler builds") {
                problemApp(
                    configure = {
                        on<BookNotFoundException> {
                            ProblemDetail.fromStatus(HttpStatusCode.NotFound, "No book with id ${it.id}")
                        }
                    },
                    routes = {
                        routing { get("/books/{id}") { throw BookNotFoundException("42") } }
                    },
                ) {
                    val response = client.get("/books/42")

                    response.status shouldBe HttpStatusCode.NotFound
                    response.contentType()?.withoutParameters() shouldBe ProblemJsonContentType

                    val problem = response.problemBody()
                    problem.string("title") shouldBe "Not Found"
                    problem.string("detail") shouldBe "No book with id 42"
                }
            }

            should("take the request path as its instance") {
                problemApp(
                    configure = {
                        on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound) }
                    },
                    routes = {
                        routing { get("/books/{id}") { throw BookNotFoundException("42") } }
                    },
                ) {
                    client.get("/books/42").problemBody().string("instance") shouldBe "/books/42"
                }
            }

            should("keep an instance the handler named itself") {
                problemApp(
                    configure = {
                        on<BookNotFoundException> {
                            ProblemDetail.fromStatus(HttpStatusCode.NotFound, instance = "urn:book:${it.id}")
                        }
                    },
                    routes = {
                        routing { get("/books/{id}") { throw BookNotFoundException("42") } }
                    },
                ) {
                    client.get("/books/42").problemBody().string("instance") shouldBe "urn:book:42"
                }
            }

            should("win over the catch-all, whatever order they were declared in") {
                problemApp(
                    configure = {
                        on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound) }
                    },
                    routes = {
                        routing { get("/books/{id}") { throw BookNotFoundException("42") } }
                    },
                ) {
                    client.get("/books/42").status shouldBe HttpStatusCode.NotFound
                }
            }

            should("let a more specific mapping win over a broader one") {
                problemApp(
                    configure = {
                        on<IllegalStateException> { ProblemDetail.fromStatus(HttpStatusCode.Conflict) }
                        on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound) }
                    },
                    routes = {
                        routing {
                            get("/books/{id}") { throw BookNotFoundException("42") }
                            get("/broken") { throw IllegalStateException("nope") }
                        }
                    },
                ) {
                    client.get("/books/42").status shouldBe HttpStatusCode.NotFound
                    client.get("/broken").status shouldBe HttpStatusCode.Conflict
                }
            }

            should("leave an unmapped exception to the catch-all") {
                problemApp(
                    configure = {
                        on<BookNotFoundException> { ProblemDetail.fromStatus(HttpStatusCode.NotFound) }
                    },
                    routes = {
                        routing { get("/broken") { throw RuntimeException("boom") } }
                    },
                ) {
                    client.get("/broken").status shouldBe HttpStatusCode.InternalServerError
                }
            }
        }

        context("installing the handlers without configuring them") {
            should("still answer an unhandled exception as problem+json") {
                testApplication {
                    install(StatusPages) { problemDetails() }
                    routing { get("/boom") { error("nope") } }

                    val response = client.get("/boom")

                    response.status shouldBe HttpStatusCode.InternalServerError
                    response.contentType()?.withoutParameters() shouldBe ProblemJsonContentType
                    response.problemBody().string("detail") shouldBe "An unexpected error occurred."
                }
            }
        }

        context("responding with a problem from a route") {
            should("serve it as problem+json on the default serializer") {
                testApplication {
                    routing {
                        get("/gone") {
                            call.respondProblem(ProblemDetail.fromStatus(HttpStatusCode.Gone, "Long gone"))
                        }
                    }

                    val response = client.get("/gone")

                    response.status shouldBe HttpStatusCode.Gone
                    response.contentType()?.withoutParameters() shouldBe ProblemJsonContentType
                    response.problemBody().string("detail") shouldBe "Long gone"
                }
            }
        }

        context("a missing field inside a nested object") {
            should("quote the path the field sits on rather than the root") {
                problemApp(
                    routes = {
                        routing {
                            post("/orders") {
                                call.receive<CreateOrder>()
                                call.respondText("ok")
                            }
                        }
                    },
                ) {
                    val response =
                        client.post("/orders") {
                            contentType(ContentType.Application.Json)
                            setBody("""{"reference":"A-1","shipTo":{"street":"Main"}}""")
                        }

                    response.status shouldBe HttpStatusCode.BadRequest

                    val properties = response.problemBody()["properties"]!!.jsonObject
                    properties shouldContainKey "$.shipTo.city"
                }
            }
        }

        context("problem shape") {
            should("omit absent members rather than emit nulls") {
                problemApp(
                    routes = {
                        routing {
                            get("/gone") { throw HttpStatusException(HttpStatusCode.Gone) }
                        }
                    },
                ) {
                    val problem = client.get("/gone").problemBody()

                    problem["detail"].shouldBeNull()
                    problem["properties"].shouldBeNull()
                }
            }
        }
    })

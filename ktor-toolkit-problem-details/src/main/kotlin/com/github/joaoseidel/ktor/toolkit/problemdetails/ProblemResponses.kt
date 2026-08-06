package com.github.joaoseidel.ktor.toolkit.problemdetails

import com.github.joaoseidel.ktor.toolkit.problemdetails.data.ProblemDetail
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json

/** The media type RFC 9457 defines for problem details. */
val ProblemJsonContentType: ContentType = ContentType("application", "problem+json")

/**
 * The serializer used for problem details.
 *
 * Absent fields are omitted rather than emitted as `null`, so a minimal problem stays minimal.
 * Pass your own [Json] to `respondProblem` if the application uses a different naming strategy.
 */
val ProblemJson: Json =
    Json {
        explicitNulls = false
        encodeDefaults = true
    }

/**
 * Responds with [problem] as `application/problem+json`.
 *
 * This bypasses ContentNegotiation deliberately: an error body should carry the problem media type
 * regardless of what the route negotiated for its success responses.
 *
 * @param problem The problem to serialize.
 * @param json The serializer to use. Defaults to [ProblemJson].
 */
suspend fun ApplicationCall.respondProblem(
    problem: ProblemDetail,
    json: Json = ProblemJson,
) {
    respondText(
        text = json.encodeToString(problem),
        contentType = ProblemJsonContentType,
        status = HttpStatusCode.fromValue(problem.status),
    )
}

/** The request path, used as the problem's `instance` so a client can tell occurrences apart. */
internal val ApplicationCall.problemInstance: String get() = request.path()

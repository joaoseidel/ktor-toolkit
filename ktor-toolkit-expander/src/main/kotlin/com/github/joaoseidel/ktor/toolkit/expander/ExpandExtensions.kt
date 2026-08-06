package com.github.joaoseidel.ktor.toolkit.expander

import com.github.joaoseidel.ktor.toolkit.expander.web.ExpandRequest
import io.ktor.server.application.ApplicationCall

/** Parses the ?expand= query parameter for the current call. */
val ApplicationCall.expand: ExpandRequest
    get() = ExpandRequest.from(request.queryParameters)

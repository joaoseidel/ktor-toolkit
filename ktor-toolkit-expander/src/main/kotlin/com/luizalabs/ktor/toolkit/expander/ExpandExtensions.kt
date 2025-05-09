package com.luizalabs.ktor.toolkit.expander

import com.luizalabs.ktor.toolkit.expander.web.ExpandRequest
import io.ktor.server.application.ApplicationCall

/** Parses the ?expand= query parameter for the current call. */
val ApplicationCall.expand: ExpandRequest
    get() = ExpandRequest.from(request.queryParameters)

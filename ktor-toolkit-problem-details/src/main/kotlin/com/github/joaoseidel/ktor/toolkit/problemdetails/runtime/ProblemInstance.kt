package com.github.joaoseidel.ktor.toolkit.problemdetails.runtime

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path

internal val ApplicationCall.problemInstance: String get() = request.path()

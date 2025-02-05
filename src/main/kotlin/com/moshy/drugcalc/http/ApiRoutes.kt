package com.moshy.drugcalc.http

import io.ktor.server.application.*
import io.ktor.server.routing.*

internal fun Application.configureApiRoutes(apiBuilder: Route.() -> Unit) {
    routing {
        route("/api") {
            apiBuilder()
        }
    }
}
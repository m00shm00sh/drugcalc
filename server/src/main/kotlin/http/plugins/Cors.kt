package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS

/** install: [Cors] */
internal fun Application.configureCors(corsConfig: AppConfig.Cors) {
    install(CORS) {
        corsConfig.frontends.forEach(::allowHost)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
    }
}


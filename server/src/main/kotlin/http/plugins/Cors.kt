package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS

/** install: [Cors] */
internal fun Application.configureCors(corsConfig: AppConfig.Cors) {
    install(CORS) {
        corsConfig.frontends.forEach(::allowHost)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowCredentials = true
    }
}


package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.bodylimit.*

/** install: [RequestBodyLimit] */
internal fun Application.configureLimits(limits: AppConfig.Http.Limits) {
    install(RequestBodyLimit) {
        bodyLimit {
            limits.requestBodyLimit
        }
    }
}
package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.ratelimit.*

/** install: [RequestBodyLimit] */
internal fun Application.configureLimits(limits: AppConfig.Http.Limits) {
    install(RequestBodyLimit) {
        bodyLimit {
            limits.requestBodyLimit
        }
    }
    install(RateLimit) {
        register {
            rateLimiter(limit = limits.rateLimitNum, refillPeriod = limits.rateLimitTime)
        }
    }
}
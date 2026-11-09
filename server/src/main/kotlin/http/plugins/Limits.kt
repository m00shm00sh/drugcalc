package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.server.http.routing.HEARTBEAT_PATH
import com.moshy.drugcalc.server.http.routing.configureApiRoutes
import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.bodylimit.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.path

/** Provider name for the shared, server-wide limit applied in [configureApiRoutes]. */
internal val GLOBAL_RATE_LIMIT = RateLimitName("AppThroughputRateLimiter")

/** install: [RequestBodyLimit], [RateLimit] */
internal fun Application.configureLimits(limits: AppConfig.Http.Limits) {
    install(RequestBodyLimit) {
        bodyLimit {
            limits.requestBodyLimit
        }
    }
    install(RateLimit) {
        /* Applied to every request. The request key isolates each client by its
         * remote address (honoring X-Forwarded-For from the Caddy proxy), so one
         * client cannot consume another's budget.
         * Heartbeat requests have zero weight, exempting them from both buckets.
         */
        /* This will probably be reworked into just throughput should the app become large
         * enough for an api gateway and multiple workers because the rate limiting would have
         * to be applied prior to calling the worker.
         */
        global {
            requestKey { call -> call.request.origin.remoteAddress }
            requestWeight { call, _ -> if (call.isHeartbeat()) 0 else 1 }
            rateLimiter(
                limit = limits.clientRateLimit.num,
                refillPeriod = limits.clientRateLimit.time,
            )
        }
        // Applied to all routed endpoints via rateLimit(GLOBAL_RATE_LIMIT). This
        // bucket is shared by every client and caps total server throughput.
        register(GLOBAL_RATE_LIMIT) {
            rateLimiter(
                limit = limits.globalRateLimit.num,
                refillPeriod = limits.globalRateLimit.time,
            )
        }
    }
}

private fun ApplicationCall.isHeartbeat(): Boolean =
    request.path().trimEnd('/') == HEARTBEAT_PATH

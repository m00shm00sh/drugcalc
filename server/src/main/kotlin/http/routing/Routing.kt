package com.moshy.drugcalc.server.http.routing

import com.moshy.drugcalc.calc.calc.Evaluator
import com.moshy.drugcalc.calc.datacontroller.DataController
import com.moshy.drugcalc.server.http.JwtService
import com.moshy.drugcalc.server.http.plugins.GLOBAL_RATE_LIMIT
import com.moshy.drugcalc.server.http.routing.util.get
import com.moshy.drugcalc.server.http.user.UserService
import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.resources.Resource
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.resources.Resources
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

@Resource("api")
internal class Heartbeat()

/** Path of the [Heartbeat] route, used to exempt it from rate limiting. */
internal const val HEARTBEAT_PATH = "/api"

internal fun Application.configureApiRoutes(
    flags: AppConfig.DevFlags,
    dataController: DataController,
    userService: UserService,
    jwtService: JwtService,
    evaluator: Evaluator,
    jsonModule: Json
) {
    install(Resources) {
        serializersModule = jsonModule.serializersModule
    }
    install(IgnoreTrailingSlash)
    routing {
        // Per-client limiting is installed application-wide in configureLimits;
        // this adds the shared global bucket on top of it.
        rateLimit(GLOBAL_RATE_LIMIT) {
            openAPI("openapi")

            route("api") {
                configureLoginRoutes(flags, jwtService, userService)
                configureDataRoutes(flags, dataController)
                configureCalcRoutes(flags, dataController, evaluator)
            }
        }
        // Heartbeat is exempt from all rate limiting.
        get<Heartbeat, String> {
            "OK"
        }
    }
}

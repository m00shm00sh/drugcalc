package com.moshy.drugcalc.server.http.routing

import com.moshy.drugcalc.calc.datacontroller.DataController
import calc.Evaluator
import com.moshy.drugcalc.server.http.JwtService
import com.moshy.drugcalc.server.http.user.UserService
import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.resources.Resources
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

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
        openAPI("openapi")

        route("api") {
            configureLoginRoutes(flags, jwtService, userService)
            configureDataRoutes(flags, dataController)
            configureCalcRoutes(flags, dataController, evaluator, jsonModule)
        }
    }
}
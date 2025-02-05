package com.moshy.drugcalc.http

import com.moshy.drugcalc.repo.Repository
import com.moshy.drugcalc.util.AppConfig
import io.ktor.server.application.*
import io.ktor.server.plugins.bodylimit.*

internal fun httpMainSetup(repo: Repository, limits: AppConfig.Limits): (Application) -> Unit {
    fun Application.httpMain() {
        install(RequestBodyLimit) {
            bodyLimit { limits.requestBodyLimit }
        }
        configureDataRouting(limits.sendUnexpectedExceptionStackTrace)
        configureApiRoutes {
            configureCalcApiRoutes(repo)
            configureConfigApiRoutes(repo)
            configureDataApiRoutes(repo)
        }
    }
    return Application::httpMain
}

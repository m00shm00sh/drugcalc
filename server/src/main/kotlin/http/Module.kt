package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.calc.datacontroller.DataController
import com.moshy.drugcalc.calc.calc.Evaluator
import com.moshy.drugcalc.db.getDataSource
import com.moshy.drugcalc.server.http.plugins.configureSerialization
import com.moshy.drugcalc.types.datasource.DataSourceDelegate
import com.moshy.drugcalc.server.http.plugins.*
import com.moshy.drugcalc.server.http.routing.configureApiRoutes
import com.moshy.drugcalc.server.http.user.*
import com.moshy.drugcalc.server.io.JsonWithLenientIsoDuration
import com.moshy.drugcalc.server.util.*
import io.ktor.server.application.*

// main ktor entrypoint
internal suspend fun Application.initializeKtorModule(config: AppConfig.App) {
    val uAdmin = User(
        name = "dcadmin",
        pass = generateRandomString(64),
        isAdmin = true
    )

    val userService = UserService()
    userService.saveUser(uAdmin)

    println("""
        
        Temporary credentials:
        u: ${uAdmin.name}
        p: ${uAdmin.pass}
        
    """.trimIndent()
    )

    val dataSource: DataSourceDelegate = getDataSource(config.db)
    if (dataSource.isDbEmpty() && !config.flags.allowEmptyDb)
        throw IllegalArgumentException("disallowed empty db state")

    val dataController = DataController(config.datacontroller, dataSource)

    val evaluator = Evaluator(config.evaluator)

    configureModule(config, userService, dataController, evaluator)
}

/*
 * this is separated from initializeKtorModule in that the service instances are passed;
 * this permits unit testing authentication-related parts
 */
internal fun Application.configureModule(
    config: AppConfig.App,
    userService: UserService,
    dataController: DataController,
    evaluator: Evaluator,
) {
    val jwtService = JwtService(config.jwt, userService)
    val json = JsonWithLenientIsoDuration

    configureExceptions(config.flags.sendStackTrace)
    configureLimits(config.http.limits)
    configureSerialization(json)
    configureSecurity(jwtService)
    configureApiRoutes(config.flags, dataController, userService, jwtService, evaluator, json)
}

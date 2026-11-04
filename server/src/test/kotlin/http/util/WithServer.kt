package com.moshy.drugcalc.server.http.util

import com.moshy.drugcalc.calc.calc.Evaluator
import com.moshy.drugcalc.server.http.configureModule
import com.moshy.drugcalc.server.http.user.UserService
import com.moshy.drugcalc.server.io.JsonWithLenientIsoDuration
import com.moshy.drugcalc.server.util.AppConfig
import com.moshy.drugcalc.types.datasource.DBConfig
import com.moshy.drugcalc.calctest.DataControllerTestSupport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import java.util.EnumSet
import kotlin.time.Duration.Companion.hours

internal fun withServer(
    ro: EnumSet<DataControllerTestSupport.RoFlag>,
    // for reusing password within test class
    userService: UserService = defaultUserService(),
    // for optionally reusing jwt within test class
    jwtConfig: AppConfig.Jwt = defaultJwtConfig(),
    // for overriding limits (e.g. tight limits in tests)
    limits: AppConfig.Http.Limits = defaultTestLimits(),
    block: suspend HttpClient.() -> Unit
) = testApplication {
    val config = AppConfig.App(
        AppConfig.DevFlags(httpDevMode = true, sendStackTrace = true, enableTestonlyEndpoints = true),
        db = UNUSED_DB_CONFIG,
        http = AppConfig.Http(limits = limits),
        jwt = jwtConfig
    )
    application {
        val dataController = DataControllerTestSupport.newTestController(ro)
        configureModule(
            config,
            userService = userService,
            dataController = dataController,
            evaluator = Evaluator(config.evaluator)
        )
    }
    val client = createClient {
        install(ContentNegotiation) {
            json(JsonWithLenientIsoDuration)
        }
    }
    client.block()
}

internal fun defaultUserService() = UserService()
internal fun defaultJwtConfig() = AppConfig.Jwt(audience = "-dctest-", issuer = "http://dc-app-test:mem/")

/* Rate limiting is global, so tests that do not exercise it need permissive limits;
 * otherwise routine request sequences (such as data API CRUD tests) get 429s.
 */
internal fun defaultTestLimits() = AppConfig.Http.Limits(
    globalRateLimit = AppConfig.Http.Limits.RateLimit(Int.MAX_VALUE, 1.hours),
    clientRateLimit = AppConfig.Http.Limits.RateLimit(Int.MAX_VALUE, 1.hours),
)

private val UNUSED_DB_CONFIG = DBConfig("", "")

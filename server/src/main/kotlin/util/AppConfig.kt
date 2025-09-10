package com.moshy.drugcalc.server.util

import com.moshy.drugcalc.calc.calc.Evaluator
import com.moshy.drugcalc.types.datasource.DBConfig
import com.moshy.drugcalc.calc.datacontroller.DataController
import com.sksamuel.hoplite.*
import kotlin.time.*

internal object AppConfig {

    data class DevFlags(
        val httpDevMode: Boolean = false,
        val allowDbClear: Boolean = false,
        val sendStackTrace: Boolean = false,
        val enableTestonlyEndpoints: Boolean = false,
        val allowEmptyDb: Boolean = false,
    )

    data class Jwt(
        val audience: String = "dc",
        val issuer: String = "http://dc-app:mem/",
        val realm: String = "v0-inMemory",
        val secret: Secret = Secret(generateRandomString(64)),
        val timeout: Duration = 1.toDuration(DurationUnit.HOURS)
    )

    data class Http(
        val limits: Limits = Limits(),
        val bind: List<Bind> = emptyList(),
        val sslKey: SslKey? = null
    ) {
        data class Limits(
            val requestBodyLimit: Long = (1L shl 20), // 1 MiB
        )
        data class Bind(
            val host: String,
            val port: Int = 0,
            val sslPort: Int = 0
        ) {
            init {
                require((port > 0) xor (sslPort > 0)) {
                    "either port or sslport must be specified"
                }
            }
        }
        data class SslKey(
            val pemPath: List<String>,
            val refresh: Long? = null,
            val selfSignedLocalhost: Boolean = false
        ) {
            init {
                require(pemPath.size in listOf(0, 2)) {
                    "require exactly two PEM files - full chain pub key and priv key"
                }
                if (refresh != null) {
                    require(refresh > 0) {
                        "refresh must be positive if specified"
                    }
                }
            }
        }
    }

    data class App(
        val flags: DevFlags = DevFlags(),
        val evaluator: Evaluator.Config = Evaluator.Config(),
        val db: DBConfig,
        val datacontroller: DataController.Config = DataController.Config(),
        val http: Http,
        val jwt: Jwt,
    )

    fun config(args: Array<String>) =
        ConfigLoaderBuilder.default()
            .addCommandLineSource(args, prefix = "-D")
            .addResourceSource("/application.conf")
            .addFileSource("application.conf", optional = true)
            .build()
            .loadConfigOrThrow<App>()
}
